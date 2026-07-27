package com.knowledgeos.document.dto;

import java.util.List;
import java.util.UUID;

public record DocumentDetailResponse(
        UUID id,
        String title,
        String category,
        String department,
        List<String> tags,
        String lifecycleStatus,
        DocumentVersionResponse currentVersion,
        List<DocumentVersionResponse> versions
) {}
