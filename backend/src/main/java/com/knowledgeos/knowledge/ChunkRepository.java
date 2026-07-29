package com.knowledgeos.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Accesso a "chunk" via JDBC diretto (non JPA): pgvector richiede il tipo
 * colonna VECTOR e l'operatore "<=>" per la similarita' coseno, per cui SQL
 * esplicito e' piu' semplice e trasparente di un mapping Hibernate custom
 * (03_DATABASE_DESIGN.md §7). Le query passano sempre da tenant_id esplicito
 * (difesa in profondita' rispetto a RLS, 06_SECURITY_MODEL.md §3.2).
 */
@Repository
@RequiredArgsConstructor
public class ChunkRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private static final String INSERT_SQL = """
            INSERT INTO chunk
                (id, tenant_id, document_id, document_version_id, title, section, page_number,
                 content, metadata, embedding, embedding_model, chunk_index)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
            """;

    private static final String SEARCH_BY_VECTOR_SQL = """
            SELECT c.id, c.document_id, c.document_version_id, c.title, c.section, c.page_number, c.content,
                   dv.version_label, 1 - (c.embedding <=> ?) AS similarity
            FROM chunk c
            JOIN document_version dv ON dv.id = c.document_version_id
            WHERE c.tenant_id = ?
              AND (? IS NULL OR c.metadata ->> 'category' = ANY (?))
            ORDER BY c.embedding <=> ?
            LIMIT ?
            """;

    // ts_rank_cd (cover density) discrimina meglio di ts_rank quando piu' termini
    // della query occorrono vicini nello stesso chunk. La guardia numnode(...) > 0
    // evita il comportamento indefinito di websearch_to_tsquery su input degenere
    // (domanda cortissima o composta solo da stopword): in quel caso la ricerca
    // keyword restituisce semplicemente zero candidati, con fallback naturale al
    // solo-vettoriale in fase di fusione RRF (si veda RrfMerger).
    private static final String SEARCH_BY_KEYWORD_SQL = """
            SELECT c.id, c.document_id, c.document_version_id, c.title, c.section, c.page_number, c.content,
                   dv.version_label, 1 - (c.embedding <=> ?) AS similarity
            FROM chunk c
            JOIN document_version dv ON dv.id = c.document_version_id
            WHERE c.tenant_id = ?
              AND (? IS NULL OR c.metadata ->> 'category' = ANY (?))
              AND numnode(websearch_to_tsquery('italian', ?)) > 0
              AND c.content_tsv @@ websearch_to_tsquery('italian', ?)
            ORDER BY ts_rank_cd(c.content_tsv, websearch_to_tsquery('italian', ?)) DESC
            LIMIT ?
            """;

    public UUID insert(ChunkRecord record) {
        UUID id = UUID.randomUUID();
        String metadataJson = writeJson(record.metadata());

        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement ps = connection.prepareStatement(INSERT_SQL)) {
                ps.setObject(1, id);
                ps.setObject(2, record.tenantId());
                ps.setObject(3, record.documentId());
                ps.setObject(4, record.documentVersionId());
                ps.setString(5, record.title());
                ps.setString(6, record.section());
                ps.setInt(7, record.page());
                ps.setString(8, record.content());
                ps.setString(9, metadataJson);
                ps.setObject(10, new PGvector(record.embedding()));
                ps.setString(11, record.embeddingModel());
                ps.setInt(12, record.chunkIndex());
                ps.executeUpdate();
            }
            return null;
        });

        return id;
    }

    public List<ChunkSearchResult> searchByVector(UUID tenantId, float[] queryEmbedding, int limit, List<String> categories) {
        return jdbcTemplate.execute((ConnectionCallback<List<ChunkSearchResult>>) connection -> {
            try (PreparedStatement ps = connection.prepareStatement(SEARCH_BY_VECTOR_SQL)) {
                PGvector vector = new PGvector(queryEmbedding);
                ps.setObject(1, vector);
                ps.setObject(2, tenantId);
                bindCategoryFilter(connection, ps, 3, categories);
                ps.setObject(5, vector);
                ps.setInt(6, limit);
                return readResults(ps);
            }
        });
    }

    public List<ChunkSearchResult> searchByKeyword(UUID tenantId, String questionText, float[] queryEmbedding, int limit, List<String> categories) {
        return jdbcTemplate.execute((ConnectionCallback<List<ChunkSearchResult>>) connection -> {
            try (PreparedStatement ps = connection.prepareStatement(SEARCH_BY_KEYWORD_SQL)) {
                ps.setObject(1, new PGvector(queryEmbedding));
                ps.setObject(2, tenantId);
                bindCategoryFilter(connection, ps, 3, categories);
                ps.setString(5, questionText);
                ps.setString(6, questionText);
                ps.setString(7, questionText);
                ps.setInt(8, limit);
                return readResults(ps);
            }
        });
    }

    private void bindCategoryFilter(java.sql.Connection connection, PreparedStatement ps, int firstParamIndex, List<String> categories) throws java.sql.SQLException {
        if (categories == null || categories.isEmpty()) {
            ps.setNull(firstParamIndex, java.sql.Types.ARRAY, "text[]");
            ps.setNull(firstParamIndex + 1, java.sql.Types.ARRAY, "text[]");
        } else {
            java.sql.Array array = connection.createArrayOf("text", categories.toArray());
            ps.setArray(firstParamIndex, array);
            ps.setArray(firstParamIndex + 1, array);
        }
    }

    private List<ChunkSearchResult> readResults(PreparedStatement ps) throws java.sql.SQLException {
        List<ChunkSearchResult> results = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                results.add(new ChunkSearchResult(
                        (UUID) rs.getObject("id"),
                        (UUID) rs.getObject("document_id"),
                        (UUID) rs.getObject("document_version_id"),
                        rs.getString("title"),
                        rs.getString("version_label"),
                        rs.getString("section"),
                        rs.getInt("page_number"),
                        rs.getString("content"),
                        rs.getDouble("similarity")
                ));
            }
        }
        return results;
    }

    /**
     * Rimuove tutti i chunk (e i relativi embedding) di un documento — usato
     * dall'eliminazione documento per assicurare che il contenuto non sia piu'
     * trovabile dal retrieval, non solo nascosto lato UI (04_API_SPECIFICATION.md §3.7).
     */
    public void deleteByDocumentId(UUID tenantId, UUID documentId) {
        jdbcTemplate.update("DELETE FROM chunk WHERE tenant_id = ? AND document_id = ?", tenantId, documentId);
    }

    private String writeJson(java.util.Map<String, Object> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata == null ? java.util.Map.of() : metadata);
        } catch (Exception e) {
            throw new IllegalStateException("Impossibile serializzare i metadata del chunk.", e);
        }
    }
}
