package com.knowledgeos.document;

import com.knowledgeos.document.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * 04_API_SPECIFICATION.md §3 — Documenti.
 */
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    @GetMapping
    @PreAuthorize("hasAnyRole('VIEWER','DOCUMENT_MANAGER','KNOWLEDGE_EDITOR','TENANT_ADMIN')")
    public Page<DocumentSummaryResponse> list(Pageable pageable) {
        return documentService.list(pageable);
    }

    @GetMapping("/{documentId}")
    @PreAuthorize("hasAnyRole('VIEWER','DOCUMENT_MANAGER','KNOWLEDGE_EDITOR','TENANT_ADMIN')")
    public DocumentDetailResponse detail(@PathVariable UUID documentId) {
        return documentService.getDetail(documentId);
    }

    @PostMapping(consumes = "multipart/form-data")
    @PreAuthorize("hasRole('DOCUMENT_MANAGER')")
    public ResponseEntity<UploadDocumentResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam String title,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) List<String> tags) {
        UploadDocumentResponse response = documentService.uploadNewDocument(title, category, department, tags, file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @PostMapping(value = "/{documentId}/versions", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('DOCUMENT_MANAGER')")
    public ResponseEntity<UploadDocumentResponse> uploadVersion(
            @PathVariable UUID documentId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String versionLabel) {
        UploadDocumentResponse response = documentService.uploadNewVersion(documentId, versionLabel, file);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{documentId}/versions/{versionId}/ingestion-status")
    @PreAuthorize("hasAnyRole('VIEWER','DOCUMENT_MANAGER','KNOWLEDGE_EDITOR','TENANT_ADMIN')")
    public IngestionStatusResponse ingestionStatus(@PathVariable UUID documentId, @PathVariable UUID versionId) {
        return documentService.getIngestionStatus(documentId, versionId);
    }

    @PatchMapping("/{documentId}")
    @PreAuthorize("hasRole('DOCUMENT_MANAGER')")
    public ResponseEntity<Void> update(@PathVariable UUID documentId, @RequestBody UpdateDocumentRequest request) {
        documentService.update(documentId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{documentId}")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID documentId) {
        documentService.softDelete(documentId);
        return ResponseEntity.noContent().build();
    }
}
