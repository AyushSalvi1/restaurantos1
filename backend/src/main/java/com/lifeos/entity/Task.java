package com.lifeos.entity;

import com.lifeos.entity.enums.Difficulty;
import com.lifeos.entity.enums.EnergyRequirement;
import com.lifeos.entity.enums.Priority;
import com.lifeos.entity.enums.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "tasks")
@Getter
@Setter
public class Task extends BaseEntity {

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 24, nullable = false)
    private TaskStatus status = TaskStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 16, nullable = false)
    private Priority priority = Priority.MEDIUM;

    @Column(name = "category", length = 48)
    private String category;

    @Column(name = "deadline")
    private Instant deadline;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Column(name = "actual_minutes", nullable = false)
    private int actualMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "difficulty", length = 16)
    private Difficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "energy_requirement", length = 16)
    private EnergyRequirement energyRequirement;

    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;

    @Column(name = "goal_id", length = 36)
    private String goalId;

    @Column(name = "milestone_id", length = 36)
    private String milestoneId;

    @Column(name = "project_id", length = 36)
    private String projectId;

    @Column(name = "recurrence_rule", length = 120)
    private String recurrenceRule;

    @Column(name = "recurrence_parent_id", length = 36)
    private String recurrenceParentId;

    @Column(name = "recurrence_end_date")
    private Instant recurrenceEndDate;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}