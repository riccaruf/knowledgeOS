package com.knowledgeos.rag;

import com.knowledgeos.knowledge.ChunkSearchResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reciprocal Rank Fusion tra il ranking vettoriale e quello keyword
 * (05_RAG_PIPELINE.md §5.3): combina le posizioni nei due ranking invece dei
 * punteggi grezzi (non direttamente confrontabili tra coseno e ts_rank_cd).
 * Un chunk assente da una delle due liste contribuisce solo il termine
 * dell'altra, non viene penalizzato.
 */
public final class RrfMerger {

    private RrfMerger() {
    }

    public static List<ChunkSearchResult> merge(List<ChunkSearchResult> vectorRanked,
                                                  List<ChunkSearchResult> keywordRanked,
                                                  int rrfK,
                                                  int limit) {
        Map<UUID, ChunkSearchResult> chunksById = new LinkedHashMap<>();
        Map<UUID, Double> scoresById = new LinkedHashMap<>();

        accumulate(vectorRanked, rrfK, chunksById, scoresById);
        accumulate(keywordRanked, rrfK, chunksById, scoresById);

        return scoresById.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(limit)
                .map(entry -> chunksById.get(entry.getKey()))
                .toList();
    }

    private static void accumulate(List<ChunkSearchResult> ranked, int rrfK,
                                    Map<UUID, ChunkSearchResult> chunksById, Map<UUID, Double> scoresById) {
        for (int i = 0; i < ranked.size(); i++) {
            ChunkSearchResult chunk = ranked.get(i);
            int rank = i + 1;
            chunksById.putIfAbsent(chunk.chunkId(), chunk);
            scoresById.merge(chunk.chunkId(), 1.0 / (rrfK + rank), Double::sum);
        }
    }
}
