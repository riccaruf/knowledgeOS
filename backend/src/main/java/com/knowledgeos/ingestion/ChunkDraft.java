package com.knowledgeos.ingestion;

public record ChunkDraft(String title, String section, int page, String content, int chunkIndex) {}
