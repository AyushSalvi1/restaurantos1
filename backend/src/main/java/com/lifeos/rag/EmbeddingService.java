package com.lifeos.rag;

import com.lifeos.ai.AIProvider;
import com.lifeos.ai.AiProviderRegistry;
import com.lifeos.ai.provider.LocalEmbedder;
import com.lifeos.exception.AppException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Produces embeddings through the configured provider and serialises them for storage.
 *
 * <p>A remote provider that fails is not silently downgraded per call: the registry decides the
 * active provider once, and a failure is surfaced as an error so partial, mismatched vectors never
 * end up in the store.</p>
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final AiProviderRegistry registry;
    private final LocalEmbedder localEmbedder;
    private final ObjectMapper objectMapper;

    public EmbeddingService(AiProviderRegistry registry, LocalEmbedder localEmbedder, ObjectMapper objectMapper) {
        this.registry = registry;
        this.localEmbedder = localEmbedder;
        this.objectMapper = objectMapper;
    }

    public List<float[]> embed(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        AIProvider provider = registry.active();
        List<float[]> vectors;
        try {
            vectors = provider.embed(texts);
        } catch (RuntimeException ex) {
            if (provider instanceof com.lifeos.ai.provider.HeuristicProvider) {
                throw ex;
            }
            log.warn("Embedding provider {} failed ({}); retrying on-device", provider.name(), ex.getMessage());
            vectors = localEmbedder.embedAll(texts, registry.embeddingDimensions());
        }
        vectors.forEach(localEmbedder::normalise);
        return vectors;
    }

    public String model() {
        return registry.embeddingModel();
    }

    public int dimensions() {
        return registry.embeddingDimensions();
    }

    public boolean isLocal() {
        return registry.isLocalEmbedding();
    }

    public String serialise(float[] vector) {
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (JsonProcessingException ex) {
            throw AppException.unprocessable("Unable to store the document embedding");
        }
    }

    public float[] deserialise(String json) {
        if (json == null || json.isBlank()) {
            return new float[0];
        }
        try {
            float[] vector = objectMapper.readValue(json, float[].class);
            return vector == null ? new float[0] : vector;
        } catch (JsonProcessingException ex) {
            log.warn("Discarding unreadable stored embedding");
            return new float[0];
        }
    }
}