package com.lifeos.ai.provider;

import com.lifeos.ai.AIProvider;
import com.lifeos.ai.AiRequest;
import com.lifeos.ai.AiResponse;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Hashed bag-of-words embedding used when no remote embedding model is configured.
 *
 * <p>Tokens are hashed into a fixed number of buckets with sublinear term frequency weighting and
 * L2 normalisation, which gives meaningful lexical similarity for retrieval. It is not a
 * substitute for a trained sentence encoder, and the registry reports that fallback explicitly so
 * the UI can say so rather than pretending semantic quality it does not have.</p>
 */
@Component
public class LocalEmbedder {

    private final LocalTokenizer tokenizer;

    public LocalEmbedder(LocalTokenizer tokenizer) {
        this.tokenizer = tokenizer;
    }

    public float[] embed(String text, int dimensions) {
        float[] vector = new float[dimensions];
        List<String> tokens = tokenizer.tokenize(text);
        if (tokens.isEmpty()) {
            return vector;
        }
        for (String token : tokens) {
            int primary = Math.floorMod(token.hashCode(), dimensions);
            vector[primary] += 1.0f;
            // A second, independent bucket reduces collisions between unrelated tokens.
            int secondary = Math.floorMod((token.hashCode() * 31) + 17, dimensions);
            if (secondary != primary) {
                vector[secondary] += 0.5f;
            }
        }
        for (int i = 0; i < dimensions; i++) {
            // Sublinear term frequency, applied only to occupied buckets. Unused buckets must stay zero:
            // log(0) is -Infinity, which would make the whole vector NaN after normalisation and silently
            // disable every retrieval path.
            if (vector[i] > 0) {
                vector[i] = (float) (1.0 + Math.log(vector[i]));
            }
        }
        normalise(vector);
        return vector;
    }

    public List<float[]> embedAll(List<String> texts, int dimensions) {
        return texts.stream().map(text -> embed(text, dimensions)).toList();
    }

    public void normalise(float[] vector) {
        double sum = 0;
        for (float value : vector) {
            sum += (double) value * value;
        }
        if (sum <= 0) {
            return;
        }
        double magnitude = Math.sqrt(sum);
        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float) (vector[i] / magnitude);
        }
    }

    public double cosine(float[] a, float[] b) {
        if (a.length != b.length) {
            return 0;
        }
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
        }
        return dot;
    }

    /** True when the provider produced embeddings locally rather than calling a hosted model. */
    public boolean isFallback(AIProvider provider) {
        return provider instanceof HeuristicProvider;
    }
}