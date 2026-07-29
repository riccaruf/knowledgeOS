package com.knowledgeos.rag;

import com.knowledgeos.knowledge.ChunkSearchResult;

/**
 * Un chunk candidato con il punteggio di pertinenza (0-10) assegnato dal
 * reranking (05_RAG_PIPELINE.md §5.4).
 */
public record RankedChunk(ChunkSearchResult chunk, double rerankScore) {
}
