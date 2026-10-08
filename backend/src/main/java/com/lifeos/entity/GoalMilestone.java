package com.lifeos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "goal_milestones")
@Getter
@Setter
public class GoalMilestone extends BaseEntity {

    @Column(name = "goal_id", length = 36, nullable = false)
    private String goalId;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "due_date")
    private Instant dueDate;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Column(name = "progress", nullable = false)
    private int progress;

    @Column(name = "position", nullable = false)
    private int position;
}