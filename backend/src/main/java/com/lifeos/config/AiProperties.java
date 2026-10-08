package com.lifeos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "lifeos.ai")
public record AiProperties(
        Provider provider,
        Duration requestTimeout,
        Gemini gemini,
        OpenAi openai,
        Ollama ollama
) {

    public enum Provider {
        GEMINI,
        OPENAI,
        OLLAMA,
        HEURISTIC
    }

    public record Gemini(String baseUrl, String apiKey, String model, String embeddingModel) {
        public boolean ready() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record OpenAi(String baseUrl, String apiKey, String model, String embeddingModel) {
        public boolean ready() {
            return apiKey != null && !apiKey.isBlank();
        }
    }

    public record Ollama(String baseUrl, String model, String embeddingModel) {
        public boolean ready() {
            return baseUrl != null && !baseUrl.isBlank();
        }
    }
}