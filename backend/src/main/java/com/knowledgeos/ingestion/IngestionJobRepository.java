package com.knowledgeos.ingestion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IngestionJobRepository extends JpaRepository<IngestionJob, UUID> {

    Optional<IngestionJob> findByDocumentVersionIdAndTenantId(UUID documentVersionId, UUID tenantId);

    List<IngestionJob> findByTenantIdAndStatusOrderByCreatedAtAsc(UUID tenantId, String status);
}
