package com.knowledgeos.rag;

import com.knowledgeos.audit.AuditService;
import com.knowledgeos.knowledge.ChunkSearchResult;
import com.knowledgeos.llm.OllamaChatClient;
import com.knowledgeos.llm.OllamaEmbeddingClient;
import com.knowledgeos.rag.dto.QueryRequest;
import com.knowledgeos.rag.dto.QueryResponse;
import com.knowledgeos.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AnswerGenerationServiceTest {

    private OllamaEmbeddingClient embeddingClient;
    private OllamaChatClient chatClient;
    private HybridSearchService hybridSearchService;
    private LlmReranker llmReranker;
    private QueryLogRepository queryLogRepository;
    private AuditService auditService;
    private AnswerGenerationService service;

    @BeforeEach
    void setUp() {
        embeddingClient = mock(OllamaEmbeddingClient.class);
        chatClient = mock(OllamaChatClient.class);
        hybridSearchService = mock(HybridSearchService.class);
        llmReranker = mock(LlmReranker.class);
        queryLogRepository = mock(QueryLogRepository.class);
        auditService = mock(AuditService.class);
        RetrievalProperties retrievalProperties = new RetrievalProperties(6, 40, 60, 600);

        service = new AnswerGenerationService(embeddingClient, chatClient, hybridSearchService,
                llmReranker, new ContextBuilder(), queryLogRepository, auditService, retrievalProperties);

        when(embeddingClient.embed(anyString())).thenReturn(new float[]{0.1f});
        when(queryLogRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TenantContext.set(new TenantContext.Data(UUID.randomUUID(), UUID.randomUUID(),
                "subject", "user@example.com", "User", Set.of("VIEWER")));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static ChunkSearchResult chunk(UUID id) {
        return new ChunkSearchResult(id, UUID.randomUUID(), UUID.randomUUID(),
                "Documento", "v1", "Sezione", 1, "contenuto", 0.0);
    }

    @Test
    void noCandidatesReturnsFallbackAnswerAndSkipsRerankAndLlm() {
        when(hybridSearchService.search(any(), anyString(), any(), any())).thenReturn(List.of());

        QueryResponse response = service.answer(new QueryRequest("domanda?", null, null));

        assertThat(response.confidence()).isEqualTo(0.0);
        assertThat(response.sources()).isEmpty();
        assertThat(response.answer()).contains("Non ho trovato informazioni pertinenti");
        verifyNoInteractions(llmReranker);
        verify(chatClient, never()).generate(anyString());
    }

    @Test
    void confidenceIsAverageOfNormalizedRerankScores() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        List<ChunkSearchResult> candidates = List.of(chunk(id1), chunk(id2));
        when(hybridSearchService.search(any(), anyString(), any(), any())).thenReturn(candidates);
        when(llmReranker.rerank(anyString(), eq(candidates), eq(6))).thenReturn(List.of(
                new RankedChunk(candidates.get(0), 8.0),
                new RankedChunk(candidates.get(1), 4.0)));
        when(chatClient.generate(anyString())).thenReturn("Risposta generata.");

        QueryResponse response = service.answer(new QueryRequest("domanda?", null, null));

        assertThat(response.confidence()).isCloseTo(0.6, within(0.001));
    }

    @Test
    void relevanceScoreInSourcesIsNormalizedRerankScore() {
        UUID id1 = UUID.randomUUID();
        List<ChunkSearchResult> candidates = List.of(chunk(id1));
        when(hybridSearchService.search(any(), anyString(), any(), any())).thenReturn(candidates);
        when(llmReranker.rerank(anyString(), eq(candidates), eq(6)))
                .thenReturn(List.of(new RankedChunk(candidates.get(0), 7.5)));
        when(chatClient.generate(anyString())).thenReturn("Risposta generata.");

        QueryResponse response = service.answer(new QueryRequest("domanda?", null, null));

        assertThat(response.sources()).hasSize(1);
        assertThat(response.sources().get(0).relevanceScore()).isCloseTo(0.75, within(0.001));
    }
}
