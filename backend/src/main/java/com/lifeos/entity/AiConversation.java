package com.lifeos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "ai_conversations")
@Getter
@Setter
public class AiConversation extends BaseEntity {

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "title", length = 200, nullable = false)
    private String title = "New conversation";

    @Column(name = "message_count", nullable = false)
    private int messageCount;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}