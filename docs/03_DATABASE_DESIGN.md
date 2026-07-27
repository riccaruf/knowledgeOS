# 03 — Database Design

## 1. Principi di modellazione

1. **Multi-tenancy a livello di riga**: ogni tabella che contiene dati appartenenti a un tenant ha una colonna `tenant_id`, con filtro obbligatorio applicato a livello applicativo (e, dove supportato, rinforzato con Row Level Security di PostgreSQL) — dettaglio in `06_SECURITY_MODEL.md`.
2. **Un solo database, schema condiviso, isolamento logico**: nella prima versione si adotta lo schema *shared database, shared schema, tenant_id discriminator* — più semplice da operare on-premise rispetto a database-per-tenant, comunque compatibile con un'evoluzione verso schema/database dedicato per tenant di grandi dimensioni.
3. **Vettori e metadati nello stesso sistema transazionale**: pgvector permette di associare embedding e metadati relazionali senza sincronizzare due datastore distinti (si veda `02_SOLUTION_ARCHITECTURE.md`).
4. **Versionamento esplicito dei documenti**: nessun update distruttivo sui contenuti; ogni nuova versione crea nuove righe di versione/chunk, preservando lo storico e la tracciabilità delle risposte passate.
5. **Soft delete**: le entità principali (documenti, chunk, utenti applicativi) non vengono mai cancellate fisicamente in modo immediato, ma marcate (`status`/`deleted_at`) per preservare audit e integrità referenziale storica.

---

## 2. Estensioni PostgreSQL richieste

```sql
CREATE EXTENSION IF NOT EXISTS vector;      -- pgvector: tipo colonna VECTOR, indici ANN
CREATE EXTENSION IF NOT EXISTS pg_trgm;     -- ricerca keyword/fuzzy full-text complementare
```

---

## 3. Entity Model — panoramica

```
Tenant 1───* User
Tenant 1───* Document 1───* DocumentVersion 1───* Chunk
Tenant 1───* KnowledgeEntity 1───* KnowledgeRelation
Tenant 1───* AuditLogEntry
Tenant 1───* Agent (config futura)

Document        : entità logica del documento (indipendente da versione)
DocumentVersion : una versione concreta caricata/pubblicata del documento
Chunk            : unità semantica estratta da una DocumentVersion, con embedding
KnowledgeEntity  : nodo del dominio di conoscenza (prodotto, macchina, procedura, ecc.)
KnowledgeRelation: arco tra due KnowledgeEntity (es. "usa", "richiede", "è una")
```

---

## 4. Tabelle principali

### 4.1 `tenant`

| Colonna | Tipo | Note |
|---|---|---|
| id | UUID PK | |
| name | VARCHAR | Ragione sociale / nome cliente |
| slug | VARCHAR UNIQUE | Identificativo tecnico (subdomain, realm Keycloak) |
| status | VARCHAR | `ACTIVE`, `SUSPENDED`, `PROVISIONING` |
| storage_bucket | VARCHAR | Nome bucket MinIO dedicato |
| keycloak_realm | VARCHAR | Realm Keycloak associato |
| created_at | TIMESTAMPTZ | |
| updated_at | TIMESTAMPTZ | |

### 4.2 `app_user`

| Colonna | Tipo | Note |
|---|---|---|
| id | UUID PK | |
| tenant_id | UUID FK → tenant | |
| keycloak_subject | VARCHAR UNIQUE | `sub` claim del token OIDC |
| email | VARCHAR | |
| display_name | VARCHAR | |
| status | VARCHAR | `ACTIVE`, `DISABLED` |
| created_at | TIMESTAMPTZ | |

Nota: i ruoli/permessi non sono duplicati qui in modo estensivo — Keycloak resta source of truth per ruoli/gruppi; questa tabella è una proiezione locale utile per audit e riferimenti FK (es. autore di un documento).

### 4.3 `document`

Entità logica stabile del documento (sopravvive a più versioni).

| Colonna | Tipo | Note |
|---|---|---|
| id | UUID PK | |
| tenant_id | UUID FK → tenant | |
| title | VARCHAR | |
| category | VARCHAR | es. "Manuale tecnico", "Procedura ISO", "Capitolato" |
| department | VARCHAR | Reparto di riferimento (opzionale) |
| tags | TEXT[] | |
| current_version_id | UUID FK → document_version (nullable) | Puntatore alla versione corrente pubblicata |
| lifecycle_status | VARCHAR | `DRAFT`, `PUBLISHED`, `ARCHIVED`, `DEPRECATED` |
| created_by | UUID FK → app_user | |
| created_at | TIMESTAMPTZ | |
| updated_at | TIMESTAMPTZ | |
| deleted_at | TIMESTAMPTZ NULL | Soft delete |

### 4.4 `document_version`

Ogni caricamento/revisione crea una nuova riga.

| Colonna | Tipo | Note |
|---|---|---|
| id | UUID PK | |
| tenant_id | UUID FK → tenant | |
| document_id | UUID FK → document | |
| version_label | VARCHAR | es. "v1.2", "Rev. C" |
| file_object_key | VARCHAR | Chiave oggetto su MinIO |
| file_mime_type | VARCHAR | |
| file_size_bytes | BIGINT | |
| checksum_sha256 | VARCHAR | Integrità/deduplica |
| author | VARCHAR | Autore originale del documento (metadato di dominio, non necessariamente `app_user`) |
| ingestion_status | VARCHAR | `PENDING`, `PROCESSING`, `PROCESSED`, `FAILED` |
| ingestion_error | TEXT NULL | |
| uploaded_by | UUID FK → app_user | |
| uploaded_at | TIMESTAMPTZ | |

### 4.5 `chunk`

Unità atomica di conoscenza, con embedding. Corrisponde all'oggetto descritto in `05_RAG_PIPELINE.md`.

| Colonna | Tipo | Note |
|---|---|---|
| id | UUID PK | |
| tenant_id | UUID FK → tenant | Duplicata qui deliberatamente per filtro diretto senza join, requisito di isolamento |
| document_id | UUID FK → document | |
| document_version_id | UUID FK → document_version | |
| title | VARCHAR | Titolo documento/sezione (denormalizzato per retrieval veloce) |
| section | VARCHAR | Capitolo/sezione |
| page_number | INT NULL | |
| content | TEXT | Testo del chunk |
| content_tsv | TSVECTOR | Colonna generata per full-text search (keyword) |
| metadata | JSONB | Metadata estesi non modellati a colonna (autore, categoria, tag, timestamp originale documento, relazioni) |
| embedding | VECTOR(1024) | Dimensione dipendente dal modello di embedding configurato per il tenant (si veda 4.8) |
| embedding_model | VARCHAR | Nome/versione del modello usato — necessario per rigenerare/gestire migrazioni tra modelli |
| chunk_index | INT | Ordine del chunk all'interno del documento/sezione |
| created_at | TIMESTAMPTZ | |

Indici chiave:

```sql
CREATE INDEX idx_chunk_tenant ON chunk (tenant_id);
CREATE INDEX idx_chunk_document_version ON chunk (document_version_id);
CREATE INDEX idx_chunk_embedding_hnsw ON chunk USING hnsw (embedding vector_cosine_ops);
CREATE INDEX idx_chunk_content_tsv ON chunk USING GIN (content_tsv);
CREATE INDEX idx_chunk_metadata_gin ON chunk USING GIN (metadata jsonb_path_ops);
```

`content_tsv` è mantenuta con una colonna generata o trigger:

```sql
ALTER TABLE chunk ADD COLUMN content_tsv tsvector
  GENERATED ALWAYS AS (to_tsvector('italian', content)) STORED;
```

### 4.6 `knowledge_entity`

Nodo del dominio di conoscenza aziendale (vedi vision: prodotti, macchine, reparti, procedure, regole).

| Colonna | Tipo | Note |
|---|---|---|
| id | UUID PK | |
| tenant_id | UUID FK → tenant | |
| entity_type | VARCHAR | `PRODUCT`, `MACHINE`, `PROCEDURE`, `DEPARTMENT`, `GLOSSARY_TERM`, `ACRONYM`, `RULE` |
| name | VARCHAR | |
| description | TEXT | |
| attributes | JSONB | Attributi specifici per tipo |
| source_document_id | UUID FK → document NULL | Documento da cui l'entità è stata estratta/dichiarata (se applicabile) |
| created_at | TIMESTAMPTZ | |
| updated_at | TIMESTAMPTZ | |

### 4.7 `knowledge_relation`

Arco tra due entità del dominio (es. `PX400` --usa--> `Filtro F32`).

| Colonna | Tipo | Note |
|---|---|---|
| id | UUID PK | |
| tenant_id | UUID FK → tenant | |
| source_entity_id | UUID FK → knowledge_entity | |
| target_entity_id | UUID FK → knowledge_entity | |
| relation_type | VARCHAR | es. `IS_A`, `USES`, `REQUIRES`, `PART_OF` |
| attributes | JSONB | |
| created_at | TIMESTAMPTZ | |

### 4.8 `embedding_model_config`

Configurazione, per tenant, del modello di embedding attivo — separata dalla configurazione dell'LLM (principio di separazione LLM/embedding).

| Colonna | Tipo | Note |
|---|---|---|
| id | UUID PK | |
| tenant_id | UUID FK → tenant | |
| model_name | VARCHAR | es. `bge-large-en`, `nomic-embed-text` |
| vector_dimension | INT | Deve corrispondere alla dimensione della colonna `embedding` usata |
| is_active | BOOLEAN | Solo un modello attivo per tenant alla volta |
| activated_at | TIMESTAMPTZ | |

Nota architetturale: un cambio di modello di embedding richiede la rigenerazione di tutti gli embedding esistenti (batch di re-ingestion sui chunk esistenti), non un semplice switch di configurazione — tracciato tramite questa tabella e lo stato `embedding_model` su ogni chunk.

### 4.9 `llm_model_config`

| Colonna | Tipo | Note |
|---|---|---|
| id | UUID PK | |
| tenant_id | UUID FK → tenant | |
| provider | VARCHAR | `ollama` (default), estendibile |
| model_name | VARCHAR | es. `qwen2.5:14b`, `llama3.1:8b` |
| is_active | BOOLEAN | |
| parameters | JSONB | temperature, top_p, ecc. |

### 4.9bis `ingestion_job` (introdotta in implementazione, MVP)

Non presente nella progettazione originale di questo documento; aggiunta durante l'implementazione del worker di ingestion Java (`02_SOLUTION_ARCHITECTURE.md` §3.2/3.5) per tracciare stato e step di elaborazione a un livello di granularità maggiore di quanto offra da solo `document_version.ingestion_status`.

| Colonna | Tipo | Note |
|---|---|---|
| id | UUID PK | |
| tenant_id | UUID FK → tenant | |
| document_version_id | UUID FK → document_version | |
| status | VARCHAR | `QUEUED`, `RUNNING`, `DONE`, `FAILED` |
| step | VARCHAR | es. `DOWNLOAD`, `PARSING`, `CHUNKING`, `EMBEDDING`, `DONE` |
| error | TEXT | |
| attempts | INT | |
| created_at / started_at / finished_at | TIMESTAMPTZ | |

### 4.10 `agent_config` (predisposizione futura, si veda vision)

| Colonna | Tipo | Note |
|---|---|---|
| id | UUID PK | |
| tenant_id | UUID FK → tenant | |
| name | VARCHAR | es. "Production Agent" |
| system_prompt | TEXT | |
| knowledge_domain_filter | JSONB | Filtri su categoria/reparto/entità di dominio applicabili |
| allowed_tools | JSONB | |
| required_role | VARCHAR | Ruolo minimo per l'uso dell'agente |
| is_active | BOOLEAN | |

### 4.11 `audit_log`

| Colonna | Tipo | Note |
|---|---|---|
| id | UUID PK | |
| tenant_id | UUID FK → tenant | |
| user_id | UUID FK → app_user NULL | Nullable per eventi di sistema |
| event_type | VARCHAR | es. `DOCUMENT_UPLOADED`, `QUERY_EXECUTED`, `DOCUMENT_DELETED`, `USER_ROLE_CHANGED` |
| entity_type | VARCHAR | |
| entity_id | UUID NULL | |
| detail | JSONB | Payload evento (es. domanda posta, documenti citati in risposta) |
| ip_address | INET NULL | |
| created_at | TIMESTAMPTZ | |

Indice: `CREATE INDEX idx_audit_tenant_created ON audit_log (tenant_id, created_at DESC);`

### 4.12 `query_log` (dettaglio interazioni RAG, distinto da audit generico)

| Colonna | Tipo | Note |
|---|---|---|
| id | UUID PK | |
| tenant_id | UUID FK → tenant | |
| user_id | UUID FK → app_user | |
| question | TEXT | |
| answer | TEXT | |
| confidence_score | NUMERIC(4,3) | |
| retrieved_chunk_ids | UUID[] | Chunk effettivamente usati nel contesto |
| llm_model_config_id | UUID FK → llm_model_config | Tracciabilità di quale modello ha generato la risposta |
| latency_ms | INT | |
| created_at | TIMESTAMPTZ | |

---

## 5. Modello multi-tenancy

- **Isolamento dati**: ogni tabella con dati di dominio porta `tenant_id`; tutte le query applicative sono filtrate obbligatoriamente per tenant (enforcement a livello di repository/service layer in Spring, rinforzato da Row Level Security PostgreSQL come seconda linea di difesa — dettaglio in `06_SECURITY_MODEL.md`).
- **Isolamento storage**: bucket MinIO dedicato per tenant (`tenant.storage_bucket`).
- **Isolamento identità**: realm Keycloak dedicato per tenant (`tenant.keycloak_realm`), oppure gruppo isolato in realm condiviso per tenant più piccoli (decisione per singolo deployment, si veda `06_SECURITY_MODEL.md`).
- **Isolamento configurazione**: modelli LLM/embedding attivabili per tenant (`llm_model_config`, `embedding_model_config`), non globali.
- **Isolamento audit**: `audit_log` e `query_log` sempre filtrati per tenant; nessuna vista cross-tenant salvo ruolo di super-amministrazione piattaforma (fuori standard RBAC del tenant).

**Nota di implementazione (RLS con `FORCE`)**: nel deployment MVP il ruolo applicativo Postgres (`knowledgeos`) è anche il proprietario delle tabelle, perché esegue sia le migrazioni Flyway sia le query a runtime. Postgres non applica di default le policy RLS al proprietario di una tabella: senza la clausola `FORCE ROW LEVEL SECURITY`, RLS sarebbe puramente illustrativa per l'app stessa. Tutte le tabelle tenant-scoped sono quindi create con `ENABLE ROW LEVEL SECURITY` **e** `FORCE ROW LEVEL SECURITY` (`V1__init_schema.sql`), così che la policy si applichi anche alle connessioni del ruolo applicativo. Il worker di ingestion e gli script Flyway che devono scrivere righe seed impostano esplicitamente `app.current_tenant_id` prima di operare su queste tabelle.

## 6. Modello metadata del chunk

Il chunk (si veda anche `05_RAG_PIPELINE.md`) porta con sé sia colonne strutturate (per filtro/query efficiente: `document_id`, `section`, `page_number`) sia un campo `metadata JSONB` per attributi variabili (autore, timestamp originale, tag liberi, relazioni verso `knowledge_entity`), evitando di dover alterare lo schema a ogni nuova esigenza di metadatazione mantenendo comunque interrogabilità tramite indice GIN.

## 7. Vector storage design

- Tipo colonna: `VECTOR(n)` di pgvector, con `n` pari alla dimensione del modello di embedding attivo per il tenant (`embedding_model_config.vector_dimension`).
- Indice: HNSW (`vector_cosine_ops`) per bilanciare qualità/velocità di ricerca approssimata; IVFFlat considerato come alternativa più leggera in scrittura ma meno accurato — HNSW preferito per la priorità data alla qualità delle risposte.
- Distanza: coseno, standard per embedding testuali normalizzati.
- Multi-modello: se un tenant cambia modello di embedding, i chunk esistenti mantengono il vettore nel vecchio spazio finché non rigenerati; `chunk.embedding_model` permette di distinguere/filtrare vettori generati con modelli diversi ed evitare di confrontare spazi vettoriali incompatibili nella stessa query.

## 8. Diagramma relazionale sintetico

```
tenant ──< app_user
tenant ──< document ──< document_version ──< chunk
tenant ──< knowledge_entity ──< knowledge_relation >── knowledge_entity
tenant ──< embedding_model_config
tenant ──< llm_model_config
tenant ──< agent_config
tenant ──< audit_log
tenant ──< query_log >── chunk (via retrieved_chunk_ids, relazione logica non FK rigida)
```

---

## Stato del documento

Bozza per revisione — Fase 1/2 del processo di sviluppo. In attesa di approvazione prima di procedere a `04_API_SPECIFICATION.md`.
