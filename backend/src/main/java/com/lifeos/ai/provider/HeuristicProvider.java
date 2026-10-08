package com.lifeos.ai.provider;

import com.lifeos.ai.AIProvider;
import com.lifeos.ai.AiRequest;
import com.lifeos.ai.AiResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Fully local provider. LIFEOS never requires an API key to function: when no hosted model is
 * configured, answers are composed deterministically from the structured context that the
 * assistant layer supplies, and embeddings fall back to {@link LocalEmbedder}.
 *
 * <p>It explicitly refuses to invent facts: it only reformats context it was given.</p>
 */
@Component
public class HeuristicProvider implements AIProvider {

    private final LocalEmbedder localEmbedder;
    private final int dimensions;

    public HeuristicProvider(LocalEmbedder localEmbedder,
                            com.lifeos.config.VectorProperties vectorProperties) {
        this.localEmbedder = localEmbedder;
        this.dimensions = vectorProperties.dimensions();
    }

    @Override
    public String name() {
        return "heuristic";
    }

    @Override
    public String displayName() {
        return "On-device composer (no external AI service)";
    }

    @Override
    public boolean isConfigured() {
        return true;
    }

    @Override
    public AiResponse complete(AiRequest request) {
        String system = "";
        StringBuilder user = new StringBuilder();
        for (com.lifeos.ai.AiMessage message : request.messages()) {
            if (message.role() == com.lifeos.ai.AiMessage.Role.SYSTEM) {
                system = message.content();
            } else if (message.role() == com.lifeos.ai.AiMessage.Role.USER) {
                user.append(message.content()).append('\n');
            }
        }
        String answer = compose(system, user.toString());
        return new AiResponse(answer, name(), "local-composer", 0, answer.length() / 4, false);
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(localEmbedder.embed(text, dimensions));
        }
        return vectors;
    }

    @Override
    public int embeddingDimensions() {
        return dimensions;
    }

    @Override
    public String embeddingModel() {
        return "lifeos-hashing-v1";
    }

    /**
     * Reorders the supplied context lines into a readable digest and echoes the request, making it
     * obvious to the user that the reply is derived from their own stored data.
     */
    private String compose(String systemPrompt, String userPrompt) {
        StringBuilder answer = new StringBuilder();
        List<String> bullets = new ArrayList<>();
        for (String line : userPrompt.split("\\R")) {
            String trimmed = line.strip();
            if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
                bullets.add(trimmed.substring(2));
            }
        }
        if (!bullets.isEmpty()) {
            answer.append("Here is what your LIFEOS data shows:\n\n");
            bullets.stream().limit(20).forEach(bullet -> answer.append("- ").append(bullet).append('\n'));
            answer.append('\n');
        }
        answer.append("LIFEOS is currently running with the on-device composer, which reports stored data ")
                .append("rather than generating new advice. Configure GEMINI_API_KEY, OPENAI_API_KEY or ")
                .append("OLLAMA_BASE_URL to enable a hosted model for richer reasoning and summarisation.");
        return answer.toString().strip();
    }
}