package com.lifeos.entity;

import com.lifeos.entity.enums.FocusMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "focus_sessions")
@Getter
@Setter
public class FocusSession extends BaseEntity {

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "task_id", length = 36)
    private String taskId;

    @Column(name = "goal_id", length = 36)
    private String goalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", length = 32, nullable = false)
    private FocusMode mode = FocusMode.POMODORO_25_5;

    @Column(name = "planned_minutes", nullable = false)
    private int plannedMinutes = 25;

    @Column(name = "actual_minutes", nullable = false)
    private int actualMinutes;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "interrupted_count", nullable = false)
    private int interruptedCount;

    @Column(name = "outcome", length = 500)
    private String outcome;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "rating")
    private Integer rating;
}