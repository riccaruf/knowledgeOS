package com.knowledgeos.rag.dto;

import java.util.UUID;

public record QuerySourceResponse(
        UUID documentId,
        String documentTitle,
        String versionLabel,
        int page,
        String section,
        String excerpt,
        double relevanceScore
) {}
