package com.lifeos.ai;

/** Normalised provider response; callers never see vendor-specific payloads. */
public record AiResponse(
        String text,
        String provider,
        String model,
        int promptTokens,
        int completionTokens,
        boolean degraded
) {
    public static AiResponse local(String text, String model) {
        return new AiResponse(text, "heuristic", model, 0, estimateTokens(text), false);
    }

    private static int estimateTokens(String text) {
        return text == null ? 0 : (int) Math.ceil(text.length() / 4.0);
    }
}