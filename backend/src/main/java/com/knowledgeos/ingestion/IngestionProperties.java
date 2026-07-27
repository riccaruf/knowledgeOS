package com.knowledgeos.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("knowledgeos.ingestion")
public record IngestionProperties(long pollIntervalMs, int chunkTargetTokens, int chunkMaxTokens) {
}
