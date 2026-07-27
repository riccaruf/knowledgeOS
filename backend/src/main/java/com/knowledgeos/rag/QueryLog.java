package com.knowledgeos.rag;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "query_log")
@Getter
@Setter
@NoArgsConstructor
public class QueryLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String question;

    private String answer;

    @Column(name = "confidence_score")
    private BigDecimal confidenceScore;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "retrieved_chunk_ids", columnDefinition = "uuid[]")
    private UUID[] retrievedChunkIds = new UUID[0];

    @Column(name = "llm_model_config_id")
    private UUID llmModelConfigId;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
