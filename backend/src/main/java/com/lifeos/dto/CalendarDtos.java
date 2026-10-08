package com.lifeos.dto;

import com.lifeos.entity.enums.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Calendar event payloads plus the aggregated day/week/month feed used by the calendar UI. */
public final class CalendarDtos {

    private CalendarDtos() {
    }

    public record EventRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 20000) String description,
            EventType eventType,
            @NotNull Instant startAt,
            @NotNull Instant endAt,
            Boolean allDay,
            @Size(max = 120) String recurrenceRule,
            String taskId,
            String goalId,
            String habitId,
            @Size(max = 200) String location,
            @Size(max = 16) String color
    ) {
    }

    public record MoveRequest(@NotNull Instant startAt, @NotNull Instant endAt) {
    }

    public record EventResponse(
            String id,
            String title,
            String description,
            EventType eventType,
            Instant startAt,
            Instant endAt,
            boolean allDay,
            String recurrenceRule,
            String taskId,
            String goalId,
            String habitId,
            String location,
            String color,
            String externalSource,
            Instant createdAt
    ) {
    }

    /** Expanded occurrence of a recurring series, carrying the parent series id. */
    public record Occurrence(
            String id,
            String seriesId,
            String title,
            EventType eventType,
            Instant startAt,
            Instant endAt,
            String color,
            String taskId
    ) {
    }

    public record CalendarFeed(
            LocalDate rangeStart,
            LocalDate rangeEnd,
            List<Occurrence> occurrences,
            List<TaskDtos.TaskResponse> deadlines,
            List<HabitDtos.HabitResponse> habitSchedules,
            long totalEvents
    ) {
    }

    public record RangeRequest(LocalDate from, LocalDate to) {
    }
}