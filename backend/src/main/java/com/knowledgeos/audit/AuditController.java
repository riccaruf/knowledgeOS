package com.knowledgeos.audit;

import com.knowledgeos.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 04_API_SPECIFICATION.md §6.3 — audit log, riservato a TENANT_ADMIN
 * (06_SECURITY_MODEL.md §6: sola lettura, append-only).
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepository repository;

    @GetMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public Page<AuditLog> list(Pageable pageable) {
        return repository.findByTenantIdOrderByCreatedAtDesc(TenantContext.getTenantId(), pageable);
    }
}
