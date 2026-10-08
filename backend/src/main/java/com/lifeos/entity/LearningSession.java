package com.lifeos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "learning_sessions")
@Getter
@Setter
public class LearningSession extends CreatedEntity {

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "learning_goal_id", length = 36)
    private String learningGoalId;

    @Column(name = "topic_id", length = 36)
    private String topicId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "minutes", nullable = false)
    private int minutes;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "quiz_score", precision = 5, scale = 2)
    private BigDecimal quizScore;
}