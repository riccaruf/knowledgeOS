package com.knowledgeos.ingestion;

import com.knowledgeos.ingestion.pdf.ParsedParagraph;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Semantic chunking strutturato (05_RAG_PIPELINE.md §3.2): i confini di
 * sezione individuati in fase di parsing sono la prima frontiera di taglio;
 * solo all'interno di una sezione troppo lunga si applica una sotto-divisione
 * per soglia dimensionale, senza mai spezzare un paragrafo a meta' a meno che
 * il paragrafo stesso superi la soglia massima.
 *
 * Semplificazione MVP dichiarata nel piano di implementazione: la rottura per
 * similarita' semantica fine (embedding-based) all'interno di una sezione
 * lunga non e' implementata in questo giro; si usa una soglia di token
 * (approssimata a conteggio parole) — raffinamento rimandato a Milestone 2.
 */
@Service
@RequiredArgsConstructor
public class ChunkingService {

    private final IngestionProperties properties;

    public List<ChunkDraft> chunk(String documentTitle, List<ParsedParagraph> paragraphs) {
        List<ChunkDraft> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        int bufferTokens = 0;
        int bufferPage = 0;
        String currentSection = null;

        for (ParsedParagraph paragraph : paragraphs) {
            boolean newSection = currentSection != null && !currentSection.equals(paragraph.section());
            if (newSection) {
                flush(chunks, documentTitle, currentSection, bufferPage, buffer);
                buffer.setLength(0);
                bufferTokens = 0;
            }
            currentSection = paragraph.section();

            int paragraphTokens = countTokens(paragraph.text());

            if (paragraphTokens > properties.chunkMaxTokens()) {
                flush(chunks, documentTitle, currentSection, bufferPage, buffer);
                buffer.setLength(0);
                bufferTokens = 0;
                chunks.addAll(hardSplit(documentTitle, currentSection, paragraph, chunks.size()));
                continue;
            }

            if (bufferTokens > 0 && bufferTokens + paragraphTokens > properties.chunkTargetTokens()) {
                flush(chunks, documentTitle, currentSection, bufferPage, buffer);
                buffer.setLength(0);
                bufferTokens = 0;
            }

            if (buffer.isEmpty()) {
                bufferPage = paragraph.page();
            } else {
                buffer.append("\n\n");
            }
            buffer.append(paragraph.text());
            bufferTokens += paragraphTokens;
        }
        flush(chunks, documentTitle, currentSection, bufferPage, buffer);

        return renumber(chunks);
    }

    private List<ChunkDraft> hardSplit(String title, String section, ParsedParagraph paragraph, int startIndex) {
        String[] words = paragraph.text().split("\\s+");
        List<ChunkDraft> drafts = new ArrayList<>();
        StringBuilder part = new StringBuilder();
        int count = 0;
        for (String word : words) {
            part.append(word).append(' ');
            count++;
            if (count >= properties.chunkTargetTokens()) {
                drafts.add(new ChunkDraft(title, section, paragraph.page(), part.toString().strip(), startIndex + drafts.size()));
                part.setLength(0);
                count = 0;
            }
        }
        if (!part.isEmpty()) {
            drafts.add(new ChunkDraft(title, section, paragraph.page(), part.toString().strip(), startIndex + drafts.size()));
        }
        return drafts;
    }

    private void flush(List<ChunkDraft> chunks, String title, String section, int page, StringBuilder buffer) {
        String content = buffer.toString().strip();
        if (!content.isEmpty()) {
            chunks.add(new ChunkDraft(title, section, page, content, chunks.size()));
        }
    }

    private List<ChunkDraft> renumber(List<ChunkDraft> chunks) {
        List<ChunkDraft> result = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            ChunkDraft c = chunks.get(i);
            result.add(new ChunkDraft(c.title(), c.section(), c.page(), c.content(), i));
        }
        return result;
    }

    private int countTokens(String text) {
        return text.isBlank() ? 0 : text.trim().split("\\s+").length;
    }
}
