package com.knowledgeos.security;

import com.knowledgeos.tenant.TenantContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

/**
 * 04_API_SPECIFICATION.md §2 — profilo utente corrente risolto dal token.
 */
@RestController
public class MeController {

    public record MeResponse(UUID id, String email, String displayName, UUID tenantId, Set<String> roles) {}

    @GetMapping("/api/v1/me")
    public MeResponse me() {
        TenantContext.Data data = TenantContext.get();
        return new MeResponse(data.appUserId(), data.email(), data.displayName(), data.tenantId(), data.roles());
    }
}
