package com.knowledgeos.document.dto;

import java.util.List;

public record UpdateDocumentRequest(String title, String category, String department, List<String> tags,
                                     String lifecycleStatus) {}
