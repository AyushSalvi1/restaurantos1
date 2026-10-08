package com.lifeos.ai;

import java.util.List;

/** One turn in a conversation handed to a provider. */
public record AiMessage(Role role, String content) {

    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT
    }

    public static AiMessage system(String content) {
        return new AiMessage(Role.SYSTEM, content);
    }

    public static AiMessage user(String content) {
        return new AiMessage(Role.USER, content);
    }

    public static AiMessage assistant(String content) {
        return new AiMessage(Role.ASSISTANT, content);
    }

    public static List<AiMessage> of(AiMessage... messages) {
        return List.of(messages);
    }
}