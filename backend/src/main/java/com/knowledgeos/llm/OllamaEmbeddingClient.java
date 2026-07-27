package com.knowledgeos.llm;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Client verso il modello di embedding, servito da Ollama ma trattato come
 * componente logicamente separato dall'LLM generativo (05_RAG_PIPELINE.md §4).
 */
@Service
@RequiredArgsConstructor
public class OllamaEmbeddingClient {

    private record EmbeddingRequest(String model, String prompt) {}
    private record EmbeddingResponse(List<Double> embedding) {}

    private final RestClient ollamaRestClient;
    private final OllamaProperties properties;

    public float[] embed(String text) {
        EmbeddingResponse response = ollamaRestClient.post()
                .uri("/api/embeddings")
                .body(new EmbeddingRequest(properties.embeddingModel(), text))
                .retrieve()
                .body(EmbeddingResponse.class);

        if (response == null || response.embedding() == null) {
            throw new IllegalStateException("Ollama non ha restituito un embedding valido.");
        }
        float[] vector = new float[response.embedding().size()];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = response.embedding().get(i).floatValue();
        }
        return vector;
    }
}
