package com.knowledgeos.ingestion;

import com.knowledgeos.document.Document;
import com.knowledgeos.document.DocumentRepository;
import com.knowledgeos.document.DocumentVersion;
import com.knowledgeos.document.DocumentVersionRepository;
import com.knowledgeos.ingestion.pdf.ParsedParagraph;
import com.knowledgeos.ingestion.pdf.PdfParsingService;
import com.knowledgeos.knowledge.ChunkRecord;
import com.knowledgeos.knowledge.ChunkRepository;
import com.knowledgeos.llm.OllamaEmbeddingClient;
import com.knowledgeos.llm.OllamaProperties;
import com.knowledgeos.storage.ObjectStorageService;
import com.knowledgeos.tenant.Tenant;
import com.knowledgeos.tenant.TenantContext;
import com.knowledgeos.tenant.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Worker asincrono di ingestion, in Java dentro il backend (non un servizio
 * Python separato — decisione presa nel piano di implementazione per l'MVP).
 * Poll periodico su ingestion_job per tenant attivo; esegue
 * download -> parsing -> chunking -> embedding -> scrittura chunk
 * (05_RAG_PIPELINE.md §2).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IngestionWorker {

    private final TenantRepository tenantRepository;
    private final IngestionJobRepository jobRepository;
    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository versionRepository;
    private final ObjectStorageService objectStorageService;
    private final PdfParsingService pdfParsingService;
    private final ChunkingService chunkingService;
    private final OllamaEmbeddingClient embeddingClient;
    private final ChunkRepository chunkRepository;
    private final OllamaProperties ollamaProperties;

    @Scheduled(fixedDelayString = "${knowledgeos.ingestion.poll-interval-ms}")
    public void pollAndProcess() {
        for (Tenant tenant : tenantRepository.findByStatus("ACTIVE")) {
            TenantContext.set(new TenantContext.Data(tenant.getId(), null, "ingestion-worker", null,
                    "Ingestion Worker", Set.of()));
            try {
                List<IngestionJob> queued = jobRepository
                        .findByTenantIdAndStatusOrderByCreatedAtAsc(tenant.getId(), IngestionJob.STATUS_QUEUED);
                for (IngestionJob job : queued) {
                    processJob(tenant, job);
                }
            } finally {
                TenantContext.clear();
            }
        }
    }

    @Transactional
    public void processJob(Tenant tenant, IngestionJob job) {
        job.setStatus(IngestionJob.STATUS_RUNNING);
        job.setStartedAt(Instant.now());
        job.setAttempts(job.getAttempts() + 1);
        jobRepository.save(job);

        DocumentVersion version = versionRepository
                .findByIdAndTenantId(job.getDocumentVersionId(), tenant.getId())
                .orElse(null);
        if (version == null) {
            failJob(job, "Versione documento non trovata.");
            return;
        }
        version.setIngestionStatus(DocumentVersion.STATUS_PROCESSING);
        versionRepository.save(version);

        try {
            Document document = documentRepository.findByIdAndTenantIdAndDeletedAtIsNull(version.getDocumentId(), tenant.getId())
                    .orElseThrow(() -> new IllegalStateException("Documento non trovato per la versione in elaborazione."));

            job.setStep("DOWNLOAD");
            jobRepository.save(job);
            byte[] bytes;
            try (var stream = objectStorageService.get(tenant.getStorageBucket(), version.getFileObjectKey())) {
                bytes = stream.readAllBytes();
            }

            job.setStep("PARSING");
            jobRepository.save(job);
            List<ParsedParagraph> paragraphs = pdfParsingService.parse(bytes);

            job.setStep("CHUNKING");
            jobRepository.save(job);
            List<ChunkDraft> drafts = chunkingService.chunk(document.getTitle(), paragraphs);

            job.setStep("EMBEDDING");
            jobRepository.save(job);
            Map<String, Object> baseMetadata = buildMetadata(document, version);
            for (ChunkDraft draft : drafts) {
                float[] embedding = embeddingClient.embed(draft.content());
                chunkRepository.insert(new ChunkRecord(
                        tenant.getId(), document.getId(), version.getId(), draft.title(), draft.section(),
                        draft.page(), draft.content(), baseMetadata, embedding,
                        ollamaProperties.embeddingModel(), draft.chunkIndex()));
            }

            version.setIngestionStatus(DocumentVersion.STATUS_PROCESSED);
            version.setIngestionError(null);
            versionRepository.save(version);

            job.setStatus(IngestionJob.STATUS_DONE);
            job.setStep("DONE");
            job.setFinishedAt(Instant.now());
            jobRepository.save(job);

        } catch (Exception e) {
            log.error("Ingestion fallita per documentVersionId={}", job.getDocumentVersionId(), e);
            version.setIngestionStatus(DocumentVersion.STATUS_FAILED);
            version.setIngestionError(e.getMessage());
            versionRepository.save(version);
            failJob(job, e.getMessage());
        }
    }

    private void failJob(IngestionJob job, String error) {
        job.setStatus(IngestionJob.STATUS_FAILED);
        job.setError(error);
        job.setFinishedAt(Instant.now());
        jobRepository.save(job);
    }

    private Map<String, Object> buildMetadata(Document document, DocumentVersion version) {
        Map<String, Object> metadata = new HashMap<>();
        if (document.getCategory() != null) metadata.put("category", document.getCategory());
        if (document.getTags() != null && document.getTags().length > 0) metadata.put("tags", document.getTags());
        if (version.getAuthor() != null) metadata.put("author", version.getAuthor());
        metadata.put("versionLabel", version.getVersionLabel());
        metadata.put("uploadedAt", version.getUploadedAt().toString());
        return metadata;
    }
}
