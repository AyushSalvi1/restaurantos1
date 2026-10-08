package com.lifeos.dto;

import com.lifeos.entity.enums.HabitFrequency;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Habit definition and tracking payloads. */
public final class HabitDtos {

    private HabitDtos() {
    }

    public record HabitRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 20000) String description,
            @Size(max = 48) String category,
            HabitFrequency frequencyType,
            @Min(1) @Max(7) Integer timesPerPeriod,
            List<Integer> targetDays,
            LocalTime reminderTime,
            @Size(max = 16) String color,
            Boolean archived
    ) {
    }

    public record HabitLogRequest(
            @NotNull LocalDate logDate,
            Boolean completed,
            java.math.BigDecimal quantity,
            @Size(max = 500) String note
    ) {
    }

    public record HabitToggleRequest(LocalDate logDate) {
    }

    public record HabitLogResponse(
            String id,
            String habitId,
            LocalDate logDate,
            boolean completed,
            java.math.BigDecimal quantity,
            String note
    ) {
    }

    public record HabitResponse(
            String id,
            String name,
            String description,
            String category,
            HabitFrequency frequencyType,
            int timesPerPeriod,
            List<Integer> targetDays,
            LocalTime reminderTime,
            String color,
            boolean archived,
            int currentStreak,
            int longestStreak,
            int completedLast30Days,
            int scheduledLast30Days,
            double consistencyRate30d,
            LocalDate lastCompletedDate,
            boolean completedToday,
            Instant createdAt
    ) {
    }

    public record HabitTrendPoint(LocalDate date, boolean completed, boolean scheduled) {
    }

    public record HabitTrend(String habitId, String name, List<HabitTrendPoint> points) {
    }

    public record HabitStats(
            String habitId,
            String name,
            int currentStreak,
            int longestStreak,
            int completedLast30Days,
            int scheduledLast30Days,
            double consistencyRate30d,
            int missedLast30Days,
            String bestDayOfWeek,
            String suggestion
    ) {
    }
}