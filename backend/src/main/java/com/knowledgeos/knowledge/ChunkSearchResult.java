package com.knowledgeos.knowledge;

import java.util.UUID;

public record ChunkSearchResult(
        UUID chunkId,
        UUID documentId,
        UUID documentVersionId,
        String documentTitle,
        String versionLabel,
        String section,
        int page,
        String content,
        double similarity
) {}
