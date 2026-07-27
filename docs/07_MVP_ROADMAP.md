# 07 — MVP Roadmap

## 1. Filosofia della roadmap

Ogni milestone deve produrre un sistema **utilizzabile end-to-end**, non componenti isolati. L'MVP dimostra il ciclo completo "documento → risposta con fonte verificabile" a singolo tenant; le milestone successive aggiungono profondità enterprise (multi-tenant reale, qualità del retrieval, dominio di conoscenza strutturato) e infine capacità AI avanzate (agenti specializzati, generazione contenuti).

Nessuna milestone introduce fine-tuning del modello LLM (vincolo architetturale permanente, non solo dell'MVP).

---

## 2. Milestone 1 — MVP (Minimum Viable Product)

Obiettivo: dimostrare il flusso completo con un singolo tenant, su un solo modello LLM e un solo modello di embedding, con sicurezza e tracciabilità già presenti fin dal primo rilascio (non aggiunte dopo).

### 2.1 Perimetro funzionale

- **Frontend Angular**: login (via Keycloak), upload documento PDF, lista documenti con stato ingestion, interfaccia di chat, visualizzazione fonti (documento/pagina/estratto).
- **Backend Spring Boot 3 / Java 21**: API REST core (`04_API_SPECIFICATION.md` §2–4), Tenant Context Resolver (anche con singolo tenant attivo, per non dover riscrivere in seguito), Document Service, Ingestion Orchestrator, Retrieval Service, Answer Generation Service.
- **Keycloak**: autenticazione OIDC, realm singolo, ruoli base (`TENANT_ADMIN`, `DOCUMENT_MANAGER`, `VIEWER`).
- **Upload PDF**: supporto al formato PDF (testuale; OCR per PDF scansionati incluso già in MVP se il target cliente iniziale ne ha necessità concreta, altrimenti rimandato a Milestone 2).
- **Parsing + Semantic Chunking**: pipeline descritta in `05_RAG_PIPELINE.md` §2–3, per il formato PDF.
- **Embedding Generation**: singolo modello di embedding configurato (es. BGE o Nomic Embed), eseguito localmente.
- **pgvector storage**: schema base (`document`, `document_version`, `chunk`) con indice HNSW.
- **RAG query**: pipeline di retrieval semplificata — ricerca vettoriale + filtro metadata base; hybrid search e reranking avanzato pianificati per Milestone 2 (l'MVP può includere una versione base di ricerca keyword se a costo contenuto, altrimenti solo vettoriale).
- **Risposte con citazioni**: ogni risposta include documento, pagina, estratto, livello di confidenza (anche se calcolato in forma semplificata rispetto al modello finale).
- **Audit minimo**: log delle query e degli upload (tabelle `audit_log`/`query_log` già presenti da subito, per non dover migrare schema in seguito).

### 2.2 Esplicitamente fuori perimetro MVP

- Multi-tenant reale con più tenant attivi contemporaneamente (schema e codice già multi-tenant-ready, ma validato in produzione con un solo tenant).
- Hybrid search completa (vector + keyword con fusione avanzata) e reranking dedicato.
- Knowledge Domain strutturato (glossario, entità, relazioni) — solo modello dati predisposto, non ancora popolato/gestito da UI.
- Agenti specializzati.
- Formati Word/Excel/disegni (solo PDF in MVP).
- Generazione di nuovi contenuti (solo Q&A in MVP).

### 2.3 Criteri di uscita

- Un utente carica un PDF, il sistema lo elabora in modo asincrono e lo rende interrogabile.
- Una domanda in linguaggio naturale ottiene risposta corretta con fonte verificabile (documento + pagina + estratto) su un set di documenti di test concordato col cliente pilota.
- Nessun dato esce dall'infrastruttura on-premise durante l'intero flusso (verifica esplicita, non assunzione).
- RBAC di base funzionante (un `VIEWER` non può caricare/eliminare documenti; un `DOCUMENT_MANAGER` sì).

---

## 3. Milestone 2 — Enterprise Features

Obiettivo: rendere il sistema utilizzabile in produzione da più clienti/reparti reali, con la qualità di retrieval e la governance richieste in ambito enterprise.

### 3.1 Retrieval e qualità delle risposte

- **Hybrid Search** completa: fusione tra ricerca vettoriale e keyword (`05_RAG_PIPELINE.md` §5.3).
- **Reranking** dedicato (cross-encoder locale) per aumentare la precisione dei chunk selezionati.
- **Context Builder** avanzato: espansione a capitolo, inclusione tabelle correlate, deduplicazione.
- Estensione formati documentali: **Word (.docx)**, **Excel (.xlsx)**, PDF scansionati con **OCR**.

### 3.2 Multi-tenancy reale

- Attivazione realm Keycloak dedicati per più tenant in produzione contemporanea.
- Validazione Row Level Security in ambiente multi-tenant con carico reale.
- Isolamento storage MinIO per tenant verificato in produzione.
- Configurazione LLM/embedding differenziata per tenant.

### 3.3 Audit e governance

- Dashboard di audit completa (`04_API_SPECIFICATION.md` §6.3) per `TENANT_ADMIN`.
- Gestione lifecycle documentale completa (bozza → pubblicato → archiviato → deprecato) con notifiche su documenti obsoleti.
- Feedback utente su risposte (`04_API_SPECIFICATION.md` §4.3) come base per il miglioramento continuo del retrieval (non del modello LLM).

### 3.4 Knowledge Domain

- UI di gestione per `knowledge_entity`/`knowledge_relation` (`04_API_SPECIFICATION.md` §5).
- Visualizzazione grafo di dominio.
- Arricchimento automatico (semi-assistito) delle entità di dominio a partire dai documenti ingeriti, con validazione umana prima della pubblicazione nel dominio di conoscenza.

### 3.5 Criteri di uscita

- Almeno due tenant reali operativi in parallelo sulla stessa installazione, con isolamento verificato.
- Miglioramento misurabile della pertinenza delle risposte rispetto all'MVP (metrica interna su set di domande di validazione).
- Copertura formati documentali estesa a Word/Excel/PDF scansionati.

---

## 4. Milestone 3 — Advanced AI Features

Obiettivo: sfruttare il Knowledge Layer maturo per casi d'uso oltre la semplice Q&A, sempre nel rispetto del vincolo "nessun fine-tuning".

### 4.1 Agenti specializzati

- Implementazione piena di `agent_config` (`03_DATABASE_DESIGN.md` §4.10, `04_API_SPECIFICATION.md` §7): Production Agent, HR Agent, Quality Agent, Sales Agent come configurazioni di prompt + dominio di conoscenza + permessi, non come modelli separati.
- Gestione permessi granulare per agente (chi può interrogare quale agente).

### 4.2 Generazione di contenuti

- Generazione assistita di nuovi documenti (es. bozze di istruzioni operative, procedure) basata sulla conoscenza aziendale esistente, con validazione umana obbligatoria prima della pubblicazione come documento ufficiale.
- Template configurabili per tipologia di documento generato.

### 4.3 Capacità RAG avanzate

- Multi-hop retrieval sul grafo di dominio (`05_RAG_PIPELINE.md` §8).
- Retrieval multimodale (disegni tecnici, schemi) oltre al solo testo.
- Affinamento del reranking tramite feedback loop storico (`query_log` + feedback utente).

### 4.4 Scalabilità infrastrutturale

- Valutazione migrazione a Kubernetes se il numero di tenant/carico lo giustifica (`02_SOLUTION_ARCHITECTURE.md` §6).
- Valutazione (solo se necessario per volumi molto elevati) di un vector database dedicato esterno in sostituzione/affiancamento a pgvector — decisione da prendere sulla base di dati di produzione reali, non preventivamente.

### 4.5 Criteri di uscita

- Almeno un agente specializzato in produzione presso un cliente pilota.
- Almeno un flusso di generazione contenuti validato da un cliente reale.

---

## 5. Vista sintetica

```
Milestone 1 — MVP
  Angular + Spring Boot + Keycloak + Upload PDF + Parsing + Semantic Chunking
  + Embedding + pgvector + RAG query + Risposte con citazioni
        │
        ▼
Milestone 2 — Enterprise Features
  Hybrid Search + Reranking + Multi-tenant reale + Audit completo
  + Knowledge Domain + Formati estesi (Word/Excel/OCR)
        │
        ▼
Milestone 3 — Advanced AI Features
  Agenti specializzati + Generazione contenuti + Retrieval avanzato
  + Scalabilità infrastrutturale (Kubernetes / vector DB dedicato se necessario)
```

---

## 6. Nota di processo

Ogni modifica architetturale introdotta durante lo sviluppo di una milestone deve riflettersi negli altri documenti in `/docs` (in particolare `02_SOLUTION_ARCHITECTURE.md`, `03_DATABASE_DESIGN.md`, `04_API_SPECIFICATION.md`, `05_RAG_PIPELINE.md`) prima di essere considerata conclusa, per mantenere la documentazione come fonte di verità coerente con il codice.

---

## 7. Stato di implementazione — Milestone 1 (primo giro)

Primo giro di implementazione completato (repository `backend/`, `frontend/`, `infra/`). Stato rispetto al perimetro §2.1:

| Elemento | Stato | Note |
|---|---|---|
| Angular + login Keycloak | ✅ | `onLoad: login-required`, ruoli realm mappati su authority Spring |
| Upload PDF | ✅ | Solo PDF testuale; PDF scansionati (OCR) esplicitamente rifiutati |
| Parsing + Semantic Chunking | ✅ (semplificato) | Euristica font-size per sezioni PDF; soglia a conteggio parole invece di rottura semantica embedding-based (si veda `05_RAG_PIPELINE.md` §3.2) |
| Embedding Generation | ✅ | Via Ollama (`nomic-embed-text` di default), modello disaccoppiato dall'LLM |
| pgvector storage | ✅ | Incluso RLS con `FORCE` (si veda `06_SECURITY_MODEL.md` §3.2) |
| RAG query | ✅ (solo vettoriale) | Hybrid search e reranking confermati come Milestone 2, non nel primo giro |
| Risposte con citazioni | ✅ | Documento, versione, pagina, sezione, estratto, confidenza (media similarità) |
| Audit minimo | ✅ | `audit_log`/`query_log` popolati; endpoint di consultazione senza ancora i filtri di `04_API_SPECIFICATION.md` §6.3 |
| Multi-tenant (schema-ready, singolo tenant validato) | ✅ | Tenant demo seedato via Flyway, provisioning utenti JIT al primo login |
| Knowledge Domain, agenti, config modelli via API | ⏭️ | Confermati fuori perimetro di questo giro, come da roadmap originale |

Deviazioni rispetto alla documentazione originale sono annotate puntualmente in `03_DATABASE_DESIGN.md` (tabella `ingestion_job`), `05_RAG_PIPELINE.md` (semplificazioni chunking/OCR) e `04_API_SPECIFICATION.md` (endpoint non ancora implementati).

---

## Stato del documento

Documentazione architetturale approvata; primo giro di implementazione della Milestone 1 completato (si veda §7). Iterazioni successive continueranno ad aggiornare questa tabella.
