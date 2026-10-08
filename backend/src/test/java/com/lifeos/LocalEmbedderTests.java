package com.lifeos;

import com.lifeos.ai.provider.LocalEmbedder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The offline provider is the default, so the local embedder is the thing that decides whether semantic
 * retrieval works at all when no API key is configured. These tests pin the numerical behaviour, because a
 * silently degenerate vector looks like "no results found" rather than like an error.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Local embedding numerics")
class LocalEmbedderTests {

    private static final int DIMENSIONS = 384;

    @Autowired
    private LocalEmbedder embedder;

    @Test
    @DisplayName("produces a finite, unit-length vector")
    void producesFiniteUnitVector() {
        float[] vector = embedder.embed("consistent wake time matters more than total hours", DIMENSIONS);

        double sum = 0;
        for (float value : vector) {
            assertThat(Float.isFinite(value)).as("component %s must be finite", value).isTrue();
            sum += (double) value * value;
        }
        assertThat(Math.sqrt(sum)).isCloseTo(1.0d, org.assertj.core.data.Offset.offset(1e-4));
    }

    @Test
    @DisplayName("scores identical text at 1 and unrelated text well below a relevant passage")
    void ranksRelevantTextHighest() {
        String passage = "Consistent wake time matters more than total hours. Anchor the wake time first, "
                + "then let sleep onset follow. Caffeine after midday shortens deep sleep.";
        String query = "caffeine and deep sleep";

        float[] passageVector = embedder.embed(passage, DIMENSIONS);
        float[] queryVector = embedder.embed(query, DIMENSIONS);
        float[] unrelatedVector = embedder.embed(
                "Quarterly revenue rose after the pricing change and the support backlog shrank.", DIMENSIONS);

        double exact = embedder.cosine(queryVector, embedder.embed(query, DIMENSIONS));
        double relevant = embedder.cosine(queryVector, passageVector);
        double unrelated = embedder.cosine(queryVector, unrelatedVector);

        assertThat(exact).isCloseTo(1.0d, org.assertj.core.data.Offset.offset(1e-4));
        assertThat(relevant).as("the passage shares several query terms").isGreaterThan(0.05);
        assertThat(relevant).as("relevant text must outrank unrelated text").isGreaterThan(unrelated);
    }

    @Test
    @DisplayName("handles empty and punctuation-only input without producing NaN")
    void handlesDegenerateInput() {
        for (String text : List.of("", "   ", "!!! ??? ...")) {
            float[] vector = embedder.embed(text, DIMENSIONS);
            for (float value : vector) {
                assertThat(Float.isFinite(value)).as("degenerate input %s produced %s", text, value).isTrue();
            }
        }
    }

    @Test
    @DisplayName("returns no similarity when dimensions differ")
    void refusesMismatchedDimensions() {
        float[] shortVector = embedder.embed("sleep", 64);
        float[] longVector = embedder.embed("sleep", DIMENSIONS);
        assertThat(embedder.cosine(shortVector, longVector)).isZero();
    }
}
