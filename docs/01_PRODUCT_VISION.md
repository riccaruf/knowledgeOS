# 01 — Product Vision

## 1. Nome prodotto (working title)

**KnowledgeOS** — piattaforma AI on-premise per la gestione intelligente della conoscenza aziendale.

Il nome è provvisorio: identifica il prodotto come "sistema operativo della conoscenza", non come chatbot. Questo documento fissa il perimetro di visione prima di qualsiasi scelta implementativa.

---

## 2. Vision

> Ogni PMI possiede una quantità enorme di conoscenza intrappolata in documenti disomogenei e nella memoria delle persone. KnowledgeOS trasforma questa conoscenza dispersa in un asset digitale interrogabile, verificabile e sicuro, che vive interamente dentro i confini dell'azienda.

KnowledgeOS non è un prodotto "chat sui PDF". È un **Knowledge Layer**: uno strato infrastrutturale che si posiziona tra la documentazione aziendale grezza e le persone che devono usarla per lavorare, decidere, produrre. La chat in linguaggio naturale è solo una delle interfacce di accesso a questo strato — non il prodotto.

Il valore del prodotto non risiede nel modello linguistico (che è intenzionalmente generico e sostituibile), ma nella qualità con cui il sistema:

1. struttura la conoscenza aziendale in forma semantica,
2. la mantiene aggiornata, versionata e tracciabile,
3. la rende recuperabile con precisione e la espone con fonti verificabili,
4. lo fa senza mai far uscire i dati dal perimetro del cliente.

---

## 3. Il problema di business

Le PMI (in particolare quelle manifatturiere, industriali, tecniche e regolamentate) accumulano nel tempo grandi volumi di documentazione eterogenea:

- manuali tecnici e manuali macchina
- procedure operative standard (SOP)
- documentazione di sistema qualità ISO
- capitolati tecnici e capitolati di gara
- specifiche tecniche di prodotto
- disegni tecnici ed elaborati
- contratti e allegati contrattuali
- documenti Word, fogli Excel, PDF scansionati
- knowledge base interne informali (wiki, note, email)

### 3.1 Sintomi osservabili

- Il tempo delle persone tecniche ed esperte viene consumato rispondendo a domande già documentate altrove.
- La conoscenza critica (es. "come si configura la macchina X per il cliente Y") esiste solo nella testa di poche persone senior — rischio di perdita al turnover/pensionamento.
- I documenti sono duplicati, in versioni multiple, spesso senza un chiaro "documento maestro" aggiornato.
- Le persone non sanno se un'informazione trovata è ancora valida o è stata superata da una revisione successiva.
- La ricerca full-text tradizionale (file system, intranet, DMS) restituisce liste di documenti, non risposte — l'utente deve comunque leggere e interpretare.
- L'onboarding di nuovo personale tecnico richiede mesi perché la conoscenza non è strutturata né interrogabile.

### 3.2 Causa radice

La conoscenza aziendale è **non strutturata, non semantizzata e non centralizzata in forma interrogabile**. Esistono repository di documenti (file server, DMS, intranet), ma non esiste uno strato che comprenda il *contenuto* dei documenti, li colleghi tra loro, e risponda a domande — mantenendo sempre la tracciabilità verso la fonte.

### 3.3 Perché ora

- I modelli LLM locali (Ollama, Qwen, Llama, Mistral, Gemma) hanno raggiunto una qualità sufficiente per essere eseguiti on-premise su hardware aziendale accessibile, senza dipendenza da API cloud.
- Le normative su privacy, riservatezza industriale e proprietà intellettuale (in particolare per aziende con know-how tecnico/produttivo) rendono l'invio di documentazione a servizi cloud esterni un rischio non accettabile per molte imprese.
- Esiste quindi uno spazio commerciale per un prodotto RAG **enterprise, on-premise, verificabile**, distinto sia dai chatbot generici cloud-based sia dai tool "demo" costruiti su singoli PDF.

---

## 4. Target clienti

### 4.1 Profilo primario

PMI e aziende mid-market, prevalentemente:

- **Manifatturiero e industriale**: produttori di macchine, impiantistica, componentistica — grandi volumi di manuali tecnici, disegni, procedure di manutenzione.
- **Aziende certificate ISO** (9001, 14001, 45001, settore-specifiche): necessità di gestire procedure, istruzioni operative, documentazione di audit in modo tracciabile.
- **Aziende di ingegneria e progettazione**: capitolati, specifiche tecniche, offerte, documentazione di gara.
- **Aziende con know-how tecnico sensibile**: per le quali l'invio di documentazione a cloud pubblici (OpenAI, Anthropic, Google, ecc.) è escluso da policy interne, contrattuali o di settore.

### 4.2 Caratteristiche comuni del cliente target

- Possiede uno storico documentale consistente (centinaia/migliaia di documenti), spesso poco organizzato.
- Ha già sensibilità o vincoli su privacy e riservatezza dei dati (contratti con clausole di confidenzialità, proprietà industriale, dati di clienti terzi nei capitolati).
- Ha un reparto IT interno o un fornitore IT di fiducia in grado di gestire un'infrastruttura on-premise (server, Docker, eventualmente Kubernetes).
- Non ha (né vuole avere) competenze interne di data science/ML — il prodotto deve essere utilizzabile senza necessità di fine-tuning o gestione diretta dei modelli.

### 4.3 Buyer e utenti

- **Buyer economico**: direzione generale, direzione IT, quality manager — motivati da risparmio di tempo, riduzione del rischio di perdita di know-how, compliance.
- **Buyer tecnico**: IT manager/CTO — motivato da controllo sull'infrastruttura, assenza di dipendenze cloud, possibilità di scegliere/sostituire il modello LLM.
- **Utenti finali**: tecnici di produzione, manutentori, ufficio qualità, ufficio tecnico, ufficio commerciale/gare, HR, nuovi assunti.

---

## 5. Value proposition

### 5.1 Proposta di valore centrale

> "La memoria digitale della tua azienda, che resta dentro casa tua."

KnowledgeOS converte l'intero patrimonio documentale aziendale in una base di conoscenza semantica interrogabile in linguaggio naturale, restituendo risposte sempre accompagnate da fonte, documento, pagina ed estratto originale — senza che un solo byte di documentazione lasci l'infrastruttura del cliente.

### 5.2 Pilastri di valore

1. **Time-to-answer**: da minuti/ore di ricerca manuale a secondi di risposta verificata.
2. **Affidabilità verificabile**: ogni risposta è tracciabile alla fonte esatta (documento, versione, pagina) — l'utente non deve "fidarsi" del modello, può verificare.
3. **Privacy by design**: on-premise first. Nessun documento, nessun embedding, nessun log di conversazione viene inviato a servizi terzi. Requisito commerciale, non solo tecnico.
4. **Indipendenza dal fornitore LLM**: il modello linguistico è intercambiabile (Ollama con Qwen/Llama/Gemma/Mistral). Il cliente non è vincolato a un singolo vendor di AI; la conoscenza aziendale non dipende dai pesi di un modello specifico.
5. **Knowledge Layer, non solo chat**: la stessa base di conoscenza semantica alimenta più modalità d'uso (chat, generazione documenti, agenti specializzati per reparto) — investimento riusabile, non un tool monouso.
6. **Multi-tenant e scalabile**: nato per essere distribuito sia come installazione dedicata on-premise per singolo cliente, sia in futuro come SaaS multi-tenant, senza riscrittura architetturale.

### 5.3 Differenziazione rispetto alle alternative

| Alternativa | Limite | Come si posiziona KnowledgeOS |
|---|---|---|
| Chatbot cloud generico (ChatGPT/Copilot aziendale) | Dati inviati a cloud terzo; nessuna garanzia di isolamento; risposte non tracciabili a fonte precisa | On-premise, fonti verificabili documento/pagina |
| Tool "chat sui PDF" consumer | Nessun multi-tenant, nessun RBAC, nessuna gestione ciclo di vita documentale, nessun dominio di conoscenza aziendale | Prodotto enterprise: versioning, RBAC, audit, dominio aziendale strutturato |
| DMS/Intranet con ricerca full-text | Ricerca per parole chiave, non per significato; nessuna risposta sintetica | Ricerca semantica ibrida + risposta generata con citazioni |
| Fine-tuning di un LLM sui dati aziendali | Costoso, difficile da aggiornare, "black box", rischio di leakage nei pesi | Conoscenza nel livello RAG, aggiornabile in tempo reale, ispezionabile e cancellabile per documento |
| System integrator "AI on-premise per PMI" (es. AigisLab) | Azienda di consulenza/progetti custom: portfolio ampio e generico (agenti, sviluppo software, forecasting, ottimizzazione produzione, OCR documentale), monetizzato a commessa; l'"on-premise" è un argomento di vendita trasversale, non un prodotto verticale sulla knowledge base | KnowledgeOS è un **prodotto pacchettizzato**, non un progetto da ricostruire per ogni cliente: piattaforma riusabile (Docker Compose/Kubernetes, multi-tenant-ready) con RBAC, audit, versioning e Knowledge Domain già incluse come fondamenta, non da sviluppare su misura volta per volta |

### 5.3bis Nota sulla categoria "system integrator AI on-premise"

Questa categoria di concorrenti (system integrator/boutique di consulenza AI che vendono "on-premise = privacy" come claim generico su un portfolio ampio di servizi) è la più vicina per posizionamento di privacy/compliance, ma compete su un piano diverso: vende progetti, non un prodotto verticale. La differenziazione di KnowledgeOS verso questa categoria si gioca su tre leve, in ordine di difendibilità:

1. **Modello di ricavo**: licenza/abbonamento su una piattaforma riusabile vs ricavi a commessa — time-to-value più rapido per il cliente, marginalità più scalabile per noi (non richiede uno sviluppo custom completo ad ogni vendita).
2. **Profondità enterprise nativa**: RBAC, audit, versioning documentale, isolamento tenant con Row Level Security in difesa in profondità sono parte dell'architettura di prodotto (`02_SOLUTION_ARCHITECTURE.md`, `06_SECURITY_MODEL.md`), non componenti da progettare ex novo per ogni cliente come farebbe un integrator.
3. **Verticalità sulla conoscenza documentale**: il claim non è "AI on-premise" in generale, ma specificamente "memoria digitale verificabile dell'azienda" con citazione a documento/pagina/estratto — un posizionamento più stretto ma più concreto per il target PMI con grandi volumi di documentazione tecnica/regolamentata (manifatturiero, ISO, capitolati), rispetto a un menu ampio di servizi AI.

---

## 6. Use case principali

### UC1 — Ricerca procedure e manuali tecnici
Un tecnico di manutenzione chiede: *"Ogni quante ore va sostituito il filtro della pompa PX400?"*
Il sistema risponde con il valore esatto, citando `Manuale_Pompa_PX400.pdf`, pagina 24, versione corrente.

### UC2 — Supporto a gare e capitolati
L'ufficio tecnico commerciale deve rispondere a un capitolato di gara. Interroga il sistema su requisiti già affrontati in gare precedenti, ottenendo estratti da capitolati e offerte pregresse con riferimento al documento e cliente originario (nel rispetto dell'isolamento tenant/riservatezza).

### UC3 — Onboarding di nuovo personale
Un neoassunto in produzione può interrogare il sistema su procedure operative, organigramma reparti, glossario aziendale interno, riducendo il carico su personale senior per il training iniziale.

### UC4 — Audit e conformità ISO
Il responsabile qualità verifica rapidamente se una procedura documentata è coerente con la versione più recente approvata, e ottiene un log tracciabile di quali documenti sono stati consultati per rispondere a un audit.

### UC5 — Continuità della conoscenza (bus factor)
Conoscenza tacita di dipendenti esperti in procinto di pensionamento/uscita viene preventivamente strutturata come documentazione, ingerita nel sistema, e resa interrogabile dal resto dell'organizzazione.

### UC6 — Generazione di nuovi contenuti basata su conoscenza esistente
L'ufficio qualità richiede la bozza di una nuova istruzione operativa per un macchinario, e il sistema genera un contenuto coerente con lo stile e i contenuti delle procedure esistenti, da validare da parte umana.

### UC7 (futuro) — Agenti specializzati per reparto
Un "Production Agent" risponde solo su conoscenza di produzione con permessi e tool specifici; un "HR Agent" risponde solo su conoscenza HR — stesso Knowledge Layer, configurazioni diverse (prompt + dominio + permessi).

---

## 7. Cosa KnowledgeOS non è (perimetro esplicito)

- Non è un servizio cloud SaaS multi-tenant pubblico nella sua prima versione (è on-premise first; il multi-tenant è un requisito architetturale per il futuro, non un obbligo di deployment immediato).
- Non fa fine-tuning del modello linguistico: la conoscenza vive nel livello RAG, non nei pesi.
- Non sostituisce il DMS/sistema di gestione documentale del cliente: si integra a valle di esso come strato di comprensione semantica (nell'MVP, gestisce direttamente upload e lifecycle di base; l'integrazione con DMS esterni è un'estensione futura).
- Non è un chatbot generico "aperto": ogni risposta deve essere ancorata alla base documentale del tenant, con citazione della fonte.

---

## 8. Metriche di successo (indicative, da validare con i primi clienti pilota)

- Tempo medio di risposta a una domanda documentale: da minuti/ore (ricerca manuale) a < 10 secondi.
- Percentuale di risposte con fonte verificabile e pertinente: obiettivo > 90% sulle domande in-domain.
- Riduzione del carico di richieste interne verso personale esperto/senior (misurabile via survey/adozione).
- Numero di documenti e reparti onboardati nel sistema nel tempo (adozione interna).
- Zero incidenti di esfiltrazione dati verso servizi esterni (requisito non negoziabile, non solo una metrica di successo).

---

## Stato del documento

Bozza per revisione — Fase 1/2 del processo di sviluppo. In attesa di approvazione prima di procedere a `02_SOLUTION_ARCHITECTURE.md`.
