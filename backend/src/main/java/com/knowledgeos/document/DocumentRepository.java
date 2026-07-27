package com.knowledgeos.document;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Page<Document> findByTenantIdAndDeletedAtIsNull(UUID tenantId, Pageable pageable);

    Optional<Document> findByIdAndTenantIdAndDeletedAtIsNull(UUID id, UUID tenantId);
}
