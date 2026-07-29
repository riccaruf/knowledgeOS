package com.knowledgeos.rag;

import com.knowledgeos.knowledge.ChunkSearchResult;
import com.knowledgeos.llm.OllamaChatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmRerankerTest {

    private OllamaChatClient chatClient;
    private LlmReranker reranker;

    @BeforeEach
    void setUp() {
        chatClient = mock(OllamaChatClient.class);
        RetrievalProperties properties = new RetrievalProperties(6, 40, 60, 600);
        reranker = new LlmReranker(chatClient, properties);
    }

    private static ChunkSearchResult chunk(UUID id) {
        return new ChunkSearchResult(id, UUID.randomUUID(), UUID.randomUUID(),
                "Documento", "v1", "Sezione", 1, "contenuto di esempio", 0.0);
    }

    @Test
    void ordersCandidatesByParsedScoreDescending() {
        UUID low = UUID.randomUUID();
        UUID high = UUID.randomUUID();
        List<ChunkSearchResult> candidates = List.of(chunk(low), chunk(high));
        when(chatClient.generate(anyString(), anyDouble())).thenReturn("1|2\n2|9");

        List<RankedChunk> result = reranker.rerank("domanda", candidates, 2);

        assertThat(result).extracting(rc -> rc.chunk().chunkId()).containsExactly(high, low);
    }

    @Test
    void truncatesToFinalTopN() {
        List<ChunkSearchResult> candidates = List.of(
                chunk(UUID.randomUUID()), chunk(UUID.randomUUID()), chunk(UUID.randomUUID()));
        when(chatClient.generate(anyString(), anyDouble())).thenReturn("1|5\n2|6\n3|7");

        List<RankedChunk> result = reranker.rerank("domanda", candidates, 1);

        assertThat(result).hasSize(1);
    }

    @Test
    void fallsBackToHybridOrderWhenOllamaThrows() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<ChunkSearchResult> candidates = List.of(chunk(first), chunk(second));
        when(chatClient.generate(anyString(), anyDouble())).thenThrow(new RuntimeException("Ollama non raggiungibile"));

        List<RankedChunk> result = reranker.rerank("domanda", candidates, 2);

        assertThat(result).extracting(rc -> rc.chunk().chunkId()).containsExactly(first, second);
    }

    @Test
    void fallsBackToHybridOrderWhenResponseIsUnparseable() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<ChunkSearchResult> candidates = List.of(chunk(first), chunk(second));
        when(chatClient.generate(anyString(), anyDouble())).thenReturn("non riesco a valutare questi passaggi");

        List<RankedChunk> result = reranker.rerank("domanda", candidates, 2);

        assertThat(result).extracting(rc -> rc.chunk().chunkId()).containsExactly(first, second);
    }

    @Test
    void partialParseFillsMissingIndicesWithPositionalFallback() {
        UUID scored = UUID.randomUUID();
        UUID unscored = UUID.randomUUID();
        List<ChunkSearchResult> candidates = List.of(chunk(scored), chunk(unscored));
        // Solo l'indice 1 viene valutato dal modello; l'indice 2 deve ricevere
        // il punteggio posizionale di fallback e non far crashare il reranking.
        when(chatClient.generate(anyString(), anyDouble())).thenReturn("1|10");

        List<RankedChunk> result = reranker.rerank("domanda", candidates, 2);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).chunk().chunkId()).isEqualTo(scored);
    }
}
