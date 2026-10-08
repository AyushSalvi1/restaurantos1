package com.lifeos.ai;

import java.util.List;

/**
 * Contract every AI backend implements. Selecting a provider is purely configuration driven
 * ({@code AI_PROVIDER}), which keeps Gemini, OpenAI-compatible endpoints and local Ollama
 * interchangeable without touching business code.
 */
public interface AIProvider {

    /** Stable identifier persisted alongside stored messages, e.g. {@code gemini}. */
    String name();

    /** Human readable label for the admin configuration screen. */
    String displayName();

    /** False when credentials are missing, in which case the registry falls back to the local provider. */
    boolean isConfigured();

    AiResponse complete(AiRequest request);

    /**
     * Embeds a batch of texts. Each returned vector is L2-normalised so cosine similarity is a
     * plain dot product in the vector store.
     */
    List<float[]> embed(List<String> texts);

    /** Dimensionality of the vectors {@link #embed} produces. */
    int embeddingDimensions();

    /** Vector model identifier recorded next to each stored embedding. */
    String embeddingModel();
}