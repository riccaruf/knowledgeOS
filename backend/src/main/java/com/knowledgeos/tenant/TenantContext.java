package com.knowledgeos.tenant;

import java.util.Set;
import java.util.UUID;

/**
 * Contesto di sicurezza/tenant della richiesta corrente, valorizzato da
 * {@link TenantContextFilter} dopo la validazione del JWT e propagato
 * per tutta la durata della richiesta sullo stesso (virtual) thread.
 *
 * Letto da {@link TenantAwareDataSource} per impostare la GUC Postgres
 * usata dalle policy di Row Level Security (06_SECURITY_MODEL.md §3.2).
 */
public final class TenantContext {

    public record Data(UUID tenantId, UUID appUserId, String keycloakSubject, String email,
                        String displayName, Set<String> roles) {}

    private static final ThreadLocal<Data> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Data data) {
        CURRENT.set(data);
    }

    public static Data get() {
        return CURRENT.get();
    }

    public static UUID getTenantId() {
        Data data = CURRENT.get();
        return data == null ? null : data.tenantId();
    }

    public static UUID getAppUserId() {
        Data data = CURRENT.get();
        return data == null ? null : data.appUserId();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
