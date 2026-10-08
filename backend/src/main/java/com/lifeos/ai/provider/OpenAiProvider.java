package com.lifeos.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.lifeos.ai.AIProvider;
import com.lifeos.ai.AiMessage;
import com.lifeos.ai.AiRequest;
import com.lifeos.ai.AiResponse;
import com.lifeos.config.AiProperties;
import com.lifeos.exception.AppException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter for any OpenAI-compatible {@code /chat/completions} endpoint, which also covers most
 * self-hosted gateways. Only the base URL and key are configurable.
 */
@Component
public class OpenAiProvider implements AIProvider {

    private final AiProperties properties;
    private final RestClient restClient;

    public OpenAiProvider(AiProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public String displayName() {
        return "OpenAI-compatible API";
    }

    @Override
    public boolean isConfigured() {
        return properties.openai() != null && properties.openai().ready();
    }

    @Override
    public AiResponse complete(AiRequest request) {
        AiProperties.OpenAi config = properties.openai();
        List<Map<String, String>> messages = new ArrayList<>();
        for (AiMessage message : request.messages()) {
            messages.add(Map.of("role", message.role().name().toLowerCase(java.util.Locale.ROOT),
                    "content", message.content()));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        body.put("messages", messages);
        body.put("temperature", request.temperature() == null ? 0.2 : request.temperature());
        if (request.maxOutputTokens() != null) {
            body.put("max_tokens", request.maxOutputTokens());
        }

        JsonNode response = restClient.post()
                .uri(config.baseUrl() + "/chat/completions")
                .header("Authorization", "Bearer " + config.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw AppException.unprocessable("The model returned an empty response");
        }
        JsonNode message = response.path("choices").path(0).path("message");
        String text = message.path("content").asText("");
        return new AiResponse(text.strip(), name(), config.model(),
                response.path("usage").path("prompt_tokens").asInt(0),
                response.path("usage").path("completion_tokens").asInt(0), false);
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        AiProperties.OpenAi config = properties.openai();
        JsonNode response = restClient.post()
                .uri(config.baseUrl() + "/embeddings")
                .header("Authorization", "Bearer " + config.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("model", config.embeddingModel(), "input", texts))
                .retrieve()
                .body(JsonNode.class);

        if (response == null || !response.path("data").isArray()) {
            throw AppException.unprocessable("The embedding endpoint returned no data");
        }
        List<float[]> vectors = new ArrayList<>();
        for (JsonNode item : response.path("data")) {
            JsonNode values = item.path("embedding");
            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vector[i] = (float) values.get(i).asDouble();
            }
            vectors.add(vector);
        }
        return vectors;
    }

    @Override
    public int embeddingDimensions() {
        return 1536;
    }

    @Override
    public String embeddingModel() {
        return properties.openai().embeddingModel();
    }
}