package com.knowledgeos.rag;

import com.knowledgeos.knowledge.ChunkRepository;
import com.knowledgeos.knowledge.ChunkSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Hybrid search (05_RAG_PIPELINE.md §5.3): fonde ricerca vettoriale e keyword
 * sullo stesso sottoinsieme filtrato per tenant/categoria, restituendo un pool
 * ampio di candidati (non il top-K finale) da passare al reranking.
 */
@Service
@RequiredArgsConstructor
public class HybridSearchService {

    private final ChunkRepository chunkRepository;
    private final RetrievalProperties retrievalProperties;

    public List<ChunkSearchResult> search(UUID tenantId, String question, float[] questionEmbedding, List<String> categories) {
        int candidatePoolSize = retrievalProperties.candidatePoolSize();

        List<ChunkSearchResult> vectorRanked = chunkRepository.searchByVector(
                tenantId, questionEmbedding, candidatePoolSize, categories);
        List<ChunkSearchResult> keywordRanked = chunkRepository.searchByKeyword(
                tenantId, question, questionEmbedding, candidatePoolSize, categories);

        return RrfMerger.merge(vectorRanked, keywordRanked, retrievalProperties.rrfK(), candidatePoolSize);
    }
}
