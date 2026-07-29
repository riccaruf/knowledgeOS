package com.knowledgeos.llm;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Client verso il modello linguistico generativo (Ollama /api/generate).
 * Il modello e' generico e sostituibile (Qwen/Llama/Gemma/Mistral) — nessun
 * fine-tuning, la conoscenza aziendale arriva sempre nel prompt come contesto
 * (05_RAG_PIPELINE.md §7).
 */
@Service
@RequiredArgsConstructor
public class OllamaChatClient {

    private record GenerateRequest(String model, String prompt, boolean stream, GenerateOptions options) {}
    private record GenerateOptions(double temperature) {}
    private record GenerateResponse(String response) {}

    private final RestClient ollamaRestClient;
    private final OllamaProperties properties;

    public String generate(String prompt) {
        return generate(prompt, 0.2);
    }

    public String generate(String prompt, double temperature) {
        GenerateResponse response = ollamaRestClient.post()
                .uri("/api/generate")
                .body(new GenerateRequest(properties.llmModel(), prompt, false, new GenerateOptions(temperature)))
                .retrieve()
                .body(GenerateResponse.class);

        if (response == null || response.response() == null) {
            throw new IllegalStateException("Ollama non ha restituito una risposta valida.");
        }
        return response.response().strip();
    }
}
