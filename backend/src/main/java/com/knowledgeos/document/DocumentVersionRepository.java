package com.knowledgeos.document;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    Optional<DocumentVersion> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<DocumentVersion> findByIdAndDocumentIdAndTenantId(UUID id, UUID documentId, UUID tenantId);

    List<DocumentVersion> findByDocumentIdAndTenantIdOrderByUploadedAtDesc(UUID documentId, UUID tenantId);
}
