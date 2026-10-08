package com.lifeos.entity;

import com.lifeos.entity.enums.ProductivityStyle;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;

/** Onboarding answers and personalisation inputs for the planning engine. */
@Entity
@Table(name = "user_preferences")
@Getter
@Setter
public class UserPreference extends BaseEntity {

    @Column(name = "user_id", length = 36, nullable = false, unique = true)
    private String userId;

    @Column(name = "working_hours_start")
    private LocalTime workingHoursStart;

    @Column(name = "working_hours_end")
    private LocalTime workingHoursEnd;

    @Column(name = "day_start")
    private LocalTime dayStart;

    @Column(name = "day_end")
    private LocalTime dayEnd;

    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_productivity_style", length = 32)
    private ProductivityStyle preferredProductivityStyle;

    @Column(name = "preferred_focus_minutes", nullable = false)
    private int preferredFocusMinutes = 25;

    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes = 5;

    @Column(name = "weekly_productivity_hours", nullable = false)
    private BigDecimal weeklyProductivityHours = new BigDecimal("20.00");

    @Column(name = "areas_of_interest", columnDefinition = "TEXT")
    private String areasOfInterest;

    @Column(name = "current_skills", columnDefinition = "TEXT")
    private String currentSkills;

    @Column(name = "primary_goal_areas", columnDefinition = "TEXT")
    private String primaryGoalAreas;

    @Column(name = "financial_goals", columnDefinition = "TEXT")
    private String financialGoals;

    @Column(name = "learning_goals", columnDefinition = "TEXT")
    private String learningGoals;

    @Column(name = "life_balance_weights", columnDefinition = "TEXT")
    private String lifeBalanceWeights;
}