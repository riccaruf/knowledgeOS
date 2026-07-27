package com.knowledgeos.rag;

import com.knowledgeos.knowledge.ChunkSearchResult;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Assembla il contesto per l'LLM a partire dai chunk recuperati
 * (05_RAG_PIPELINE.md §6). Semplificazione MVP dichiarata nel piano di
 * implementazione: nessuna espansione a capitolo intero, nessuna
 * deduplicazione avanzata — ogni chunk selezionato viene incluso cosi'
 * com'e', numerato per permettere all'LLM (e al mapping verso le fonti)
 * di riferirsi ad esso in modo univoco.
 */
@Component
public class ContextBuilder {

    public String build(List<ChunkSearchResult> chunks) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            ChunkSearchResult chunk = chunks.get(i);
            sb.append("[Fonte ").append(i + 1).append("] ")
                    .append(chunk.documentTitle())
                    .append(" — ").append(chunk.section() != null ? chunk.section() : "N/D")
                    .append(" — pag. ").append(chunk.page())
                    .append("\n")
                    .append(chunk.content())
                    .append("\n\n");
        }
        return sb.toString().strip();
    }
}
