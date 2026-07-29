package com.knowledgeos.rag;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * topK e' il conteggio finale dopo il reranking (05_RAG_PIPELINE.md §5.4);
 * candidatePoolSize e' l'ampiezza del pool ibrido su cui il reranking opera,
 * a monte di quel taglio finale.
 */
@ConfigurationProperties("knowledgeos.retrieval")
public record RetrievalProperties(int topK, int candidatePoolSize, int rrfK, int rerankExcerptChars) {
}
