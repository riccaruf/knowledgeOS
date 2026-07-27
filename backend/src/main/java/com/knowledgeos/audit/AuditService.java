package com.knowledgeos.audit;

import com.knowledgeos.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Scrive gli eventi rilevanti in audit_log (06_SECURITY_MODEL.md §6).
 * Richiede un TenantContext gia' popolato (chiamato sempre dal contesto di
 * una richiesta autenticata, mai da codice di sistema anonimo).
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;

    @Transactional
    public void record(String eventType, String entityType, UUID entityId, Map<String, Object> detail) {
        TenantContext.Data context = TenantContext.get();
        if (context == null) {
            return;
        }
        AuditLog log = new AuditLog();
        log.setTenantId(context.tenantId());
        log.setUserId(context.appUserId());
        log.setEventType(eventType);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetail(detail == null ? Map.of() : detail);
        repository.save(log);
    }
}
