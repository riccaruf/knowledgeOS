package com.knowledgeos.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Provisioning "just-in-time" degli utenti applicativi: Keycloak resta l'unica
 * fonte di verita' per identita'/credenziali (06_SECURITY_MODEL.md §2.1); questa
 * tabella e' una proiezione locale creata al primo accesso di un utente già
 * autenticato, usata per riferimenti FK (autore upload, audit) — non gestisce
 * mai password o policy di accesso.
 */
@Service
@RequiredArgsConstructor
public class AppUserService {

    private final AppUserRepository repository;

    @Transactional
    public AppUser findOrProvision(UUID tenantId, String keycloakSubject, String email, String displayName) {
        return repository.findByTenantIdAndKeycloakSubject(tenantId, keycloakSubject)
                .orElseGet(() -> {
                    AppUser user = new AppUser();
                    user.setTenantId(tenantId);
                    user.setKeycloakSubject(keycloakSubject);
                    user.setEmail(email);
                    user.setDisplayName(displayName);
                    return repository.save(user);
                });
    }
}
