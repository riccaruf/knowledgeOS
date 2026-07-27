package com.knowledgeos.tenant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * La tabella "tenant" non e' soggetta a RLS (non e' essa stessa dati di un
 * tenant, ma il registro dei tenant — 03_DATABASE_DESIGN.md §4.1).
 */
@Entity
@Table(name = "tenant")
@Getter
@Setter
@NoArgsConstructor
public class Tenant {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "storage_bucket", nullable = false)
    private String storageBucket;

    @Column(name = "keycloak_realm", nullable = false)
    private String keycloakRealm;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
