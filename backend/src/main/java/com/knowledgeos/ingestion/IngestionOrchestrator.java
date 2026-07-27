package com.knowledgeos.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Riceve il trigger di una nuova versione documento e accoda un job di
 * ingestion (02_SOLUTION_ARCHITECTURE.md §3.2). L'esecuzione effettiva e'
 * demandata a IngestionWorker (polling asincrono, virtual threads).
 */
@Service
@RequiredArgsConstructor
public class IngestionOrchestrator {

    private final IngestionJobRepository jobRepository;

    @Transactional
    public IngestionJob enqueue(UUID tenantId, UUID documentVersionId) {
        IngestionJob job = new IngestionJob();
        job.setTenantId(tenantId);
        job.setDocumentVersionId(documentVersionId);
        job.setStatus(IngestionJob.STATUS_QUEUED);
        return jobRepository.save(job);
    }
}
