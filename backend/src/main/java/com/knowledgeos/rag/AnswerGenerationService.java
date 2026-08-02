package com.knowledgeos.rag;

import com.knowledgeos.audit.AuditService;
import com.knowledgeos.knowledge.ChunkSearchResult;
import com.knowledgeos.llm.OllamaChatClient;
import com.knowledgeos.llm.OllamaEmbeddingClient;
import com.knowledgeos.rag.dto.QueryRequest;
import com.knowledgeos.rag.dto.QueryResponse;
import com.knowledgeos.rag.dto.QuerySourceResponse;
import com.knowledgeos.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Endpoint centrale del prodotto (POST /api/v1/query): dalla domanda in
 * linguaggio naturale alla risposta con fonti verificabili
 * (05_RAG_PIPELINE.md §5-7). Pipeline: metadata filtering (categoria) ->
 * hybrid search (vettoriale + keyword, HybridSearchService) -> reranking
 * (LlmReranker) -> Context Builder -> LLM.
 */
@Service
@RequiredArgsConstructor
public class AnswerGenerationService {

    private static final String SYSTEM_PROMPT = """
            Sei l'assistente di conoscenza aziendale di KnowledgeOS. Rispondi ESCLUSIVAMENTE
            sulla base del contesto fornito qui sotto, tratto dalla documentazione aziendale.
            Se l'informazione richiesta non e' presente nel contesto, dichiara esplicitamente
            che non e' disponibile nella documentazione consultata: non inventare contenuti.
            Rispondi in italiano, in modo conciso e diretto.
            """;

    private final OllamaEmbeddingClient embeddingClient;
    private final OllamaChatClient chatClient;
    private final HybridSearchService hybridSearchService;
    private final LlmReranker llmReranker;
    private final ContextBuilder contextBuilder;
    private final QueryLogRepository queryLogRepository;
    private final AuditService auditService;
    private final RetrievalProperties retrievalProperties;

    @Transactional
    public QueryResponse answer(QueryRequest request) {
        long start = System.currentTimeMillis();
        UUID tenantId = TenantContext.getTenantId();

        float[] questionEmbedding = embeddingClient.embed(request.question());

        List<String> categoryFilter = request.filters() != null ? request.filters().category() : null;
        List<ChunkSearchResult> candidates = hybridSearchService.search(
                tenantId, request.question(), questionEmbedding, categoryFilter);

        List<RankedChunk> reranked = candidates.isEmpty()
                ? List.of()
                : llmReranker.rerank(request.question(), candidates, retrievalProperties.topK());
        List<ChunkSearchResult> chunks = reranked.stream().map(RankedChunk::chunk).toList();

        String answer;
        double confidence;
        if (chunks.isEmpty()) {
            answer = "Non ho trovato informazioni pertinenti nella documentazione aziendale per rispondere a questa domanda.";
            confidence = 0.0;
        } else {
            String context = contextBuilder.build(chunks);
            String prompt = SYSTEM_PROMPT + "\n\nContesto:\n" + context + "\n\nDomanda: " + request.question();
            answer = chatClient.generate(prompt, request.llmModel(), 0.2);
            confidence = reranked.stream().mapToDouble(rc -> rc.rerankScore() / 10.0).average().orElse(0.0);
        }

        int latencyMs = (int) (System.currentTimeMillis() - start);

        QueryLog log = new QueryLog();
        log.setTenantId(tenantId);
        log.setUserId(TenantContext.getAppUserId());
        log.setQuestion(request.question());
        log.setAnswer(answer);
        log.setConfidenceScore(BigDecimal.valueOf(confidence).setScale(3, RoundingMode.HALF_UP));
        log.setRetrievedChunkIds(chunks.stream().map(ChunkSearchResult::chunkId).toArray(UUID[]::new));
        log.setLatencyMs(latencyMs);
        log = queryLogRepository.save(log);

        auditService.record("QUERY_EXECUTED", "query_log", log.getId(),
                Map.of("question", request.question(), "sourcesCount", chunks.size()));

        List<QuerySourceResponse> sources = reranked.stream()
                .map(this::toSource)
                .toList();

        String conversationId = request.conversationId() != null ? request.conversationId() : UUID.randomUUID().toString();

        return new QueryResponse(answer, round(confidence), sources, conversationId, log.getId());
    }

    private QuerySourceResponse toSource(RankedChunk rankedChunk) {
        ChunkSearchResult chunk = rankedChunk.chunk();
        String excerpt = chunk.content().length() > 400 ? chunk.content().substring(0, 400) + "..." : chunk.content();
        return new QuerySourceResponse(chunk.documentId(), chunk.documentTitle(), chunk.versionLabel(),
                chunk.page(), chunk.section(), excerpt, round(rankedChunk.rerankScore() / 10.0));
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }
}
