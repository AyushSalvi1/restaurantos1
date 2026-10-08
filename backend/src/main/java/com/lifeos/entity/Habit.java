package com.lifeos.entity;

import com.lifeos.entity.enums.HabitFrequency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

@Entity
@Table(name = "habits")
@Getter
@Setter
public class Habit extends BaseEntity {

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "name", length = 120, nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "category", length = 48)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency_type", length = 16, nullable = false)
    private HabitFrequency frequencyType = HabitFrequency.DAILY;

    @Column(name = "times_per_period", nullable = false)
    private int timesPerPeriod = 1;

    /** ISO-8601 day numbers (1 = Monday .. 7 = Sunday) as a comma separated list. */
    @Column(name = "target_days", length = 16)
    private String targetDays;

    @Column(name = "reminder_time")
    private LocalTime reminderTime;

    @Column(name = "color", length = 16)
    private String color;

    @Column(name = "archived", nullable = false)
    private boolean archived;
}