package com.knowledgeos.document;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "document_version")
@Getter
@Setter
@NoArgsConstructor
public class DocumentVersion {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_PROCESSED = "PROCESSED";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "version_label", nullable = false)
    private String versionLabel;

    @Column(name = "file_object_key", nullable = false)
    private String fileObjectKey;

    @Column(name = "file_mime_type", nullable = false)
    private String fileMimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    @Column(name = "checksum_sha256")
    private String checksumSha256;

    private String author;

    @Column(name = "ingestion_status", nullable = false)
    private String ingestionStatus = STATUS_PENDING;

    @Column(name = "ingestion_error")
    private String ingestionError;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private Instant uploadedAt = Instant.now();
}
