# 04 — API Specification

## 1. Convenzioni generali

- **Stile**: REST su HTTPS, payload JSON.
- **Versionamento**: prefisso di path `/api/v1/...`. Breaking change → nuova versione di path.
- **Autenticazione**: Bearer JWT (OIDC access token emesso da Keycloak) in header `Authorization: Bearer <token>`.
- **Tenant resolution**: il tenant NON viene passato come parametro esplicito dal client; viene derivato lato server dal claim del token (es. `tenant_id` custom claim o mapping realm→tenant). Questo previene manipolazione client-side del tenant.
- **Paginazione**: query param `page` (0-based) e `size`; risposta con envelope `{ content, page, size, totalElements, totalPages }`.
- **Errori**: formato uniforme (RFC 7807 Problem Details):

```json
{
  "type": "https://knowledgeos.dev/errors/validation-error",
  "title": "Validation failed",
  "status": 400,
  "detail": "Il campo 'title' è obbligatorio",
  "instance": "/api/v1/documents",
  "traceId": "a1b2c3d4"
}
```

- **Idempotenza**: operazioni di upload accettano header opzionale `Idempotency-Key` per evitare doppio caricamento in caso di retry client.
- **Content negotiation**: `Content-Type: application/json` salvo endpoint di upload file (`multipart/form-data`).

---

## 2. Autenticazione e sessione

L'autenticazione avviene interamente tramite Keycloak (Authorization Code + PKCE lato frontend). Il backend non espone endpoint di login/password: agisce solo da Resource Server.

| Operazione | Dove avviene |
|---|---|
| Login utente | Redirect frontend → Keycloak `/realms/{realm}/protocol/openid-connect/auth` |
| Ottenimento token | Keycloak `/realms/{realm}/protocol/openid-connect/token` |
| Refresh token | Keycloak token endpoint (gestito da libreria OIDC frontend) |
| Logout | Redirect frontend → Keycloak `/realms/{realm}/protocol/openid-connect/logout` |
| Validazione token | Backend (Spring Security OAuth2 Resource Server, JWK set da Keycloak) |

Endpoint applicativo di supporto:

```
GET /api/v1/me
```
Restituisce il profilo utente corrente risolto dal token (id, email, ruoli, tenant).

Risposta:
```json
{
  "id": "3f2a...",
  "email": "mario.rossi@cliente.it",
  "displayName": "Mario Rossi",
  "tenantId": "8b1c...",
  "roles": ["DOCUMENT_MANAGER", "VIEWER"]
}
```

---

## 3. Documenti

### 3.1 Elenco documenti

```
GET /api/v1/documents?category=&department=&tag=&status=&page=&size=
```

Risposta (estratto):
```json
{
  "content": [
    {
      "id": "d1e2...",
      "title": "Manuale Pompa PX400",
      "category": "Manuale tecnico",
      "department": "Produzione",
      "tags": ["pompa", "manutenzione"],
      "lifecycleStatus": "PUBLISHED",
      "currentVersion": {
        "id": "v9a8...",
        "versionLabel": "v1.2",
        "uploadedAt": "2026-05-10T09:12:00Z"
      }
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 134,
  "totalPages": 7
}
```

Permessi: ruolo `VIEWER` o superiore.

### 3.2 Dettaglio documento

```
GET /api/v1/documents/{documentId}
```
Include storico versioni (`versions[]`).

### 3.3 Upload nuovo documento (nuova entità)

```
POST /api/v1/documents
Content-Type: multipart/form-data
```

Campi form: `file`, `title`, `category`, `department` (opzionale), `tags` (opzionale, CSV o array).

Permessi: `DOCUMENT_MANAGER`.

Risposta `202 Accepted` (l'ingestion è asincrona):
```json
{
  "documentId": "d1e2...",
  "versionId": "v9a8...",
  "ingestionStatus": "PENDING"
}
```

### 3.4 Upload nuova versione di documento esistente

```
POST /api/v1/documents/{documentId}/versions
Content-Type: multipart/form-data
```
Campi form: `file`, `versionLabel` (opzionale, auto-incrementale se omesso).

Permessi: `DOCUMENT_MANAGER`.

### 3.5 Stato ingestion

```
GET /api/v1/documents/{documentId}/versions/{versionId}/ingestion-status
```
```json
{
  "status": "PROCESSING",
  "startedAt": "2026-07-23T10:00:00Z",
  "step": "SEMANTIC_CHUNKING",
  "error": null
}
```
Il frontend effettua polling su questo endpoint (o riceve aggiornamento via WebSocket/SSE — estensione futura) durante l'upload.

### 3.6 Aggiornamento metadata / lifecycle

```
PATCH /api/v1/documents/{documentId}
```
Body: campi modificabili (`title`, `category`, `department`, `tags`, `lifecycleStatus`).

Permessi: `DOCUMENT_MANAGER`.

### 3.7 Eliminazione documento (soft delete)

```
DELETE /api/v1/documents/{documentId}
```
Effettua soft delete: il documento e le sue versioni/chunk vengono marcati come non attivi e rimossi dal retrieval; permangono per audit storico. Permessi: `TENANT_ADMIN`.

---

## 4. Chat / Retrieval

### 4.1 Interrogazione in linguaggio naturale

```
POST /api/v1/query
```

Request:
```json
{
  "question": "Ogni quante ore va sostituito il filtro della pompa PX400?",
  "conversationId": "c771...",
  "filters": {
    "category": ["Manuale tecnico"],
    "department": null
  }
}
```

`conversationId` opzionale — se presente, la domanda viene contestualizzata con lo storico della conversazione (gestito lato backend).

Response `200 OK`:
```json
{
  "answer": "Il filtro della pompa PX400 deve essere sostituito ogni 500 ore di esercizio.",
  "confidence": 0.91,
  "sources": [
    {
      "documentId": "d1e2...",
      "documentTitle": "Manuale_Pompa_PX400.pdf",
      "versionLabel": "v1.2",
      "page": 24,
      "section": "4.3 Manutenzione ordinaria",
      "excerpt": "Il filtro deve essere sostituito ogni 500 ore di funzionamento continuativo...",
      "relevanceScore": 0.87
    }
  ],
  "conversationId": "c771...",
  "queryLogId": "q551..."
}
```

Permessi: `VIEWER` o superiore. Il filtro tenant è sempre applicato server-side, indipendentemente dai `filters` passati dal client. Solo il filtro `category` è implementato nella build MVP (`department` è accettato dal contratto ma non ancora applicato).

**Nota di implementazione**: `conversationId`, se assente, viene generato e restituito nella risposta, ma nella build MVP non abilita ancora una reale contestualizzazione multi-turno (ogni domanda è trattata in modo indipendente) — è un placeholder per l'endpoint §4.2, non ancora implementato.

### 4.2 Storico conversazione (non implementato in questa build)

```
GET /api/v1/conversations/{conversationId}
```
Restituisce lo storico messaggi (domanda/risposta/fonti) per la sessione di chat. Rimandato insieme alla contestualizzazione multi-turno vera e propria (si veda nota sopra); il frontend MVP mantiene lo storico della conversazione solo lato client, per la sessione corrente.

### 4.3 Feedback su risposta (non implementato in questa build)

```
POST /api/v1/query/{queryLogId}/feedback
```
```json
{ "rating": "HELPFUL" }
```
Valori enum: `HELPFUL`, `NOT_HELPFUL`, `INCORRECT`. Rimandato: non necessario per validare il ciclo end-to-end dell'MVP (upload → ingestion → domanda → risposta con fonti).

---

## 5. Knowledge Domain (non implementato in questa build)

Come da `07_MVP_ROADMAP.md`, il Knowledge Domain è schema-ready in questa build (tabelle `knowledge_entity`/`knowledge_relation` presenti, `03_DATABASE_DESIGN.md` §4.6-4.7) ma senza API né UI di gestione: rimandato a Milestone 2.

### 5.1 Entità di dominio

```
GET  /api/v1/knowledge/entities?type=PRODUCT&search=
POST /api/v1/knowledge/entities
GET  /api/v1/knowledge/entities/{id}
PATCH /api/v1/knowledge/entities/{id}
DELETE /api/v1/knowledge/entities/{id}
```

Body creazione:
```json
{
  "entityType": "MACHINE",
  "name": "PX400",
  "description": "Pompa industriale serie 400",
  "attributes": { "produttore": "AcmeCorp" }
}
```

Permessi: `KNOWLEDGE_EDITOR`.

### 5.2 Relazioni tra entità

```
POST /api/v1/knowledge/relations
```
```json
{
  "sourceEntityId": "e1...",
  "targetEntityId": "e2...",
  "relationType": "USES"
}
```

### 5.3 Grafo di dominio (vista aggregata)

```
GET /api/v1/knowledge/graph?rootEntityId=&depth=2
```
Restituisce nodi e archi per visualizzazione (es. grafo prodotto → componenti → procedure).

---

## 6. Amministrazione

### 6.1 Gestione utenti (non implementato in questa build)

```
GET  /api/v1/admin/users
PATCH /api/v1/admin/users/{id}/status     # ACTIVE / DISABLED
```
Permessi: `TENANT_ADMIN`. La creazione/gestione credenziali resta su Keycloak; questi endpoint gestiscono solo stato applicativo e associazione a ruoli applicativi visibili nel prodotto. **Nota di implementazione**: nella build MVP il provisioning dell'utente applicativo (`app_user`) avviene automaticamente al primo login (JIT, si veda `06_SECURITY_MODEL.md`), ma non esiste ancora una API di amministrazione per elencarli/disabilitarli — rimandato a Milestone 2.

### 6.2 Configurazione modelli (non implementato in questa build)

```
GET   /api/v1/admin/config/llm
PATCH /api/v1/admin/config/llm
GET   /api/v1/admin/config/embedding
PATCH /api/v1/admin/config/embedding
```

Body esempio (`PATCH .../llm`):
```json
{ "provider": "ollama", "modelName": "qwen2.5:14b", "parameters": { "temperature": 0.2 } }
```

Permessi: `TENANT_ADMIN`. Nota: il cambio di modello di embedding innesca un processo di re-ingestion massivo (asincrono) — l'endpoint restituisce un job di migrazione tracciabile, non un effetto immediato. **Nota di implementazione**: nella build MVP il modello LLM e il modello di embedding sono configurati staticamente per l'intero deployment tramite variabili d'ambiente/`application.yml` (`OLLAMA_LLM_MODEL`, `OLLAMA_EMBEDDING_MODEL`), non tramite questi endpoint né in modo differenziato per tenant — coerente con la validazione a singolo tenant dell'MVP (`07_MVP_ROADMAP.md`).

### 6.3 Audit log

```
GET /api/v1/admin/audit?eventType=&from=&to=&userId=&page=&size=
```
Permessi: `TENANT_ADMIN`. **Nota di implementazione**: nella build MVP i filtri `eventType`/`from`/`to`/`userId` non sono ancora supportati (solo paginazione e ordinamento per data decrescente).

---

## 7. Agenti specializzati (predisposizione futura — contratto stabile fin da ora)

```
GET   /api/v1/admin/agents
POST  /api/v1/admin/agents
PATCH /api/v1/admin/agents/{id}
POST  /api/v1/agents/{id}/query
```

`POST /api/v1/agents/{id}/query` ha lo stesso contratto request/response di `POST /api/v1/query`, ma il dominio di conoscenza, il prompt di sistema e i permessi sono vincolati dalla configurazione dell'agente (`agent_config`, si veda `03_DATABASE_DESIGN.md`).

---

## 8. Codici di errore applicativi principali

| HTTP Status | Caso |
|---|---|
| 400 | Validazione input fallita |
| 401 | Token assente/non valido/scaduto |
| 403 | Utente autenticato ma privo del ruolo richiesto, o tentativo di accesso cross-tenant |
| 404 | Risorsa non trovata (o non trovata *nel tenant corrente*, mai distinguibile da un cliente esterno se appartiene ad altro tenant) |
| 409 | Conflitto (es. `Idempotency-Key` già usata con payload diverso) |
| 422 | Documento non processabile (formato non supportato, file corrotto) |
| 429 | Rate limit (protezione su endpoint `/query` per tenant) |
| 500 | Errore interno |
| 503 | Componente downstream non disponibile (es. Ollama non raggiungibile) |

---

## Stato del documento

Bozza per revisione — Fase 1/2 del processo di sviluppo. In attesa di approvazione prima di procedere a `05_RAG_PIPELINE.md`.
