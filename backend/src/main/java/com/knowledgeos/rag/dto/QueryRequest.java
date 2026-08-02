package com.knowledgeos.rag.dto;

import jakarta.validation.constraints.NotBlank;

public record QueryRequest(@NotBlank String question, String conversationId, QueryFilters filters, String llmModel) {}
