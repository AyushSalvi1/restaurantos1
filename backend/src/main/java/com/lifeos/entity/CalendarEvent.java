package com.lifeos.entity;

import com.lifeos.entity.enums.EventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "calendar_events")
@Getter
@Setter
public class CalendarEvent extends BaseEntity {

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 24, nullable = false)
    private EventType eventType = EventType.EVENT;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "all_day", nullable = false)
    private boolean allDay;

    @Column(name = "recurrence_rule", length = 120)
    private String recurrenceRule;

    @Column(name = "task_id", length = 36)
    private String taskId;

    @Column(name = "goal_id", length = 36)
    private String goalId;

    @Column(name = "habit_id", length = 36)
    private String habitId;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "color", length = 16)
    private String color;

    @Column(name = "external_source", length = 48)
    private String externalSource;

    @Column(name = "external_id", length = 190)
    private String externalId;
}