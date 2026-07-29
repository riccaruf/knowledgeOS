package com.knowledgeos.rag;

import com.knowledgeos.knowledge.ChunkSearchResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RrfMergerTest {

    private static ChunkSearchResult chunk(UUID id) {
        return new ChunkSearchResult(id, UUID.randomUUID(), UUID.randomUUID(),
                "Documento", "v1", "Sezione", 1, "contenuto", 0.0);
    }

    @Test
    void disjointListsKeepBothChunksOrderedByCombinedRank() {
        UUID vectorOnly = UUID.randomUUID();
        UUID keywordOnly = UUID.randomUUID();

        List<ChunkSearchResult> merged = RrfMerger.merge(
                List.of(chunk(vectorOnly)), List.of(chunk(keywordOnly)), 60, 10);

        assertThat(merged).extracting(ChunkSearchResult::chunkId)
                .containsExactlyInAnyOrder(vectorOnly, keywordOnly);
    }

    @Test
    void chunkRankedTopInBothListsOutranksChunkPresentInOnlyOneList() {
        UUID topInBoth = UUID.randomUUID();
        UUID vectorOnly = UUID.randomUUID();

        List<ChunkSearchResult> vector = List.of(chunk(topInBoth), chunk(vectorOnly));
        List<ChunkSearchResult> keyword = List.of(chunk(topInBoth));

        List<ChunkSearchResult> merged = RrfMerger.merge(vector, keyword, 60, 10);

        assertThat(merged.get(0).chunkId()).isEqualTo(topInBoth);
    }

    @Test
    void emptyKeywordListFallsBackToPureVectorOrder() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        List<ChunkSearchResult> merged = RrfMerger.merge(
                List.of(chunk(first), chunk(second)), List.of(), 60, 10);

        assertThat(merged).extracting(ChunkSearchResult::chunkId).containsExactly(first, second);
    }

    @Test
    void emptyVectorListFallsBackToPureKeywordOrder() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        List<ChunkSearchResult> merged = RrfMerger.merge(
                List.of(), List.of(chunk(first), chunk(second)), 60, 10);

        assertThat(merged).extracting(ChunkSearchResult::chunkId).containsExactly(first, second);
    }

    @Test
    void resultIsTruncatedToLimit() {
        List<ChunkSearchResult> vector = List.of(chunk(UUID.randomUUID()), chunk(UUID.randomUUID()), chunk(UUID.randomUUID()));

        List<ChunkSearchResult> merged = RrfMerger.merge(vector, List.of(), 60, 2);

        assertThat(merged).hasSize(2);
    }

    @Test
    void duplicateChunkAcrossBothListsAppearsOnlyOnce() {
        UUID duplicated = UUID.randomUUID();

        List<ChunkSearchResult> merged = RrfMerger.merge(
                List.of(chunk(duplicated)), List.of(chunk(duplicated)), 60, 10);

        assertThat(merged).hasSize(1);
    }
}
