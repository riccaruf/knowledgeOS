package com.knowledgeos.document.dto;

import java.time.Instant;

public record IngestionStatusResponse(String status, String step, Instant startedAt, String error) {}
