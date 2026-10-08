package com.lifeos.dto;

import com.lifeos.entity.enums.FocusMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Focus (Pomodoro / deep work) session payloads. */
public final class FocusDtos {

    private FocusDtos() {
    }

    public record StartSessionRequest(
            FocusMode mode,
            @Min(1) @Max(600) Integer plannedMinutes,
            String taskId,
            String goalId,
            Instant startedAt
    ) {
    }

    public record CompleteSessionRequest(
            @Min(0) @Max(600) Integer actualMinutes,
            Boolean completed,
            @Min(0) @Max(100) Integer interruptedCount,
            @Size(max = 500) String outcome,
            @Size(max = 20000) String notes,
            @Min(1) @Max(5) Integer rating,
            Instant endedAt
    ) {
    }

    public record FocusSessionResponse(
            String id,
            String taskId,
            String taskTitle,
            String goalId,
            FocusMode mode,
            int plannedMinutes,
            int actualMinutes,
            Instant startedAt,
            Instant endedAt,
            boolean completed,
            int interruptedCount,
            String outcome,
            String notes,
            Integer rating
    ) {
    }

    public record FocusStats(
            long totalSessions,
            long completedSessions,
            int totalMinutes,
            int averageMinutes,
            double completionRate,
            int todayMinutes,
            long thisWeekSessions,
            int thisWeekMinutes,
            long bestStreak,
            List<DailyFocusPoint> daily
    ) {
        public record DailyFocusPoint(java.time.LocalDate date, int minutes, long sessions) {
        }
    }
}