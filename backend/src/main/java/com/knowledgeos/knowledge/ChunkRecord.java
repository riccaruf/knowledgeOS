package com.knowledgeos.knowledge;

import java.util.Map;
import java.util.UUID;

public record ChunkRecord(
        UUID tenantId,
        UUID documentId,
        UUID documentVersionId,
        String title,
        String section,
        int page,
        String content,
        Map<String, Object> metadata,
        float[] embedding,
        String embeddingModel,
        int chunkIndex
) {}
