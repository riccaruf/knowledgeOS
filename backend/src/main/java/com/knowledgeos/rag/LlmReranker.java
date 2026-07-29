package com.knowledgeos.rag;

import com.knowledgeos.knowledge.ChunkSearchResult;
import com.knowledgeos.llm.OllamaChatClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Reranking dei candidati dell'hybrid search (05_RAG_PIPELINE.md §5.4).
 * Implementato come LLM-as-reranker: un'unica chiamata batched al modello
 * chat gia' configurato via Ollama, che assegna un punteggio 0-10 a ciascun
 * candidato numerato, invece di un cross-encoder dedicato (deviazione
 * dichiarata in 05_RAG_PIPELINE.md §5.4 — scelta per evitare nuova
 * infrastruttura di inferenza in questo incremento; componente sostituibile
 * dietro la stessa interfaccia in futuro).
 *
 * La chiamata non deve mai far fallire /api/v1/query: qualunque problema
 * (Ollama non raggiungibile, risposta non parsabile, parsing parziale)
 * degrada silenziosamente all'ordine hybrid in ingresso invece di propagare
 * l'errore.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmReranker {

    private static final double RERANK_TEMPERATURE = 0.0;

    private final OllamaChatClient chatClient;
    private final RetrievalProperties retrievalProperties;

    public List<RankedChunk> rerank(String question, List<ChunkSearchResult> candidates, int finalTopN) {
        Map<Integer, Double> scoresByIndex;
        try {
            String response = chatClient.generate(buildPrompt(question, candidates), RERANK_TEMPERATURE);
            scoresByIndex = LlmRerankResponseParser.parse(response, candidates.size());
            if (scoresByIndex.isEmpty()) {
                log.warn("Reranking LLM: risposta non parsabile, uso l'ordine hybrid come fallback.");
            } else if (scoresByIndex.size() < candidates.size()) {
                log.warn("Reranking LLM: punteggio mancante per {}/{} candidati, uso il fallback posizionale per i mancanti.",
                        candidates.size() - scoresByIndex.size(), candidates.size());
            }
        } catch (Exception e) {
            log.warn("Reranking LLM non disponibile ({}), uso l'ordine hybrid come fallback.", e.getMessage());
            scoresByIndex = Map.of();
        }

        List<RankedChunk> ranked = new ArrayList<>(candidates.size());
        int n = candidates.size();
        for (int i = 0; i < n; i++) {
            int oneBasedIndex = i + 1;
            double score = scoresByIndex.containsKey(oneBasedIndex)
                    ? scoresByIndex.get(oneBasedIndex)
                    : fallbackPositionalScore(i, n);
            ranked.add(new RankedChunk(candidates.get(i), score));
        }

        return ranked.stream()
                .sorted(Comparator.comparingDouble(RankedChunk::rerankScore).reversed())
                .limit(finalTopN)
                .toList();
    }

    private double fallbackPositionalScore(int zeroBasedPosition, int total) {
        return 10.0 * (total - zeroBasedPosition) / total;
    }

    private String buildPrompt(String question, List<ChunkSearchResult> candidates) {
        int excerptChars = retrievalProperties.rerankExcerptChars();
        StringBuilder passages = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            String content = candidates.get(i).content();
            String excerpt = content.length() > excerptChars ? content.substring(0, excerptChars) + "..." : content;
            passages.append("[").append(i + 1).append("] ").append(excerpt).append("\n");
        }

        return """
                Sei un motore di valutazione della pertinenza. Data una DOMANDA e un elenco di PASSAGGI
                numerati, assegna a ciascun passaggio un punteggio intero da 0 (per niente pertinente) a 10
                (perfettamente pertinente) rispetto alla capacita' del passaggio di rispondere alla domanda.

                Rispondi ESCLUSIVAMENTE con una riga per ogni passaggio, nel formato esatto "numero|punteggio",
                un passaggio per riga, senza testo aggiuntivo, intestazioni o spiegazioni.

                DOMANDA: %s

                PASSAGGI:
                %s""".formatted(question, passages.toString().strip());
    }
}
