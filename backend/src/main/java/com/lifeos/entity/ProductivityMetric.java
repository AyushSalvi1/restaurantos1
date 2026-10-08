package com.lifeos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** One row per user per day; recomputed nightly and incrementally updated on events. */
@Entity
@Table(name = "productivity_metrics")
@Getter
@Setter
public class ProductivityMetric extends BaseEntity {

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "tasks_created", nullable = false)
    private int tasksCreated;

    @Column(name = "tasks_completed", nullable = false)
    private int tasksCompleted;

    @Column(name = "tasks_missed", nullable = false)
    private int tasksMissed;

    @Column(name = "tasks_planned", nullable = false)
    private int tasksPlanned;

    @Column(name = "focus_minutes", nullable = false)
    private int focusMinutes;

    @Column(name = "study_minutes", nullable = false)
    private int studyMinutes;

    @Column(name = "habits_completed", nullable = false)
    private int habitsCompleted;

    @Column(name = "habits_planned", nullable = false)
    private int habitsPlanned;

    @Column(name = "events_count", nullable = false)
    private int eventsCount;

    @Column(name = "transactions_count", nullable = false)
    private int transactionsCount;

    @Column(name = "planned_minutes", nullable = false)
    private int plannedMinutes;

    @Column(name = "completed_minutes", nullable = false)
    private int completedMinutes;

    @Column(name = "productivity_score", nullable = false)
    private int productivityScore;
}