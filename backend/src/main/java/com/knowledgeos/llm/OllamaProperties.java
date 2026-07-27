package com.knowledgeos.llm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurazione del runtime Ollama. llmModel ed embeddingModel sono
 * deliberatamente due proprieta' distinte anche se serviti dallo stesso
 * runtime: la separazione logica LLM/embedding (02_SOLUTION_ARCHITECTURE.md
 * §1) permette di sostituire l'uno senza toccare l'altro.
 */
@ConfigurationProperties("knowledgeos.ollama")
public record OllamaProperties(String baseUrl, String llmModel, String embeddingModel, int embeddingDimension) {
}
