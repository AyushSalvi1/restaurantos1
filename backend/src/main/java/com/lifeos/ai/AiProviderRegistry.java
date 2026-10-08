package com.lifeos.ai;

import com.lifeos.ai.provider.GeminiProvider;
import com.lifeos.ai.provider.HeuristicProvider;
import com.lifeos.ai.provider.OllamaProvider;
import com.lifeos.ai.provider.OpenAiProvider;
import com.lifeos.config.AiProperties;
import com.lifeos.config.VectorProperties;
import com.lifeos.dto.AiDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the active {@link AIProvider} from configuration and degrades gracefully.
 *
 * <p>If the configured provider is missing credentials, LIFEOS falls back to the on-device
 * composer instead of failing. The fallback is reported to callers so the UI can be honest about
 * which engine answered.</p>
 */
@Service
public class AiProviderRegistry {

    private static final Logger log = LoggerFactory.getLogger(AiProviderRegistry.class);

    private final AiProperties properties;
    private final VectorProperties vectorProperties;
    private final GeminiProvider geminiProvider;
    private final OpenAiProvider openAiProvider;
    private final OllamaProvider ollamaProvider;
    private final HeuristicProvider heuristicProvider;

    public AiProviderRegistry(AiProperties properties,
                              VectorProperties vectorProperties,
                              GeminiProvider geminiProvider,
                              OpenAiProvider openAiProvider,
                              OllamaProvider ollamaProvider,
                              HeuristicProvider heuristicProvider) {
        this.properties = properties;
        this.vectorProperties = vectorProperties;
        this.geminiProvider = geminiProvider;
        this.openAiProvider = openAiProvider;
        this.ollamaProvider = ollamaProvider;
        this.heuristicProvider = heuristicProvider;
    }

    public AIProvider active() {
        return resolve(properties.provider());
    }

    /** Resolves a specific provider, falling back to the on-device composer when unavailable. */
    public AIProvider resolve(AiProperties.Provider requested) {
        AIProvider provider = switch (requested == null ? AiProperties.Provider.HEURISTIC : requested) {
            case GEMINI -> geminiProvider;
            case OPENAI -> openAiProvider;
            case OLLAMA -> ollamaProvider;
            case HEURISTIC -> heuristicProvider;
        };
        if (!provider.isConfigured()) {
            log.info("AI provider '{}' is not configured; using the on-device composer", provider.name());
            return heuristicProvider;
        }
        return provider;
    }

    public List<AIProvider> all() {
        return List.of(geminiProvider, openAiProvider, ollamaProvider, heuristicProvider);
    }

    public boolean isDegraded() {
        return active() instanceof HeuristicProvider
                && properties.provider() != AiProperties.Provider.HEURISTIC;
    }

    public boolean isLocalEmbedding() {
        return active() instanceof HeuristicProvider;
    }

    public AiDtos.ProviderStatus status() {
        AIProvider activeProvider = active();
        List<AiDtos.ProviderStatus.ProviderAvailability> availability = new ArrayList<>();
        for (AIProvider provider : all()) {
            availability.add(new AiDtos.ProviderStatus.ProviderAvailability(provider.displayName(), provider.isConfigured(),
                    provider.isConfigured() ? "Ready" : "Missing credentials, falling back on-device"));
        }
        return new AiDtos.ProviderStatus(
                properties.provider() == null ? "heuristic" : properties.provider().name().toLowerCase(),
                activeProvider.name(),
                !(activeProvider instanceof HeuristicProvider),
                isDegraded(),
                availability,
                isDegraded()
                        ? "The configured provider is missing credentials, so LIFEOS is reporting stored data only."
                        : "Answering with " + activeProvider.displayName());
    }

    public int embeddingDimensions() {
        AIProvider provider = active();
        if (provider instanceof HeuristicProvider) {
            return vectorProperties.dimensions();
        }
        return provider.embeddingDimensions();
    }

    public String embeddingModel() {
        return active().embeddingModel();
    }
}