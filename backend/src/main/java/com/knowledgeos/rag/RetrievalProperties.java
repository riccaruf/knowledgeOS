package com.knowledgeos.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("knowledgeos.retrieval")
public record RetrievalProperties(int topK) {
}
