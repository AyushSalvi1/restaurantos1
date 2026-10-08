package com.lifeos.entity;

import com.lifeos.entity.enums.GoalStatus;
import com.lifeos.entity.enums.GoalType;
import com.lifeos.entity.enums.Origin;
import com.lifeos.entity.enums.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "goals")
@Getter
@Setter
public class Goal extends BaseEntity {

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "parent_goal_id", length = 36)
    private String parentGoalId;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", length = 48)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "goal_type", length = 24, nullable = false)
    private GoalType goalType = GoalType.LONG_TERM;

    @Column(name = "target_date")
    private Instant targetDate;

    @Column(name = "progress", nullable = false)
    private int progress;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 16, nullable = false)
    private Priority priority = Priority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 24, nullable = false)
    private GoalStatus status = GoalStatus.ACTIVE;

    @Column(name = "color", length = 16)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 16, nullable = false)
    private Origin source = Origin.USER;

    @Column(name = "ai_confirmed", nullable = false)
    private boolean aiConfirmed = true;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}