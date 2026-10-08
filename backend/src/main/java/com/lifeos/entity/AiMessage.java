package com.lifeos.entity;

import com.lifeos.entity.enums.MessageRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ai_messages")
@Getter
@Setter
public class AiMessage extends CreatedEntity {

    @Column(name = "conversation_id", length = 36, nullable = false)
    private String conversationId;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 16, nullable = false)
    private MessageRole role;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "provider", length = 32)
    private String provider;

    @Column(name = "model", length = 80)
    private String model;

    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private int completionTokens;

    /** JSON array of {@code {documentId, documentTitle, chunkIndex, snippet}} entries. */
    @Column(name = "citations", columnDefinition = "TEXT")
    private String citations;
}