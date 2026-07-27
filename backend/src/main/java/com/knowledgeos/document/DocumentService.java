package com.knowledgeos.document;

import com.knowledgeos.audit.AuditService;
import com.knowledgeos.common.exception.NotFoundException;
import com.knowledgeos.document.dto.*;
import com.knowledgeos.ingestion.IngestionOrchestrator;
import com.knowledgeos.knowledge.ChunkRepository;
import com.knowledgeos.storage.ObjectStorageService;
import com.knowledgeos.tenant.Tenant;
import com.knowledgeos.tenant.TenantContext;
import com.knowledgeos.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final TenantRepository tenantRepository;
    private final ObjectStorageService objectStorageService;
    private final IngestionOrchestrator ingestionOrchestrator;
    private final AuditService auditService;
    private final ChunkRepository chunkRepository;

    public Page<DocumentSummaryResponse> list(Pageable pageable) {
        UUID tenantId = TenantContext.getTenantId();
        return documentRepository.findByTenantIdAndDeletedAtIsNull(tenantId, pageable)
                .map(this::toSummary);
    }

    public DocumentDetailResponse getDetail(UUID documentId) {
        UUID tenantId = TenantContext.getTenantId();
        Document document = documentRepository.findByIdAndTenantIdAndDeletedAtIsNull(documentId, tenantId)
                .orElseThrow(() -> new NotFoundException("Documento non trovato."));
        List<DocumentVersionResponse> versions = versionRepository
                .findByDocumentIdAndTenantIdOrderByUploadedAtDesc(documentId, tenantId).stream()
                .map(this::toVersionResponse)
                .toList();
        DocumentVersionResponse current = versions.stream()
                .filter(v -> v.id().equals(document.getCurrentVersionId()))
                .findFirst().orElse(versions.isEmpty() ? null : versions.get(0));
        return new DocumentDetailResponse(document.getId(), document.getTitle(), document.getCategory(),
                document.getDepartment(), List.of(document.getTags()), document.getLifecycleStatus(),
                current, versions);
    }

    @Transactional
    public UploadDocumentResponse uploadNewDocument(String title, String category, String department,
                                                     List<String> tags, MultipartFile file) {
        UUID tenantId = TenantContext.getTenantId();
        UUID userId = TenantContext.getAppUserId();

        Document document = new Document();
        document.setTenantId(tenantId);
        document.setTitle(title);
        document.setCategory(category);
        document.setDepartment(department);
        document.setTags(tags == null ? new String[0] : tags.toArray(new String[0]));
        document.setLifecycleStatus("PUBLISHED");
        document.setCreatedBy(userId);
        document = documentRepository.save(document);

        DocumentVersion version = storeNewVersion(tenantId, userId, document.getId(), "v1", file);
        document.setCurrentVersionId(version.getId());
        documentRepository.save(document);

        auditService.record("DOCUMENT_UPLOADED", "document", document.getId(),
                Map.of("versionId", version.getId(), "title", title));

        return new UploadDocumentResponse(document.getId(), version.getId(), version.getIngestionStatus());
    }

    @Transactional
    public UploadDocumentResponse uploadNewVersion(UUID documentId, String versionLabel, MultipartFile file) {
        UUID tenantId = TenantContext.getTenantId();
        UUID userId = TenantContext.getAppUserId();

        Document document = documentRepository.findByIdAndTenantIdAndDeletedAtIsNull(documentId, tenantId)
                .orElseThrow(() -> new NotFoundException("Documento non trovato."));

        String label = versionLabel != null ? versionLabel
                : "v" + (versionRepository.findByDocumentIdAndTenantIdOrderByUploadedAtDesc(documentId, tenantId).size() + 1);

        DocumentVersion version = storeNewVersion(tenantId, userId, documentId, label, file);
        document.setCurrentVersionId(version.getId());
        document.setUpdatedAt(Instant.now());
        documentRepository.save(document);

        auditService.record("DOCUMENT_VERSION_UPLOADED", "document", documentId,
                Map.of("versionId", version.getId(), "versionLabel", label));

        return new UploadDocumentResponse(document.getId(), version.getId(), version.getIngestionStatus());
    }

    public IngestionStatusResponse getIngestionStatus(UUID documentId, UUID versionId) {
        UUID tenantId = TenantContext.getTenantId();
        DocumentVersion version = versionRepository.findByIdAndDocumentIdAndTenantId(versionId, documentId, tenantId)
                .orElseThrow(() -> new NotFoundException("Versione non trovata."));
        return new IngestionStatusResponse(version.getIngestionStatus(), null, null, version.getIngestionError());
    }

    @Transactional
    public void update(UUID documentId, UpdateDocumentRequest request) {
        UUID tenantId = TenantContext.getTenantId();
        Document document = documentRepository.findByIdAndTenantIdAndDeletedAtIsNull(documentId, tenantId)
                .orElseThrow(() -> new NotFoundException("Documento non trovato."));
        if (request.title() != null) document.setTitle(request.title());
        if (request.category() != null) document.setCategory(request.category());
        if (request.department() != null) document.setDepartment(request.department());
        if (request.tags() != null) document.setTags(request.tags().toArray(new String[0]));
        if (request.lifecycleStatus() != null) document.setLifecycleStatus(request.lifecycleStatus());
        document.setUpdatedAt(Instant.now());
        documentRepository.save(document);
        auditService.record("DOCUMENT_UPDATED", "document", documentId, Map.of());
    }

    /**
     * Elimina un documento: rimuove fisicamente i file dal data lake (MinIO) e
     * i chunk vettoriali (pgvector) — altrimenti il contenuto resterebbe
     * comunque trovabile dal retrieval nonostante il documento risulti
     * "eliminato" nella UI. La riga documento/versione resta (soft delete,
     * deleted_at) per preservare la tracciabilita' in audit_log di cosa e'
     * stato eliminato, quando e da chi (06_SECURITY_MODEL.md §5-6).
     */
    @Transactional
    public void softDelete(UUID documentId) {
        UUID tenantId = TenantContext.getTenantId();
        Document document = documentRepository.findByIdAndTenantIdAndDeletedAtIsNull(documentId, tenantId)
                .orElseThrow(() -> new NotFoundException("Documento non trovato."));
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant non trovato."));

        List<DocumentVersion> versions =
                versionRepository.findByDocumentIdAndTenantIdOrderByUploadedAtDesc(documentId, tenantId);
        for (DocumentVersion version : versions) {
            objectStorageService.delete(tenant.getStorageBucket(), version.getFileObjectKey());
        }
        chunkRepository.deleteByDocumentId(tenantId, documentId);

        document.setDeletedAt(Instant.now());
        document.setLifecycleStatus("ARCHIVED");
        documentRepository.save(document);

        auditService.record("DOCUMENT_DELETED", "document", documentId,
                Map.of("title", document.getTitle(), "versionsRemoved", versions.size()));
    }

    private DocumentVersion storeNewVersion(UUID tenantId, UUID userId, UUID documentId, String versionLabel,
                                             MultipartFile file) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NotFoundException("Tenant non trovato."));

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new com.knowledgeos.common.exception.UnprocessableDocumentException(
                    "Impossibile leggere il file caricato.", e);
        }

        String objectKey = "%s/%s.pdf".formatted(documentId, UUID.randomUUID());
        objectStorageService.put(tenant.getStorageBucket(), objectKey,
                new java.io.ByteArrayInputStream(bytes), bytes.length,
                file.getContentType() != null ? file.getContentType() : "application/pdf");

        DocumentVersion version = new DocumentVersion();
        version.setTenantId(tenantId);
        version.setDocumentId(documentId);
        version.setVersionLabel(versionLabel);
        version.setFileObjectKey(objectKey);
        version.setFileMimeType(file.getContentType() != null ? file.getContentType() : "application/pdf");
        version.setFileSizeBytes(bytes.length);
        version.setChecksumSha256(sha256(bytes));
        version.setUploadedBy(userId);
        version.setIngestionStatus(DocumentVersion.STATUS_PENDING);
        version = versionRepository.save(version);

        ingestionOrchestrator.enqueue(tenantId, version.getId());

        return version;
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private DocumentSummaryResponse toSummary(Document document) {
        DocumentVersionResponse current = versionRepository
                .findByDocumentIdAndTenantIdOrderByUploadedAtDesc(document.getId(), document.getTenantId()).stream()
                .filter(v -> v.getId().equals(document.getCurrentVersionId()))
                .findFirst()
                .map(this::toVersionResponse)
                .orElse(null);
        return new DocumentSummaryResponse(document.getId(), document.getTitle(), document.getCategory(),
                document.getDepartment(), List.of(document.getTags()), document.getLifecycleStatus(), current);
    }

    private DocumentVersionResponse toVersionResponse(DocumentVersion version) {
        return new DocumentVersionResponse(version.getId(), version.getVersionLabel(),
                version.getIngestionStatus(), version.getUploadedAt());
    }
}
