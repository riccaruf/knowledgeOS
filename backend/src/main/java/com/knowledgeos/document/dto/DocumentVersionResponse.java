package com.knowledgeos.document.dto;

import java.time.Instant;
import java.util.UUID;

public record DocumentVersionResponse(
        UUID id,
        String versionLabel,
        String ingestionStatus,
        Instant uploadedAt
) {}
