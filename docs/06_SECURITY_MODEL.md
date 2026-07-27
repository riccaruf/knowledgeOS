# 06 — Security Model

## 1. Principi

1. **Privacy come requisito commerciale primario**, non solo tecnico (si veda `01_PRODUCT_VISION.md`): nessun documento, embedding, log di conversazione o metadato lascia l'infrastruttura del cliente verso servizi esterni.
2. **Difesa in profondità sul multi-tenancy**: l'isolamento tenant non dipende da un solo meccanismo, ma da più livelli indipendenti (identità, applicazione, database, storage).
3. **Least privilege / RBAC esplicito**: ogni operazione richiede il ruolo minimo necessario; nessun endpoint "aperto" senza controllo di ruolo.
4. **Tracciabilità totale**: ogni accesso a dati e ogni risposta generata è auditabile (chi, cosa, quando, su quali fonti).
5. **Nessuna credenziale gestita in proprio dal backend applicativo**: l'identità è delegata interamente a Keycloak (IdP dedicato), evitando di reinventare gestione password/MFA/policy di sicurezza.

---

## 2. Authentication

### 2.1 Identity Provider — Keycloak

- Protocollo: **OpenID Connect** (OAuth2 Authorization Code Flow + PKCE) tra frontend Angular e Keycloak.
- Il backend Spring Boot agisce esclusivamente come **OAuth2 Resource Server**: valida i JWT tramite JWK set esposto da Keycloak (`/realms/{realm}/protocol/openid-connect/certs`), non gestisce mai password.
- Token: access token JWT a vita breve (es. 5–15 minuti), refresh token gestito dalla libreria OIDC del frontend (silent refresh).
- MFA, policy password, account lockout, gestione utenti: delegati interamente alle funzionalità native di Keycloak.

### 2.2 Modellazione tenant in Keycloak

Due opzioni architetturali, selezionabili per deployment in base alla scala del cliente:

| Opzione | Descrizione | Quando usarla |
|---|---|---|
| **Realm dedicato per tenant** | Isolamento massimo: ogni tenant ha un realm Keycloak separato, con propri utenti/ruoli/client | Cliente enterprise singolo con deployment on-premise dedicato (caso primario del prodotto) |
| **Realm condiviso, gruppi per tenant** | Un realm unico, tenant distinti tramite gruppi Keycloak con claim `tenant_id` mappato | Scenario SaaS multi-tenant futuro con molti tenant di piccole dimensioni sulla stessa installazione |

Per l'MVP (deployment on-premise dedicato per singolo cliente), si adotta **realm dedicato per tenant** come default, poiché massimizza l'isolamento con complessità operativa accettabile a quella scala.

### 2.3 Claim del token rilevanti

```json
{
  "sub": "3f2a...",
  "email": "mario.rossi@cliente.it",
  "realm_access": { "roles": ["document_manager", "viewer"] },
  "tenant_id": "8b1c...",
  "exp": 1753500000
}
```

Il backend estrae `tenant_id` e ruoli esclusivamente dal token verificato — mai da parametri o header forniti liberamente dal client.

---

## 3. Authorization / RBAC

### 3.1 Ruoli applicativi standard

| Ruolo | Descrizione | Permessi principali |
|---|---|---|
| `TENANT_ADMIN` | Amministratore del tenant | Gestione utenti/ruoli applicativi, configurazione modelli LLM/embedding, audit log, eliminazione documenti |
| `DOCUMENT_MANAGER` | Gestione ciclo di vita documentale | Upload, versioning, modifica metadata, archiviazione documenti |
| `KNOWLEDGE_EDITOR` | Gestione dominio di conoscenza | CRUD su entità/relazioni del knowledge domain |
| `VIEWER` | Utente finale standard | Ricerca, chat/query, visualizzazione fonti |

I ruoli sono cumulabili (un utente può avere più ruoli). Ruoli aggiuntivi specifici di dominio (es. accesso a un `agent_config` riservato a un reparto) sono modellati come vincolo aggiuntivo su `agent_config.required_role`, non come nuovi ruoli globali per ogni caso d'uso.

### 3.2 Enforcement

- **Livello API**: annotazioni Spring Security (`@PreAuthorize`) su ogni controller/metodo, basate sui ruoli estratti dal token.
- **Livello servizio**: ogni query verso il database passa sempre attraverso un livello che inietta il filtro `tenant_id` derivato dal contesto di sicurezza corrente — mai da parametro esplicito lato client (si veda `04_API_SPECIFICATION.md` §1).
- **Livello database (seconda linea di difesa)**: Row Level Security (RLS) PostgreSQL sulle tabelle multi-tenant, con policy basata su un parametro di sessione (`SET app.current_tenant_id = '...'`) impostato dal backend a inizio transazione. In caso di bug applicativo che ometta il filtro esplicito, RLS previene comunque l'accesso cross-tenant.

Esempio di policy RLS (implementato in `V1__init_schema.sql`):
```sql
ALTER TABLE chunk ENABLE ROW LEVEL SECURITY;
ALTER TABLE chunk FORCE ROW LEVEL SECURITY; -- si applica anche al proprietario della tabella

CREATE POLICY tenant_isolation_chunk ON chunk
  USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);
```

**Nota di implementazione**: nel deployment MVP il ruolo applicativo Postgres è anche proprietario delle tabelle (esegue sia le migrazioni sia le query a runtime). Senza `FORCE ROW LEVEL SECURITY`, Postgres esenta di default il proprietario dalle proprie policy RLS, rendendo la protezione puramente illustrativa. `FORCE` chiude questo gap. Lato applicativo, `TenantAwareDataSource` (un `DataSource` decorator) imposta la GUC `app.current_tenant_id` ad ogni prelievo di connessione dal pool in base al `TenantContext` della richiesta corrente, cosi' che ogni query — ORM o SQL diretto — sia automaticamente vincolata al tenant giusto senza bisogno di propagarlo manualmente in ogni repository.

### 3.3 Superadmin di piattaforma (contesto SaaS futuro)

Ruolo distinto, fuori dal RBAC di tenant, riservato al gestore della piattaforma (nel caso di deployment SaaS multi-cliente futuro) per operazioni di provisioning/dismissione tenant — non ha accesso diretto ai contenuti documentali dei tenant, solo a metadati di gestione (stato tenant, configurazione infrastrutturale).

---

## 4. Tenant Isolation — riepilogo multi-livello

| Livello | Meccanismo |
|---|---|
| Identità | Realm Keycloak dedicato (o gruppo isolato) per tenant |
| Applicazione | Filtro `tenant_id` obbligatorio iniettato dal Tenant Context Resolver su ogni richiesta |
| Database | Row Level Security su tutte le tabelle con `tenant_id` |
| Storage oggetti | Bucket MinIO dedicato per tenant |
| Modelli AI | Configurazione LLM/embedding attiva per tenant (nessuna condivisione implicita di configurazione) |
| Audit | Log sempre filtrato per tenant; nessuna vista cross-tenant salvo ruolo superadmin di piattaforma |

---

## 5. Data protection e privacy

- **Nessuna chiamata di rete esterna** da parte dei componenti che processano contenuto documentale (parsing, OCR, chunking, embedding, LLM): tutti i modelli girano on-premise (Ollama e servizi di embedding locali).
- **Cifratura in transito**: TLS su tutte le comunicazioni esterne (frontend↔backend, eventuali accessi amministrativi); comunicazioni interne tra container su rete Docker isolata.
- **Cifratura a riposo**: delegata al livello infrastrutturale (dischi cifrati, configurazione MinIO/PostgreSQL secondo policy IT del cliente) — non reinventata a livello applicativo.
- **Segregazione ambienti**: nessun dato di produzione utilizzato in ambienti di test/sviluppo senza anonimizzazione, in particolare per clienti con documentazione contrattuale sensibile (capitolati con dati di terzi).
- **Nessun training/fine-tuning su dati cliente**: coerente con il principio architetturale "no fine-tuning" (`02_SOLUTION_ARCHITECTURE.md`) — elimina strutturalmente il rischio di leakage di dati aziendali nei pesi di un modello condiviso.
- **Diritto all'oblio applicativo**: l'eliminazione di un documento (soft delete, `04_API_SPECIFICATION.md` §3.7) rimuove il documento e i suoi chunk dal retrieval attivo; una procedura di hard delete definitiva (rimozione fisica da MinIO/PostgreSQL) è prevista come operazione amministrativa separata per conformità a richieste di cancellazione vincolanti.

---

## 6. Audit

- Ogni evento rilevante (upload documento, modifica metadata, eliminazione, esecuzione query, modifica ruoli/configurazione) viene registrato in `audit_log` (`03_DATABASE_DESIGN.md` §4.11), con attore, tenant, timestamp e dettaglio payload.
- Le interazioni RAG (domanda, risposta, fonti effettivamente usate, modello LLM impiegato) sono tracciate separatamente in `query_log` (`03_DATABASE_DESIGN.md` §4.12), a supporto sia di verifiche di qualità sia di audit di conformità (es. dimostrare quali documenti hanno fondato una risposta usata in un contesto regolamentato/ISO).
- Gli audit log non sono modificabili da API applicative standard (append-only); l'accesso in lettura è riservato a `TENANT_ADMIN`.
- Retention configurabile per tenant, in base a policy interne/contrattuali del cliente (non hardcoded).

---

## 7. Superfici di attacco principali e mitigazioni

| Rischio | Mitigazione |
|---|---|
| Accesso cross-tenant per bug applicativo | RLS PostgreSQL come seconda linea di difesa indipendente dal codice applicativo |
| Escalation di privilegi via token manomesso | Validazione firma/issuer JWT lato Resource Server, chiavi pubbliche da JWK set Keycloak, nessuna fiducia in claim non firmati |
| Esfiltrazione dati verso servizi cloud | Nessuna dipendenza di rete esterna nella pipeline RAG; policy di rete a livello infrastrutturale che può bloccare traffico in uscita non necessario |
| Prompt injection tramite contenuto documentale malevolo | Vincolo esplicito nel prompt di sistema (l'LLM risponde solo sulla base del contesto, non esegue istruzioni contenute nei documenti); i chunk sono trattati come dati, non come istruzioni, nella costruzione del prompt |
| Upload di file malevoli (malware in allegati) | Scansione antivirus/validazione MIME type reale (non solo estensione) in fase di upload, prima dell'ingestion |
| Injection SQL/NoQL | Uso esclusivo di query parametrizzate/ORM (JPA/Hibernate) — mai concatenazione di stringhe SQL, incluso nel filtro `tenant_id` |
| Denial of service su endpoint `/query` (costoso, invoca LLM) | Rate limiting per tenant/utente a livello API gateway/backend |

---

## Stato del documento

Bozza per revisione — Fase 1/2 del processo di sviluppo. In attesa di approvazione prima di procedere a `07_MVP_ROADMAP.md`.
