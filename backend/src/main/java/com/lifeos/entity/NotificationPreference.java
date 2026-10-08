package com.lifeos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
public class NotificationPreference extends BaseEntity {

    @Column(name = "user_id", length = 36, nullable = false, unique = true)
    private String userId;

    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = false;

    @Column(name = "task_enabled", nullable = false)
    private boolean taskEnabled = true;

    @Column(name = "goal_enabled", nullable = false)
    private boolean goalEnabled = true;

    @Column(name = "habit_enabled", nullable = false)
    private boolean habitEnabled = true;

    @Column(name = "calendar_enabled", nullable = false)
    private boolean calendarEnabled = true;

    @Column(name = "finance_enabled", nullable = false)
    private boolean financeEnabled = false;

    @Column(name = "learning_enabled", nullable = false)
    private boolean learningEnabled = true;

    @Column(name = "ai_insight_enabled", nullable = false)
    private boolean aiInsightEnabled = true;

    @Column(name = "reminder_enabled", nullable = false)
    private boolean reminderEnabled = true;

    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;

    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;
}