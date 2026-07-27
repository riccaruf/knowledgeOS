# 05 — RAG Pipeline

## 1. Principio guida

```
Documenti → Parsing → Semantic Chunking → Embedding → Vector Database → Retrieval → Context Building → LLM
```

Nessun fine-tuning. La conoscenza aziendale vive esclusivamente nei chunk vettorizzati e nei metadati; l'LLM riceve sempre un contesto esplicito costruito dinamicamente e non deve mai "ricordare" fatti aziendali dai propri pesi.

Distinzione netta, mantenuta in ogni fase: **modello di embedding ≠ modello linguistico generativo**. Sono configurabili e sostituibili indipendentemente (`03_DATABASE_DESIGN.md` §4.8–4.9).

---

## 2. Ingestion Flow

```
Documento caricato
      │
      ▼
┌─────────────┐   se il documento è scansione immagine / PDF non testuale
│    OCR      │──────────────────────────────────────────┐
└─────────────┘                                           │
      │ testo estratto (o già presente)                   │
      ▼                                                   │
┌─────────────┐                                           │
│  Parsing    │◄──────────────────────────────────────────┘
└─────────────┘
      │ struttura documento (titoli, sezioni, pagine, tabelle)
      ▼
┌─────────────┐
│  Cleaning   │  rimozione header/footer ripetuti, normalizzazione whitespace,
└─────────────┘  deduplicazione boilerplate, correzione artefatti OCR
      │
      ▼
┌───────────────────┐
│ Semantic Chunking  │  segmentazione per unità di significato (non a lunghezza fissa)
└───────────────────┘
      │
      ▼
┌───────────────────┐
│ Embedding          │  un vettore per chunk, modello dedicato configurabile
│ Generation         │
└───────────────────┘
      │
      ▼
┌───────────────────┐
│ Vector Storage     │  scrittura in PostgreSQL/pgvector (chunk + metadata + embedding)
│ (pgvector)         │
└───────────────────┘
```

### 2.1 OCR

- Applicato solo quando il parsing rileva assenza di layer testuale (PDF scansionato, immagini di disegni con annotazioni).
- Motore OCR non vincolante architetturalmente (es. Tesseract on-premise); requisito: eseguibile localmente, nessun invio a servizi OCR cloud.
- Output: testo con posizione (bounding box/pagina) per poter comunque citare la pagina di origine.

### 2.2 Parsing

- Estrazione struttura documento per formato:
  - **PDF**: testo, struttura pagine, tabelle, eventuali bookmark/indice.
  - **Word (.docx)**: stili di titolo (Heading 1/2/3) usati per ricostruire la gerarchia capitolo/sezione.
  - **Excel (.xlsx)**: ogni foglio/tabella trattato come unità strutturata, con intestazioni colonna preservate come metadata.
- Output intermedio comune: rappresentazione a albero (documento → capitoli → sezioni → paragrafi/tabelle), indipendente dal formato sorgente, usata come input della fase di chunking.

### 2.3 Cleaning

- Rimozione di header/footer ripetuti su ogni pagina (rilevati per ripetizione), numeri di pagina isolati, watermark testuali.
- Normalizzazione spazi, a capo, encoding.
- Correzione euristica di artefatti OCR (spaziatura anomala, caratteri non riconosciuti) dove possibile; conservazione del testo grezzo originale per audit in caso di dubbio.

### 2.4 Persistenza intermedia (zona "parsed"/silver, non un medallion completo)

L'output di OCR → Parsing → Cleaning (l'albero documento→capitoli→sezioni→paragrafi/tabelle di §2.2, già ripulito) viene persistito come JSON su MinIO, nella zona "parsed" accanto alla zona raw dei file originali (`02_SOLUTION_ARCHITECTURE.md` §3.7), prima di procedere al chunking.

Motivazione: è l'unico punto della pipeline dove un dato intermedio ha un motivo concreto per essere riutilizzato — se la strategia di chunking cambia (es. soglie dimensionali diverse, nuova euristica di rottura semantica), si può rieseguire chunking + embedding direttamente da qui, senza rifare OCR/parsing (fasi tipicamente più costose, specialmente l'OCR su documenti scansionati).

Questo non equivale ad adottare un'architettura medallion (bronze/silver/gold) in senso pieno: quel pattern ha senso quando più consumer eterogenei leggono dati a livelli di raffinazione diversi (analytics, BI, ML in parallelo). Qui esiste un solo consumer (la pipeline di retrieval) e un solo livello realmente "servito" (i chunk in pgvector) — introdurre zone formali con criteri di promozione tra livelli sarebbe complessità non giustificata dal caso d'uso. La zona "parsed" è quindi un artefatto di ottimizzazione del riprocessamento, non uno strato architetturale con SLA o consumer propri; non è un requisito dell'MVP (si veda `07_MVP_ROADMAP.md`).

**Nota di implementazione**: nella build MVP questa zona "parsed" non è stata implementata — `PdfParsingService` esegue parsing e cleaning in memoria ad ogni esecuzione del job di ingestion, senza persistere l'intermedio su MinIO. Resta un'estensione a basso costo da aggiungere quando il costo di rieseguire il parsing (in particolare con OCR, Milestone 2) lo giustificherà.

---

## 3. Semantic Chunking Strategy

### 3.1 Perché non "1000 caratteri + overlap"

Una segmentazione a lunghezza fissa ignora la struttura semantica del documento: può spezzare una tabella a metà, separare un titolo dal proprio contenuto, o unire due argomenti non correlati nello stesso chunk — degradando sia il retrieval sia la qualità delle citazioni verso l'utente.

### 3.2 Approccio adottato

Chunking **struttura-aware e semantic-aware**, in questo ordine di priorità:

1. **Confini strutturali come prima frontiera**: non si spezza mai un chunk a metà di una sezione/capitolo se la sezione rientra in una soglia dimensionale ragionevole; i confini di capitolo/sezione rilevati in fase di parsing sono confini di chunk candidati.
2. **Segmentazione semantica all'interno della sezione**: se una sezione supera la soglia dimensionale, viene suddivisa in chunk più piccoli individuando punti di rottura a bassa similarità semantica tra frasi/paragrafi adiacenti (variazione di argomento), piuttosto che a un conteggio di caratteri fisso.
3. **Elementi strutturati preservati atomicamente**: una tabella non viene mai spezzata tra due chunk — o interamente in un chunk (con eventuale suddivisione per riga solo se la tabella eccede ampiamente la soglia, mantenendo intestazioni ripetute in ogni sotto-chunk per preservare contesto).
4. **Soglie dimensionali indicative**: chunk target ~300–800 token, con tolleranza per non spezzare unità semantiche; overlap minimo (non lo overlap fisso "a scorrimento" tipico del chunking ingenuo) limitato a includere il titolo/intestazione della sezione di appartenenza in ogni chunk figlio, per mantenere contesto locale.

**Nota di implementazione (semplificazione MVP dichiarata nel piano)**: per il PDF, che a differenza di `.docx` non porta una gerarchia di titoli nativa, `HeadingAwarePdfTextExtractor` rileva i confini di sezione con un'euristica sulla dimensione del font (una riga è trattata come titolo se il suo font è significativamente più grande della dimensione mediana del corpo testo del documento). Il punto 2 (rottura per similarità semantica embedding-based all'interno di una sezione lunga) non è implementato in questo giro: `ChunkingService` applica invece una soglia dimensionale basata su conteggio parole (non un vero tokenizer) come approssimazione, rispettando comunque il vincolo di non spezzare un paragrafo a metà salvo che ecceda da solo la soglia massima. Il raffinamento a rottura semantica fine resta pianificato per Milestone 2 (`07_MVP_ROADMAP.md`). L'OCR (§2.1) non è stato implementato: un PDF privo di testo estraibile viene esplicitamente rifiutato con un errore invece di produrre chunk vuoti.

### 3.3 Metadata obbligatori per chunk

Ogni chunk (si veda anche `03_DATABASE_DESIGN.md` §4.5) porta sempre:

```json
{
  "id": "c123...",
  "documentId": "d1e2...",
  "documentVersionId": "v9a8...",
  "title": "Manuale Pompa PX400",
  "section": "4.3 Manutenzione ordinaria",
  "page": 24,
  "content": "Il filtro deve essere sostituito ogni 500 ore...",
  "metadata": {
    "author": "Ufficio Tecnico",
    "category": "Manuale tecnico",
    "tags": ["pompa", "manutenzione"],
    "timestamp": "2026-05-10T09:12:00Z",
    "relatedEntities": ["PX400", "Filtro F32"]
  },
  "embedding": [ ... ],
  "embeddingModel": "bge-large-en"
}
```

Questo garantisce che ogni chunk recuperato in fase di query possa essere ricondotto univocamente a documento, versione, posizione e dominio di conoscenza correlato — requisito diretto della vision di risposte "sempre verificabili".

---

## 4. Embedding Strategy

- Modello di embedding configurabile per tenant (`embedding_model_config`), candidati iniziali: **BGE** (es. `bge-large`), **Nomic Embed**, o altri modelli compatibili serviti localmente.
- L'embedding è generato per ogni chunk al momento dell'ingestion; il modello usato è registrato su ogni chunk (`chunk.embedding_model`) per permettere convivenza/migrazione tra modelli.
- **Rigenerazione senza cambio LLM**: cambiare modello di embedding richiede un job batch di re-embedding su tutti i chunk esistenti del tenant — operazione indipendente da qualsiasi configurazione del modello linguistico generativo, coerente con il principio di separazione LLM/embedding.
- Gli embedding non vengono mai calcolati o arricchiti tramite servizi cloud esterni: sempre tramite modello eseguito localmente (via Ollama o runtime di inferenza dedicato on-premise).

---

## 5. Retrieval Strategy

```
Domanda utente
      │
      ▼
┌───────────────────┐
│ Intent Detection   │  classificazione leggera: domanda fattuale, richiesta di
└───────────────────┘  generazione contenuto, richiesta di confronto/aggregazione
      │
      ▼
┌───────────────────┐
│ Metadata Filtering │  restrizione per tenant (obbligatoria), categoria, reparto,
└───────────────────┘  filtri espliciti utente, entità di dominio menzionate
      │
      ▼
┌───────────────────┐
│ Hybrid Search      │  ricerca vettoriale (pgvector, coseno) + ricerca keyword
│ (Vector + Keyword) │  (full-text PostgreSQL) sullo stesso sottoinsieme filtrato
└───────────────────┘
      │
      ▼
┌───────────────────┐
│ Reranking          │  riordino dei candidati combinati per massimizzare pertinenza
└───────────────────┘
      │
      ▼
┌───────────────────┐
│ Context Builder     │
└───────────────────┘
      │
      ▼
     LLM
```

### 5.1 Intent Detection

Classificazione leggera (regola euristica o modello piccolo dedicato) della domanda in categorie che influenzano la strategia di retrieval e di generazione, ad esempio:
- **Fattuale puntuale** ("ogni quante ore...") → retrieval mirato, poche fonti ad alta precisione.
- **Esplorativa/di sintesi** ("riassumi la procedura di manutenzione") → retrieval più ampio, possibile inclusione di intere sezioni.
- **Generativa** ("scrivi una bozza di istruzione operativa per...") → retrieval usato come riferimento di stile/contenuto, non come singola fonte da citare letteralmente.

### 5.2 Metadata Filtering

- Il filtro `tenant_id` è **sempre** applicato, non è opzionale né configurabile dal client (enforcement a livello di query, si veda `06_SECURITY_MODEL.md`).
- Filtri aggiuntivi (categoria, reparto, tag) applicati sia da selezione esplicita utente (via API `filters`, `04_API_SPECIFICATION.md` §4.1), sia dedotti dall'intent detection (es. riconoscimento di un'entità di dominio nella domanda → filtro sui documenti collegati a quell'entità in `knowledge_entity`/`knowledge_relation`).

### 5.3 Hybrid Search

- **Componente vettoriale**: similarità coseno tra embedding della domanda (stesso modello di embedding del tenant) e `chunk.embedding`, tramite indice HNSW.
- **Componente keyword**: full-text search PostgreSQL su `chunk.content_tsv`, utile per termini esatti (codici prodotto, sigle, numeri normativi) che la sola ricerca semantica può sotto-pesare.
- Combinazione: punteggio ibrido (es. combinazione pesata o Reciprocal Rank Fusion) tra i due ranking, non sostituzione dell'uno con l'altro.

### 5.4 Reranking

- I migliori N candidati dell'hybrid search (es. top 30–50) vengono ripassati a un passo di reranking (cross-encoder locale o modello dedicato) che valuta la coppia (domanda, chunk) con maggiore precisione rispetto al solo punteggio di similarità iniziale, producendo il set finale (es. top 5–10) da passare al Context Builder.
- Il reranking è un componente sostituibile indipendentemente (principio di modularità), eseguibile on-premise.

---

## 6. Context Builder

Responsabilità: trasformare i chunk selezionati in un contesto ottimale per l'LLM, non un semplice concatenamento.

Può includere:
- I chunk selezionati, ordinati per rilevanza e, dove utile, per posizione originale nel documento (per leggibilità/coerenza).
- **Espansione contestuale**: se un chunk selezionato fa parte di una sezione più ampia e la domanda è di tipo esplorativo/di sintesi, il Context Builder può includere l'intero capitolo invece del solo chunk isolato.
- **Tabelle correlate**: se un chunk referenzia una tabella (dati tecnici, parametri), la tabella viene inclusa per intero anziché come frammento.
- **Metadata di dominio**: se la domanda o i chunk menzionano un'entità del dominio di conoscenza (es. "PX400"), vengono incluse le relazioni rilevanti da `knowledge_entity`/`knowledge_relation` (es. "PX400 richiede Procedura M12") per arricchire il contesto senza dover recuperare un chunk testuale separato per quell'informazione strutturata.
- **Deduplicazione**: chunk provenienti dalla stessa sezione o fortemente sovrapposti vengono deduplicati prima dell'invio all'LLM.
- **Budget di contesto**: il Context Builder rispetta un limite di token configurabile in base al modello LLM attivo, dando priorità ai chunk con punteggio di reranking più alto in caso di eccedenza.

Obiettivo esplicito: ridurre l'allucinazione fornendo un contesto denso e pertinente, evitando sia contesto insufficiente (risposta incompleta) sia contesto eccessivo/rumoroso (diluizione dell'attenzione del modello, aumento del rischio di confusione tra fonti).

---

## 7. Answer Generation

Il prompt inviato all'LLM include sempre, in modo strutturato:
1. Istruzioni di sistema (ruolo, vincolo di rispondere solo sulla base del contesto fornito, istruzione esplicita di dichiarare quando l'informazione non è presente nel contesto).
2. Il contesto costruito (chunk + metadata + eventuali relazioni di dominio).
3. La domanda dell'utente (eventualmente riformulata dall'intent detection, es. risoluzione di riferimenti da conversazioni precedenti).

Vincolo esplicito nel prompt di sistema: il modello deve rifiutarsi di rispondere con informazioni non presenti nel contesto fornito, dichiarando l'assenza di informazione, piuttosto che generare contenuto plausibile ma non verificato (mitigazione allucinazione al livello del prompt, complementare — non sostitutiva — alla qualità del retrieval).

### 7.1 Struttura output

Ogni risposta generata dal sistema (non necessariamente dal solo LLM: assemblata dal backend) contiene sempre:

- **Risposta naturale**: testo generato.
- **Livello di confidenza**: calcolato combinando punteggio di reranking dei chunk usati, copertura della domanda da parte del contesto, e (se disponibile) segnali del modello stesso — non un valore arbitrario del solo LLM.
- **Fonti utilizzate**: per ciascun chunk effettivamente incluso nel contesto e ritenuto rilevante: documento, versione, pagina, sezione, estratto originale (si veda contratto in `04_API_SPECIFICATION.md` §4.1).

Esempio (coerente con la vision):
```
Risposta: "Il filtro deve essere sostituito ogni 500 ore."
Fonte: Manuale_Pompa.pdf — Pagina 24 — Sezione 4.3 — v1.2
```

---

## 8. Estensioni future della pipeline

- **Multi-hop retrieval**: per domande che richiedono di attraversare più entità del grafo di dominio (es. "quali procedure sono richieste per tutte le macchine del reparto Produzione?") prima di effettuare la ricerca semantica sui chunk.
- **Retrieval su immagini/disegni tecnici**: embedding multimodale per disegni/schemi, oltre al solo testo OCR associato.
- **Feedback loop**: utilizzo del feedback utente (`04_API_SPECIFICATION.md` §4.3) per affinare pesi di reranking nel tempo, senza mai modificare i pesi dell'LLM (coerente con il principio "no fine-tuning").
- **Agenti specializzati**: la stessa pipeline, filtrata per dominio di conoscenza e permessi specifici (`agent_config`), alimenta agenti verticali (Production, HR, Quality, Sales) senza duplicare l'infrastruttura RAG.

---

## Stato del documento

Bozza per revisione — Fase 1/2 del processo di sviluppo. In attesa di approvazione prima di procedere a `06_SECURITY_MODEL.md`.
