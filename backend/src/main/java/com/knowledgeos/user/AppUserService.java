package com.knowledgeos.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

/**
 * Provisioning "just-in-time" degli utenti applicativi: Keycloak resta l'unica
 * fonte di verita' per identita'/credenziali (06_SECURITY_MODEL.md §2.1); questa
 * tabella e' una proiezione locale creata al primo accesso di un utente già
 * autenticato, usata per riferimenti FK (autore upload, audit) — non gestisce
 * mai password o policy di accesso.
 */
@Service
public class AppUserService {

    private final AppUserRepository repository;
    private final TransactionTemplate requiresNewTransactionTemplate;

    public AppUserService(AppUserRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
    }

    @Transactional
    public AppUser findOrProvision(UUID tenantId, String keycloakSubject, String email, String displayName) {
        return repository.findByTenantIdAndKeycloakSubject(tenantId, keycloakSubject)
                .orElseGet(() -> provisionOrRecoverFromConcurrentInsert(tenantId, keycloakSubject, email, displayName));
    }

    // Il primo accesso di un utente puo' arrivare come piu' richieste HTTP concorrenti
    // (frontend che chiama piu' endpoint in parallelo dopo il login): due virtual thread
    // possono superare entrambi il controllo "non esiste ancora" prima che uno dei due
    // faccia il commit. L'insert avviene in una transazione dedicata cosi' un fallimento
    // per vincolo unico non invalida la transazione (READ COMMITTED) del chiamante, che
    // puo' quindi rileggere la riga inserita dal thread vincitore della race.
    private AppUser provisionOrRecoverFromConcurrentInsert(UUID tenantId, String keycloakSubject, String email, String displayName) {
        try {
            return requiresNewTransactionTemplate.execute(status -> {
                AppUser user = new AppUser();
                user.setTenantId(tenantId);
                user.setKeycloakSubject(keycloakSubject);
                user.setEmail(email);
                user.setDisplayName(displayName);
                return repository.save(user);
            });
        } catch (DataIntegrityViolationException raceLost) {
            return repository.findByTenantIdAndKeycloakSubject(tenantId, keycloakSubject)
                    .orElseThrow(() -> raceLost);
        }
    }
}
