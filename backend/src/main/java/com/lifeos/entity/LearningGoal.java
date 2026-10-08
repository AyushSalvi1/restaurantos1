package com.lifeos.entity;

import com.lifeos.entity.enums.LearningStatus;
import com.lifeos.entity.enums.Origin;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "learning_goals")
@Getter
@Setter
public class LearningGoal extends BaseEntity {

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", length = 48)
    private String category;

    @Column(name = "target_date")
    private Instant targetDate;

    @Column(name = "progress", nullable = false)
    private int progress;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 24, nullable = false)
    private LearningStatus status = LearningStatus.ACTIVE;

    @Column(name = "hours_spent", nullable = false)
    private BigDecimal hoursSpent = BigDecimal.ZERO;

    @Column(name = "skill_id", length = 36)
    private String skillId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 16, nullable = false)
    private Origin source = Origin.USER;

    @Column(name = "ai_confirmed", nullable = false)
    private boolean aiConfirmed = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}