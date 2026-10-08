package com.lifeos.ai.provider;

import com.lifeos.ai.AiMessage;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Deterministic tokeniser shared by the local embedding model and the local answer composer.
 * Kept tiny and dependency free so LIFEOS works fully offline.
 */
@Component
public class LocalTokenizer {

    private static final java.util.Set<String> STOP_WORDS = java.util.Set.of(
            "a", "an", "the", "and", "or", "but", "if", "then", "than", "so", "of", "to", "in", "on", "at",
            "by", "for", "with", "from", "as", "is", "are", "was", "were", "be", "been", "being", "it", "its",
            "this", "that", "these", "those", "i", "me", "my", "we", "our", "you", "your", "he", "she", "they",
            "do", "does", "did", "have", "has", "had", "will", "would", "can", "could", "should", "not", "no",
            "yes", "up", "out", "about", "into", "over", "after", "before", "how", "what", "when", "where",
            "which", "who", "whom", "why", "all", "any", "both", "each", "few", "more", "most", "other", "some",
            "such", "only", "own", "same", "too", "very", "just", "now");

    public java.util.List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return java.util.List.of();
        }
        java.util.List<String> tokens = new java.util.ArrayList<>();
        for (String raw : text.toLowerCase(Locale.ROOT).split("[^\\p{IsAlnum}]+")) {
            if (raw.isEmpty()) {
                continue;
            }
            if (STOP_WORDS.contains(raw)) {
                continue;
            }
            tokens.add(raw.length() > 3 ? stem(raw) : raw);
        }
        return tokens;
    }

    /** Extremely small suffix stripper: enough to align "studies" with "study". */
    private String stem(String token) {
        for (String suffix : new String[]{"ing", "edly", "ies", "es", "ed", "ly", "s"}) {
            if (token.length() > suffix.length() + 2 && token.endsWith(suffix)) {
                String base = token.substring(0, token.length() - suffix.length());
                return suffix.equals("ies") ? base + "y" : base;
            }
        }
        return token;
    }

    /** Joins messages into a single block used by the local answer composer. */
    public String flatten(java.util.List<AiMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        for (AiMessage message : messages) {
            if (message != null && message.content() != null) {
                text.append(message.content()).append('\n');
            }
        }
        return text.toString();
    }
}