package com.knowledgeos.rag;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LlmRerankResponseParserTest {

    @Test
    void parsesWellFormedResponse() {
        String response = "1|8\n2|3.5\n3|10";

        Map<Integer, Double> scores = LlmRerankResponseParser.parse(response, 3);

        assertThat(scores).containsExactly(
                Map.entry(1, 8.0), Map.entry(2, 3.5), Map.entry(3, 10.0));
    }

    @Test
    void acceptsColonAsSeparatorToo() {
        Map<Integer, Double> scores = LlmRerankResponseParser.parse("1:7", 1);

        assertThat(scores).containsEntry(1, 7.0);
    }

    @Test
    void ignoresExtraProseAroundValidLines() {
        String response = "Ecco i punteggi:\n1|8\nqualche commento\n2|4\nGrazie";

        Map<Integer, Double> scores = LlmRerankResponseParser.parse(response, 2);

        assertThat(scores).containsExactly(Map.entry(1, 8.0), Map.entry(2, 4.0));
    }

    @Test
    void clampsOutOfRangeScores() {
        Map<Integer, Double> scores = LlmRerankResponseParser.parse("1|15\n2|-3", 2);

        assertThat(scores).containsExactly(Map.entry(1, 10.0), Map.entry(2, 0.0));
    }

    @Test
    void ignoresIndicesOutsideExpectedRange() {
        Map<Integer, Double> scores = LlmRerankResponseParser.parse("1|5\n7|9", 2);

        assertThat(scores).containsOnlyKeys(1);
    }

    @Test
    void firstOccurrenceWinsOnDuplicateIndex() {
        Map<Integer, Double> scores = LlmRerankResponseParser.parse("1|5\n1|9", 1);

        assertThat(scores).containsExactly(Map.entry(1, 5.0));
    }

    @Test
    void emptyResponseYieldsEmptyMap() {
        assertThat(LlmRerankResponseParser.parse("", 5)).isEmpty();
        assertThat(LlmRerankResponseParser.parse(null, 5)).isEmpty();
    }

    @Test
    void completelyUnparseableResponseYieldsEmptyMap() {
        assertThat(LlmRerankResponseParser.parse("Non sono in grado di valutare questi passaggi.", 5)).isEmpty();
    }
}
