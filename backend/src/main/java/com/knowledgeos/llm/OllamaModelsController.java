package com.knowledgeos.llm;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Espone al frontend i modelli Ollama disponibili sull'host, in modo che
 * l'utente possa scegliere quale LLM usare per la chat senza dover
 * modificare la configurazione del deployment.
 */
@RestController
@RequestMapping("/api/v1/models")
@RequiredArgsConstructor
public class OllamaModelsController {

    private record OllamaModel(String name) {}
    private record OllamaTagsResponse(List<OllamaModel> models) {}

    public record ModelInfo(String name, String defaultModel) {}
    public record ModelsResponse(List<String> models, String defaultModel) {}

    private final RestClient ollamaRestClient;
    private final OllamaProperties properties;

    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER','DOCUMENT_MANAGER','KNOWLEDGE_EDITOR','TENANT_ADMIN')")
    public ModelsResponse listModels() {
        OllamaTagsResponse tags = ollamaRestClient.get()
                .uri("/api/tags")
                .retrieve()
                .body(OllamaTagsResponse.class);

        List<String> names = (tags != null && tags.models() != null)
                ? tags.models().stream()
                        .map(OllamaModel::name)
                        // Escludiamo il modello di embedding dalla lista LLM
                        .filter(n -> !n.startsWith(properties.embeddingModel()))
                        .toList()
                : List.of();

        return new ModelsResponse(names, properties.llmModel());
    }
}
