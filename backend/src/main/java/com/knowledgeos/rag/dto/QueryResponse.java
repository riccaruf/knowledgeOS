package com.knowledgeos.rag.dto;

import java.util.List;
import java.util.UUID;

public record QueryResponse(
        String answer,
        double confidence,
        List<QuerySourceResponse> sources,
        String conversationId,
        UUID queryLogId
) {}
