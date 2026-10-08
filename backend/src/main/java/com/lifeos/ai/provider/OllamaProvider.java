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

/** Adapter for a local Ollama daemon. Keeps private data on the machine running LIFEOS. */
@Component
public class OllamaProvider implements AIProvider {

    private final AiProperties properties;
    private final RestClient restClient;

    public OllamaProvider(AiProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.restClient = builder.build();
    }

    @Override
    public String name() {
        return "ollama";
    }

    @Override
    public String displayName() {
        return "Ollama (local models)";
    }

    @Override
    public boolean isConfigured() {
        return properties.ollama() != null && properties.ollama().ready();
    }

    @Override
    public AiResponse complete(AiRequest request) {
        AiProperties.Ollama config = properties.ollama();
        List<Map<String, String>> messages = new ArrayList<>();
        for (AiMessage message : request.messages()) {
            messages.add(Map.of("role", message.role().name().toLowerCase(java.util.Locale.ROOT),
                    "content", message.content()));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.model());
        body.put("messages", messages);
        body.put("stream", false);
        body.put("options", Map.of(
                "temperature", request.temperature() == null ? 0.2 : request.temperature(),
                "num_predict", request.maxOutputTokens() == null ? 1024 : request.maxOutputTokens()));

        JsonNode response = restClient.post()
                .uri(config.baseUrl() + "/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        if (response == null) {
            throw AppException.unprocessable("Ollama returned an empty response");
        }
        String text = response.path("message").path("content").asText("");
        return new AiResponse(text.strip(), name(), config.model(),
                response.path("prompt_eval_count").asInt(0),
                response.path("eval_count").asInt(0), false);
    }

    @Override
    public List<float[]> embed(List<String> texts) {
        AiProperties.Ollama config = properties.ollama();
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            JsonNode response = restClient.post()
                    .uri(config.baseUrl() + "/api/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", config.embeddingModel(), "prompt", text))
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode values = response == null ? null : response.path("embedding");
            if (values == null || !values.isArray()) {
                throw AppException.unprocessable("Ollama returned an empty embedding");
            }
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
        return 768;
    }

    @Override
    public String embeddingModel() {
        return properties.ollama().embeddingModel();
    }
}