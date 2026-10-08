package com.lifeos.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifeos.ai.AIProvider;
import com.lifeos.ai.AiMessage;
import com.lifeos.ai.AiRequest;
import com.lifeos.ai.AiResponse;
import com.lifeos.config.AiProperties;
import com.lifeos.exception.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter for Google's Gemini REST API. Credentials come only from configuration; the key is sent
 * as a header and is never logged or persisted.
 */
@Component
public class GeminiProvider implements AIProvider {

    private static final Logger log = LoggerFactory.getLogger(GeminiProvider.class);

    private final AiProperties properties;
    private final RestClient restClient;

    public GeminiProvider(AiProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public String displayName() {
        return "Google Gemini";
    }

    @Override
    public boolean isConfigured() {
        return properties.gemini() != null && properties.gemini().ready();
    }

    @Override
    public AiResponse complete(AiRequest request) {
        AiProperties.Gemini config = properties.gemini();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", toContents(request.messages()));
        body.put("generationConfig", Map.of(
                "temperature", request.temperature() == null ? 0.2 : request.temperature(),
                "maxOutputTokens", request.maxOutputTokens() == null ? 1024 : request.maxOutputTokens()));

        JsonNode response = restClient.post()
                .uri(config.baseUrl() + "/models/{model}:generateContent", config.model())
                .header("x-goog-api-key", config.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw AppException.unprocessable("Gemini returned an empty response");
        }
        String text = extractText(response);
        return new AiResponse(text, name(), config.model(), usage(response, "promptTokenCount"),
                usage(response, "candidatesTokenCount"), false);
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        AiProperties.Gemini config = properties.gemini();
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            JsonNode response = restClient.post()
                    .uri(config.baseUrl() + "/models/{model}:embedContent", config.embeddingModel())
                    .header("x-goog-api-key", config.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("content", Map.of("parts", List.of(Map.of("text", text)))))
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null) {
                throw AppException.unprocessable("Gemini returned an empty embedding");
            }
            vectors.add(toVector(response.path("embedding").path("values")));
        }
        return vectors;
    }

    @Override
    public int embeddingDimensions() {
        return 768;
    }

    @Override
    public String embeddingModel() {
        return properties.gemini().embeddingModel();
    }

    private List<Map<String, Object>> toContents(List<AiMessage> messages) {
        List<Map<String, Object>> contents = new ArrayList<>();
        for (AiMessage message : messages) {
            if (message.role() == AiMessage.Role.SYSTEM) {
                continue;
            }
            contents.add(Map.of(
                    "role", message.role() == AiMessage.Role.ASSISTANT ? "model" : "user",
                    "parts", List.of(Map.of("text", message.content()))));
        }
        if (contents.isEmpty() && !messages.isEmpty()) {
            contents.add(Map.of("role", "user",
                    "parts", List.of(Map.of("text", messages.get(messages.size() - 1).content()))));
        }
        return contents;
    }

    private String extractText(JsonNode response) {
        StringBuilder text = new StringBuilder();
        for (JsonNode candidate : response.path("candidates")) {
            for (JsonNode part : candidate.path("content").path("parts")) {
                if (part.has("text")) {
                    text.append(part.get("text").asText());
                }
            }
        }
        if (text.isEmpty()) {
            log.warn("Gemini response contained no text parts");
        }
        return text.toString().strip();
    }

    private int usage(JsonNode response, String field) {
        return response.path("usageMetadata").path(field).asInt(0);
    }

    private float[] toVector(JsonNode values) {
        if (!values.isArray()) {
            throw AppException.unprocessable("Embedding response was not an array");
        }
        float[] vector = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            vector[i] = (float) values.get(i).asDouble();
        }
        return vector;
    }

    Duration timeout() {
        return properties.requestTimeout();
    }
}