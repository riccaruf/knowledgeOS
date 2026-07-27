# 02 — Solution Architecture

## 1. Principi architetturali

Questi principi vincolano ogni scelta successiva (database, API, sicurezza):

1. **Separazione LLM / Conoscenza**: il modello linguistico è generico, stateless rispetto al dominio aziendale, e sostituibile. La conoscenza vive esclusivamente nella pipeline RAG (documenti → chunk → embedding → vector store).
2. **On-premise first**: ogni componente deve poter girare interamente nell'infrastruttura del cliente, senza chiamate di rete verso servizi esterni. Le integrazioni cloud (se mai introdotte) devono essere opzionali e disattivabili.
3. **Multi-tenancy by design**: anche nel deployment on-premise a tenant singolo, il modello dati e i servizi sono scritti come se il sistema fosse sempre multi-tenant. Questo evita una riscrittura quando il prodotto evolverà verso SaaS.
4. **Modularità sostituibile**: ogni componente della pipeline (parsing, chunking, embedding, LLM, reranking) è un servizio o un'interfaccia sostituibile indipendentemente dagli altri.
5. **Tracciabilità end-to-end**: ogni risposta deve poter essere ricondotta a un chunk, un documento, una versione, una pagina. Nessuna risposta "orfana" di fonte.
6. **Niente complessità non giustificata**: nella prima versione si evita di introdurre componenti (es. vector DB dedicati esterni) quando un'alternativa più semplice (pgvector) copre i requisiti.

---

## 2. Architettura logica (vista a componenti)

```
┌──────────────────────────────────────────────────────────────────────┐
│                              FRONTEND (Angular)                       │
│   Login · Dashboard · Upload · Knowledge Mgmt · Chat · Fonti · Admin  │
└───────────────────────────────┬──────────────────────────────────────┘
                                 │ HTTPS / REST (JWT via Keycloak)
┌───────────────────────────────▼──────────────────────────────────────┐
│                        BACKEND API (Spring Boot 3 / Java 21)          │
│                                                                        │
│  ┌───────────────┐  ┌───────────────┐  ┌────────────────────────┐    │
│  │ Auth Gateway   │  │ Tenant Context │  │ API Layer (REST)       │   │
│  │ (Keycloak      │  │ Resolver       │  │ Controllers/DTO        │   │
│  │  integration)  │  │                │  │                        │   │
│  └───────────────┘  └───────────────┘  └────────────────────────┘    │
│                                                                        │
│  ┌────────────────────┐ ┌────────────────────┐ ┌──────────────────┐  │
│  │ Document Service    │ │ Ingestion           │ │ Knowledge Domain │  │
│  │ (upload, metadata,  │ │ Orchestrator        │ │ Service          │  │
│  │  versioning,        │ │ (async pipeline     │ │ (glossario,      │  │
│  │  lifecycle)         │ │  trigger/tracking)  │ │  entità, regole) │  │
│  └────────────────────┘ └────────────────────┘ └──────────────────┘  │
│                                                                        │
│  ┌────────────────────┐ ┌────────────────────┐ ┌──────────────────┐  │
│  │ Retrieval Service   │ │ Answer Generation   │ │ Audit &          │  │
│  │ (hybrid search,     │ │ Service (context     │ │ Admin Service    │  │
│  │  reranking)         │ │  build + LLM call)   │ │                  │  │
│  └────────────────────┘ └────────────────────┘ └──────────────────┘  │
└───────┬───────────────────┬───────────────────┬────────────┬─────────┘
        │                   │                   │            │
┌───────▼──────┐   ┌────────▼────────┐  ┌───────▼──────┐ ┌───▼─────────┐
│  MinIO        │   │ Ingestion       │  │ Ollama       │ │ Keycloak    │
│  (Object      │   │ Pipeline        │  │ (LLM +       │ │ (IdP,       │
│  Storage:     │   │ Workers         │  │  Embedding   │ │  RBAC,      │
│  file grezzi) │   │ (async, queue-  │  │  runtime)    │ │  tenant     │
│               │   │  based)         │  │              │ │  realms)    │
└───────────────┘   └────────┬────────┘  └──────────────┘ └─────────────┘
                              │
                    ┌─────────▼──────────────────────────┐
                    │ PostgreSQL + pgvector               │
                    │ (metadata relazionale + embedding   │
                    │  + knowledge domain + audit log)    │
                    └──────────────────────────────────────┘
```

---

## 3. Componenti e responsabilità

### 3.1 Frontend — Angular

Responsabilità:
- Autenticazione (redirect/callback OIDC verso Keycloak, gestione token).
- Dashboard di stato knowledge base (documenti, stato ingestion, statistiche).
- Upload documenti con tracking asincrono dello stato di elaborazione.
- Gestione knowledge base: ricerca, filtri, versioning, tag, categorie.
- Interfaccia di chat con visualizzazione delle fonti (documento, pagina, estratto, confidenza).
- Amministrazione: utenti, ruoli, tenant (per ruoli con permessi adeguati), configurazione dominio di conoscenza.

Non contiene business logic: ogni decisione (permessi, validazioni sostanziali) è delegata al backend.

### 3.2 Backend API — Spring Boot 3 / Java 21

Perché Spring Boot 3 / Java 21: ecosistema enterprise maturo, supporto nativo a virtual threads (Java 21) utile per orchestrare chiamate I/O-bound verso LLM/embedding senza saturare thread pool, forte integrazione con Keycloak/OAuth2 Resource Server, tooling maturo per test e osservabilità. Alternative valutate: Node.js/NestJS (scartato per minore maturità enterprise nel contesto RBAC/multi-tenant complesso richiesto), Python/FastAPI (scartato come layer API principale — Python resta comunque necessario lato ingestion/embedding, si veda oltre).

Sotto-componenti:

- **Auth Gateway**: valida i token JWT emessi da Keycloak (OAuth2 Resource Server), risolve identità utente e ruoli.
- **Tenant Context Resolver**: da claim del token (o header in fase di sviluppo) determina il tenant corrente e lo propaga a tutte le query successive (row-level filtering).
- **API Layer**: controller REST, validazione input, mapping DTO/entità, versionamento API.
- **Document Service**: gestisce upload, metadata, versioning e lifecycle (attivo, archiviato, in revisione, eliminato) dei documenti.
- **Ingestion Orchestrator**: riceve il trigger di un nuovo documento/versione e avvia la pipeline asincrona (vedi `05_RAG_PIPELINE.md`), tracciandone lo stato (in coda, in elaborazione, completata, fallita).
- **Knowledge Domain Service**: gestisce le entità del dominio aziendale per tenant (glossario, acronimi, prodotti, macchine, reparti, procedure, regole, relazioni) — si veda `03_DATABASE_DESIGN.md`.
- **Retrieval Service**: esegue la pipeline di recupero (intent detection → metadata filtering → hybrid search → reranking) descritta in `05_RAG_PIPELINE.md`.
- **Answer Generation Service**: costruisce il contesto finale e orchestra la chiamata all'LLM (via Ollama), assemblando risposta + fonti + confidenza.
- **Audit & Admin Service**: log di audit (chi ha fatto cosa, quando, su quale tenant/documento), gestione utenti/ruoli a livello applicativo (in coordinamento con Keycloak).

### 3.3 Authentication — Keycloak

- Identity Provider centralizzato: gestisce utenti, gruppi, ruoli.
- Ogni tenant è modellato come **Keycloak Realm** dedicato (isolamento forte a livello di identità) oppure come gruppo/realm condiviso con client separati, a seconda della scala del cliente (dettagliato in `06_SECURITY_MODEL.md`).
- Il backend agisce da OAuth2 Resource Server; il frontend da OAuth2/OIDC public client (Authorization Code + PKCE).
- RBAC: ruoli applicativi (es. `TENANT_ADMIN`, `DOCUMENT_MANAGER`, `KNOWLEDGE_EDITOR`, `VIEWER`) mappati su ruoli/gruppi Keycloak.

### 3.4 Document Service

- Upload multi-formato (PDF, Word, Excel, immagini/disegni scansionati in versione futura).
- Metadata: titolo, categoria, tag, autore, data, tenant, stato.
- Versioning: ogni nuova versione di un documento crea una entità versione distinta, senza perdere lo storico; le risposte citano sempre documento + versione.
- Lifecycle: bozza → pubblicato → archiviato/deprecato → (soft) eliminato.

### 3.5 Ingestion Pipeline (dettaglio in `05_RAG_PIPELINE.md`)

Eseguita da worker asincroni, disaccoppiati dal flusso sincrono delle API, per non bloccare l'esperienza utente durante l'elaborazione di documenti grandi:

```
Documento → OCR (se necessario) → Parsing → Cleaning →
Semantic Chunking → Embedding Generation → Vector Storage
```

Implementazione: i worker di ingestion sono processi separati (possibilmente in Python, dato l'ecosistema maturo per OCR/parsing/embedding — es. `unstructured`, `pypdf`, `sentence-transformers`/client verso Ollama), comunicanti col backend Spring Boot tramite coda di messaggi o tabella di job con polling. Questo isola i carichi CPU/GPU-intensivi (OCR, embedding) dal servizio API principale.

### 3.6 Vector Database — PostgreSQL + pgvector

Vedi `03_DATABASE_DESIGN.md` per lo schema. Scelto per la prima versione per: coerenza transazionale con i metadati relazionali, backup unificato, filtro SQL nativo combinabile con ricerca vettoriale (hybrid search senza sincronizzare due sistemi separati), riduzione della complessità operativa per un cliente PMI on-premise. Un vector DB dedicato (Qdrant, Milvus, Weaviate) resta un'opzione di scaling futura se il volume di embedding o i requisiti di throughput lo giustificheranno — non introdotto ora per evitare complessità non necessaria.

### 3.7 Object Storage — MinIO (data lake a due zone)

- Storage dei file documento originali (binari), separato dai metadati (che restano in PostgreSQL).
- Compatibile S3, on-premise, permette bucket isolati per tenant.
- MinIO funge di fatto da **data lake documentale** organizzato in due zone (non un medallion completo — si veda motivazione in `05_RAG_PIPELINE.md` §2.4):
  - **Zona raw** (bronze): file originali così come caricati, immutabili, mai modificati dopo l'upload — unica fonte di verità per un eventuale riprocessamento completo.
  - **Zona parsed** (silver): output intermedio di OCR/parsing/cleaning (testo strutturato in JSON — capitoli/sezioni/pagine/tabelle), persistito prima del chunking. Non è un requisito dell'MVP, ma un'estensione a basso costo pensata per evitare di rieseguire OCR/parsing quando cambia solo la strategia di chunking.
  - Non esiste una zona "gold" separata su object storage: il livello curato/servito è PostgreSQL/pgvector (chunk + embedding + metadata), già transazionale e già interrogabile — duplicarlo su MinIO non aggiungerebbe valore.

Nota tecnologica: **RustFS** (storage S3-compatible scritto in Rust, licenza Apache 2.0) è un'alternativa emergente a MinIO, la cui community edition dal 2025 ha ristretto alcune funzionalità (rimozione del web console) e resta sotto AGPLv3. RustFS è più leggero e senza vincoli di licenza copyleft, ma meno maturo/provato in produzione enterprise. Per l'MVP si mantiene MinIO per la maturità operativa; RustFS va rivalutato se la licenza MinIO diventasse un blocco commerciale con un cliente.

### 3.8 LLM Runtime — Ollama

- Esecuzione locale di modelli generici intercambiabili (Qwen, Llama, Gemma, Mistral).
- Espone sia il modello linguistico per la generazione risposte, sia (se disponibile) modelli di embedding dedicati — sebbene concettualmente il servizio di embedding sia trattato come componente logicamente separato (si veda principio 1 e `05_RAG_PIPELINE.md`), per consentire l'uso di modelli di embedding specializzati (BGE, Nomic Embed) anche se non serviti da Ollama.

---

## 4. Flussi dati principali

### 4.1 Flusso di ingestion documento

1. Utente carica un documento dal frontend → `Document Service` (Spring Boot).
2. Il documento binario viene salvato su MinIO; i metadati iniziali vengono creati in PostgreSQL con stato `PENDING`.
3. `Ingestion Orchestrator` crea un job di ingestion e lo accoda.
4. Un worker di ingestion preleva il job: scarica il file da MinIO, esegue OCR (se necessario) → parsing → cleaning → chunking semantico.
5. Per ogni chunk, il worker richiama il servizio di embedding (Ollama o modello dedicato) e ottiene il vettore.
6. I chunk (contenuto + metadata + embedding) vengono scritti in PostgreSQL/pgvector.
7. Lo stato del documento passa a `PROCESSED` (o `FAILED` con dettaglio errore); il frontend viene aggiornato (polling o notifica).

### 4.2 Flusso di interrogazione (query utente)

1. Utente pone una domanda in chat (frontend) → `Retrieval Service`.
2. `Intent Detection`: classificazione leggera della domanda (es. ricerca fattuale vs. richiesta di generazione contenuto).
3. `Metadata Filtering`: restringe lo spazio di ricerca per tenant, categoria, reparto, eventuali filtri espliciti dell'utente.
4. `Hybrid Search`: combina ricerca vettoriale (pgvector, similarità coseno) e ricerca keyword (full-text PostgreSQL) sui chunk filtrati.
5. `Reranking`: riordina i candidati con un modello/algoritmo di reranking per massimizzare la pertinenza.
6. `Context Builder`: assembla il contesto finale (chunk selezionati, eventualmente capitoli/tabelle correlati, metadata di dominio pertinenti).
7. `Answer Generation Service`: invia prompt + contesto all'LLM (Ollama), riceve la risposta.
8. Il backend assembla la risposta finale con: testo, livello di confidenza, elenco fonti (documento, versione, pagina, estratto).
9. Tutto il flusso viene tracciato nell'audit log (domanda, tenant, utente, documenti citati, timestamp).

### 4.3 Flusso di autenticazione

1. Frontend reindirizza a Keycloak (Authorization Code + PKCE).
2. Utente si autentica; Keycloak emette access token + refresh token (JWT).
3. Frontend allega l'access token a ogni chiamata REST verso il backend.
4. Backend (Resource Server) valida la firma/issuer del token, estrae ruoli e tenant claim.
5. Tenant Context Resolver applica il filtro tenant a tutte le operazioni successive nella richiesta.

---

## 5. Architettura fisica (deployment — MVP)

Deployment target iniziale: **Docker Compose** su singolo host (o piccolo cluster) presso l'infrastruttura del cliente.

```
┌─────────────────────────────── Docker Compose host ───────────────────────────────┐
│                                                                                     │
│  [angular-frontend]   [spring-boot-api]   [ingestion-worker]   [keycloak]          │
│                                                                                     │
│  [postgres+pgvector]      [minio]              [ollama]                            │
│                                                                                     │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

Note:
- Ogni servizio è containerizzato indipendentemente; comunicazione interna su rete Docker dedicata, nessun servizio esposto pubblicamente eccetto reverse proxy (frontend + API gateway).
- `ollama` richiede GPU (raccomandato) o CPU dimensionata in base al modello scelto; dimensionamento hardware da definire per cliente in fase di delivery.
- `ingestion-worker` è scalabile orizzontalmente (più repliche) in modo indipendente dal servizio API, per assorbire picchi di caricamento documenti.

## 6. Evoluzione verso Kubernetes (futuro)

L'architettura a container singoli è già predisposta per migrazione a Kubernetes: nessun componente ha stato locale non esternalizzato (stato in PostgreSQL/MinIO), permettendo scaling orizzontale di API e worker tramite Deployment/HPA, gestione di Ollama come servizio con eventuale scheduling su nodi GPU-enabled. Non implementato nell'MVP per evitare overhead operativo non necessario a un cliente PMI on-premise con singolo host.

---

## 7. Scelte tecnologiche — riepilogo motivazioni

| Tecnologia | Perché | Alternative considerate | Trade-off accettato |
|---|---|---|---|
| Spring Boot 3 / Java 21 | Enterprise-ready, virtual threads per I/O verso LLM, integrazione OAuth2/Keycloak matura | NestJS, FastAPI come layer API principale | Ecosistema Java più verboso di alternative, ma maggiore solidità enterprise/RBAC |
| Angular | Framework enterprise strutturato, adatto a dashboard/admin complesse | React, Vue | Curva di apprendimento maggiore, ma migliore struttura per app enterprise di grandi dimensioni |
| Keycloak | IdP open source enterprise-ready, multi-realm nativo per multi-tenancy, RBAC | Auth0 (cloud, non on-premise-first), Spring Security da solo (reinventerebbe IdP) | Componente aggiuntivo da operare, ma indispensabile per RBAC/tenant reali |
| PostgreSQL + pgvector | Un solo sistema per metadata + vettori, transazionale, backup semplice | Qdrant/Milvus/Weaviate dedicati | Meno performante a scala molto grande; accettabile per volumi PMI, rivalutabile in futuro |
| MinIO | Object storage S3-compatible on-premise, funge da data lake raw+parsed | Storage su filesystem diretto, RustFS | Filesystem diretto non offre isolamento/versioning/API S3 standard; RustFS più leggero ma meno maturo in produzione — rivalutabile se la licenza MinIO diventa un blocco |
| Ollama | Esecuzione locale LLM generici, cambio modello senza cambiare architettura | vLLM, TGI, API cloud | Prestazioni inferiori a serving GPU dedicato su larga scala, accettabile per target PMI |
| Docker Compose (MVP) | Deployment semplice per singolo cliente on-premise | Kubernetes da subito | Meno resiliente/scalabile, ma coerente con la scala e le competenze IT del target iniziale |

---

## Stato del documento

Bozza per revisione — Fase 1/2 del processo di sviluppo. In attesa di approvazione prima di procedere a `03_DATABASE_DESIGN.md`.
