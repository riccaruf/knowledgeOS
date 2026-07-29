package com.knowledgeos.rag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parsing dell'output del reranker LLM-as-reranker (05_RAG_PIPELINE.md §5.4):
 * una riga per candidato nel formato rigido "indice|punteggio". Righe non
 * conformi (commenti, intestazioni, testo libero che il modello aggiunge
 * nonostante l'istruzione) vengono ignorate invece di far fallire il parsing;
 * un indice duplicato mantiene il primo punteggio incontrato. Non lancia mai
 * eccezioni: una risposta interamente malformata produce semplicemente una
 * mappa vuota, lasciando a LlmReranker la responsabilita' del fallback.
 */
public final class LlmRerankResponseParser {

    private static final Pattern LINE_PATTERN = Pattern.compile("^\\s*(\\d+)\\s*[|:]\\s*(-?\\d+(?:\\.\\d+)?)\\s*$");

    private LlmRerankResponseParser() {
    }

    public static Map<Integer, Double> parse(String llmResponse, int expectedCount) {
        Map<Integer, Double> scoresByIndex = new LinkedHashMap<>();
        if (llmResponse == null || llmResponse.isBlank()) {
            return scoresByIndex;
        }

        for (String line : llmResponse.split("\\R")) {
            Matcher matcher = LINE_PATTERN.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            int index = Integer.parseInt(matcher.group(1));
            if (index < 1 || index > expectedCount) {
                continue;
            }
            double score = Math.max(0.0, Math.min(10.0, Double.parseDouble(matcher.group(2))));
            scoresByIndex.putIfAbsent(index, score);
        }

        return scoresByIndex;
    }
}
