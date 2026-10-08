package com.lifeos.ai;

import java.util.List;

/** A single provider completion request. Never carries credentials - those stay in configuration. */
public record AiRequest(
        List<AiMessage> messages,
        Double temperature,
        Integer maxOutputTokens
) {
    public AiRequest {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }

    public static AiRequest of(List<AiMessage> messages) {
        return new AiRequest(messages, 0.2, 1024);
    }
}