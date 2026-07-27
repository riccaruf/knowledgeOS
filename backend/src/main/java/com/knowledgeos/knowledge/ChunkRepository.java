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

    private static final String SEARCH_SQL = """
            SELECT c.id, c.document_id, c.document_version_id, c.title, c.section, c.page_number, c.content,
                   dv.version_label, 1 - (c.embedding <=> ?) AS similarity
            FROM chunk c
            JOIN document_version dv ON dv.id = c.document_version_id
            WHERE c.tenant_id = ?
              AND (? IS NULL OR c.metadata ->> 'category' = ANY (?))
            ORDER BY c.embedding <=> ?
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

    public List<ChunkSearchResult> search(UUID tenantId, float[] queryEmbedding, int topK, List<String> categories) {
        return jdbcTemplate.execute((ConnectionCallback<List<ChunkSearchResult>>) connection -> {
            List<ChunkSearchResult> results = new ArrayList<>();
            try (PreparedStatement ps = connection.prepareStatement(SEARCH_SQL)) {
                PGvector vector = new PGvector(queryEmbedding);
                ps.setObject(1, vector);
                ps.setObject(2, tenantId);
                if (categories == null || categories.isEmpty()) {
                    ps.setNull(3, java.sql.Types.ARRAY, "text[]");
                    ps.setNull(4, java.sql.Types.ARRAY, "text[]");
                } else {
                    java.sql.Array array = connection.createArrayOf("text", categories.toArray());
                    ps.setArray(3, array);
                    ps.setArray(4, array);
                }
                ps.setObject(5, vector);
                ps.setInt(6, topK);
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
            }
            return results;
        });
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
