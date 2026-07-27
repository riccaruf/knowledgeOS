package com.knowledgeos.ingestion.pdf;

import com.knowledgeos.common.exception.UnprocessableDocumentException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Parsing + cleaning di un PDF (05_RAG_PIPELINE.md §2.2/§2.3), in un solo
 * passo per l'MVP: estrazione riga per riga con font size, rimozione
 * header/footer ripetuti, e riconoscimento euristico dei titoli di sezione
 * in base alla dimensione del font rispetto al corpo del testo.
 *
 * L'OCR (per PDF scansionati privi di layer testuale) e' fuori perimetro
 * MVP (07_MVP_ROADMAP.md, Milestone 2): un PDF senza testo estraibile viene
 * rifiutato esplicitamente invece di produrre chunk vuoti.
 */
@Service
public class PdfParsingService {

    private static final double HEADING_FONT_RATIO = 1.15;
    private static final int HEADING_MAX_LENGTH = 140;
    private static final double BOILERPLATE_PAGE_RATIO = 0.4;

    public List<ParsedParagraph> parse(byte[] pdfBytes) {
        List<HeadingAwarePdfTextExtractor.Line> lines;
        int pageCount;
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            pageCount = document.getNumberOfPages();
            lines = HeadingAwarePdfTextExtractor.extract(document);
        } catch (IOException e) {
            throw new UnprocessableDocumentException("Impossibile leggere il PDF: file corrotto o non valido.", e);
        }

        if (lines.isEmpty()) {
            throw new UnprocessableDocumentException(
                    "Nessun testo estraibile dal PDF: probabilmente e' una scansione senza layer testuale. "
                            + "L'OCR sara' disponibile in una versione successiva (Milestone 2).");
        }

        List<HeadingAwarePdfTextExtractor.Line> cleaned = removeBoilerplate(lines, pageCount);
        double bodyFontSize = medianFontSize(cleaned);

        return groupIntoParagraphs(cleaned, bodyFontSize);
    }

    private List<HeadingAwarePdfTextExtractor.Line> removeBoilerplate(
            List<HeadingAwarePdfTextExtractor.Line> lines, int pageCount) {
        if (pageCount < 4) {
            return lines;
        }
        Map<String, Long> distinctPagesPerText = lines.stream()
                .collect(Collectors.groupingBy(HeadingAwarePdfTextExtractor.Line::text,
                        Collectors.mapping(HeadingAwarePdfTextExtractor.Line::page, Collectors.toSet())))
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> (long) e.getValue().size()));

        long threshold = Math.round(pageCount * BOILERPLATE_PAGE_RATIO);
        return lines.stream()
                .filter(line -> distinctPagesPerText.getOrDefault(line.text(), 0L) <= threshold)
                .toList();
    }

    private double medianFontSize(List<HeadingAwarePdfTextExtractor.Line> lines) {
        List<Float> sorted = lines.stream().map(HeadingAwarePdfTextExtractor.Line::maxFontSize).sorted().toList();
        return sorted.get(sorted.size() / 2);
    }

    private List<ParsedParagraph> groupIntoParagraphs(
            List<HeadingAwarePdfTextExtractor.Line> lines, double bodyFontSize) {
        List<ParsedParagraph> paragraphs = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        String currentSection = "Documento";
        int bufferFirstPage = lines.get(0).page();

        for (HeadingAwarePdfTextExtractor.Line line : lines) {
            boolean isHeading = line.maxFontSize() >= bodyFontSize * HEADING_FONT_RATIO
                    && line.text().length() <= HEADING_MAX_LENGTH;
            if (isHeading) {
                flush(paragraphs, buffer, currentSection, bufferFirstPage);
                buffer.setLength(0);
                currentSection = line.text();
            } else {
                if (buffer.isEmpty()) {
                    bufferFirstPage = line.page();
                }
                if (!buffer.isEmpty()) {
                    buffer.append(' ');
                }
                buffer.append(line.text());
            }
        }
        flush(paragraphs, buffer, currentSection, bufferFirstPage);
        return paragraphs;
    }

    private void flush(List<ParsedParagraph> paragraphs, StringBuilder buffer, String section, int page) {
        String text = buffer.toString().strip();
        if (!text.isEmpty()) {
            paragraphs.add(new ParsedParagraph(section, page, text));
        }
    }
}
