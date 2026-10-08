package com.lifeos.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits extracted text into retrieval chunks on paragraph boundaries with a sliding window,
 * so a chunk never starts or ends mid-sentence where avoidable.
 */
@Component
public class TextChunker {

    private static final int DEFAULT_CHUNK_CHARACTERS = 1200;
    private static final int DEFAULT_OVERLAP_CHARACTERS = 180;

    public List<String> chunk(String text) {
        return chunk(text, DEFAULT_CHUNK_CHARACTERS, DEFAULT_OVERLAP_CHARACTERS);
    }

    public List<String> chunk(String text, int targetCharacters, int overlapCharacters) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        String[] paragraphs = text.split("\\R\\s*\\R");
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (current.length() + trimmed.length() + 2 > targetCharacters && current.length() > 0) {
                chunks.add(current.toString().strip());
                current = new StringBuilder(tail(current.toString(), overlapCharacters));
                current.append('\n');
            }
            if (trimmed.length() > targetCharacters) {
                // A single very long paragraph is split on sentence boundaries instead.
                appendSplitBySentence(current, chunks, trimmed, targetCharacters);
                continue;
            }
            current.append(trimmed).append("\n\n");
        }
        if (!current.isEmpty() && !current.toString().isBlank()) {
            chunks.add(current.toString().strip());
        }
        return chunks;
    }

    private void appendSplitBySentence(StringBuilder current, List<String> chunks, String paragraph,
                                       int targetCharacters) {
        String[] sentences = paragraph.split("(?<=[.!?])\\s+");
        StringBuilder buffer = new StringBuilder();
        for (String sentence : sentences) {
            if (buffer.length() + sentence.length() + 1 > targetCharacters && buffer.length() > 0) {
                chunks.add(buffer.toString().strip());
                buffer.setLength(0);
            }
            buffer.append(sentence).append(' ');
        }
        if (!buffer.isEmpty()) {
            chunks.add(buffer.toString().strip());
        }
    }

    private String tail(String text, int overlap) {
        if (overlap <= 0 || text.length() <= overlap) {
            return "";
        }
        return text.substring(text.length() - overlap);
    }

    /** Rough token estimate used only for display and budgeting. */
    public int estimateTokens(String text) {
        return text == null ? 0 : (int) Math.ceil(text.length() / 4.0);
    }
}