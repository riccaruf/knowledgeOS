package com.knowledgeos.ingestion.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Estrae il testo di un PDF riga per riga, annotando per ciascuna riga la
 * pagina e la dimensione massima del font — usata come euristica per
 * distinguere titoli/sezioni dal corpo del testo (05_RAG_PIPELINE.md §3.2),
 * dato che un PDF (a differenza di un .docx) non porta una gerarchia di
 * titoli nativa.
 */
public class HeadingAwarePdfTextExtractor extends PDFTextStripper {

    public record Line(int page, float maxFontSize, String text) {}

    private final List<Line> lines = new ArrayList<>();

    public HeadingAwarePdfTextExtractor() throws IOException {
        super();
        setSortByPosition(true);
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) {
        String trimmed = text.strip();
        if (trimmed.isEmpty()) {
            return;
        }
        float maxSize = 0f;
        for (TextPosition position : textPositions) {
            maxSize = Math.max(maxSize, position.getFontSizeInPt());
        }
        lines.add(new Line(getCurrentPageNo(), maxSize, trimmed));
    }

    public static List<Line> extract(PDDocument document) throws IOException {
        HeadingAwarePdfTextExtractor extractor = new HeadingAwarePdfTextExtractor();
        extractor.getText(document);
        return extractor.lines;
    }
}
