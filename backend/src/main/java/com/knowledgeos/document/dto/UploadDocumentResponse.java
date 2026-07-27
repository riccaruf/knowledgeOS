package com.knowledgeos.document.dto;

import java.util.UUID;

public record UploadDocumentResponse(UUID documentId, UUID versionId, String ingestionStatus) {}
