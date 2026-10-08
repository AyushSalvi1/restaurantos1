package com.lifeos.service;

import com.lifeos.dto.CalendarDtos;
import com.lifeos.entity.CalendarEvent;
import com.lifeos.entity.Task;
import com.lifeos.entity.enums.EventType;
import com.lifeos.exception.AppException;
import com.lifeos.repository.CalendarEventRepository;
import com.lifeos.repository.TaskRepository;
import com.lifeos.util.DateSupport;
import com.lifeos.util.RecurrenceRule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Calendar with a custom day/week/month feed. Recurring series are expanded on read and
 * {@code externalSource/externalId} are reserved for a future Google Calendar sync adapter.
 */
@Service
public class CalendarService {

    private static final int MAX_OCCURRENCES = 400;

    private final CalendarEventRepository eventRepository;
    private final TaskRepository taskRepository;
    private final TaskService taskService;
    private final HabitService habitService;
    private final UserZoneService userZoneService;

    public CalendarService(CalendarEventRepository eventRepository,
                           TaskRepository taskRepository,
                           TaskService taskService,
                           HabitService habitService,
                           UserZoneService userZoneService) {
        this.eventRepository = eventRepository;
        this.taskRepository = taskRepository;
        this.taskService = taskService;
        this.habitService = habitService;
        this.userZoneService = userZoneService;
    }

    @Transactional(readOnly = true)
    public CalendarDtos.CalendarFeed feed(String userId, LocalDate from, LocalDate to) {
        ZoneId zone = userZoneService.zoneOf(userId);
        Instant windowStart = DateSupport.startOfDay(from, zone);
        Instant windowEnd = DateSupport.endOfDay(to, zone);

        List<CalendarDtos.Occurrence> occurrences = new ArrayList<>();
        for (CalendarEvent event : eventRepository.findOverlapping(userId, windowStart, windowEnd)) {
            occurrences.addAll(expand(event, windowStart, windowEnd, zone));
        }
        occurrences.sort(Comparator.comparing(CalendarDtos.Occurrence::startAt));

        List<Task> deadlineTasks = taskRepository.findDueBetween(userId,
                List.of(com.lifeos.entity.enums.TaskStatus.TODO, com.lifeos.entity.enums.TaskStatus.IN_PROGRESS),
                windowStart, windowEnd);

        return new CalendarDtos.CalendarFeed(
                from,
                to,
                occurrences,
                deadlineTasks.stream().map(taskService::toResponse).toList(),
                habitService.list(userId, false),
                occurrences.size());
    }

    @Transactional(readOnly = true)
    public CalendarDtos.Occurrence get(String userId, String eventId) {
        CalendarEvent event = requireOwned(userId, eventId);
        ZoneId zone = userZoneService.zoneOf(userId);
        Instant windowStart = event.getStartAt().minus(Duration.ofDays(1));
        Instant windowEnd = event.getEndAt().plus(Duration.ofDays(1));
        List<CalendarDtos.Occurrence> expanded = expand(event, windowStart, windowEnd, zone);
        return expanded.isEmpty() ? toOccurrence(event, event.getStartAt()) : expanded.get(0);
    }

    @Transactional
    public CalendarDtos.EventResponse create(String userId, CalendarDtos.EventRequest request) {
        validateRange(request.startAt(), request.endAt());
        validateLinks(userId, request.taskId());
        CalendarEvent event = new CalendarEvent();
        event.setUserId(userId);
        apply(event, request);
        eventRepository.save(event);
        return toResponse(event);
    }

    @Transactional
    public CalendarDtos.EventResponse update(String userId, String eventId, CalendarDtos.EventRequest request) {
        CalendarEvent event = requireOwned(userId, eventId);
        validateRange(request.startAt(), request.endAt());
        validateLinks(userId, request.taskId());
        apply(event, request);
        eventRepository.save(event);
        return toResponse(event);
    }

    @Transactional
    public CalendarDtos.EventResponse move(String userId, String eventId, CalendarDtos.MoveRequest request) {
        CalendarEvent event = requireOwned(userId, eventId);
        validateRange(request.startAt(), request.endAt());
        Duration duration = Duration.between(event.getStartAt(), event.getEndAt());
        event.setStartAt(request.startAt());
        event.setEndAt(request.startAt().plus(duration.isNegative() || duration.isZero() ? Duration.ofHours(1) : duration));
        eventRepository.save(event);
        return toResponse(event);
    }

    @Transactional
    public void delete(String userId, String eventId) {
        eventRepository.delete(requireOwned(userId, eventId));
    }

    @Transactional(readOnly = true)
    public List<com.lifeos.dto.DashboardDtos.UpcomingEvent> upcoming(String userId, Instant from, Instant to, int limit) {
        return eventRepository.findStartingBetween(userId, from, to).stream()
                .limit(limit)
                .map(event -> new com.lifeos.dto.DashboardDtos.UpcomingEvent(
                        event.getId(),
                        event.getTitle(),
                        event.getStartAt(),
                        event.getEndAt(),
                        event.getEventType().name(),
                        event.getLocation(),
                        event.isAllDay()))
                .toList();
    }

    @Transactional(readOnly = true)
    public long eventCount(String userId, Instant from, Instant to) {
        return eventRepository.countBetween(userId, from, to);
    }

    @Transactional(readOnly = true)
    public CalendarEvent requireOwned(String userId, String eventId) {
        return eventRepository.findByIdAndUserId(eventId, userId)
                .orElseThrow(() -> AppException.notFound("Event not found"));
    }

    // ---------------------------------------------------------------- helpers

    private List<CalendarDtos.Occurrence> expand(CalendarEvent event, Instant windowStart, Instant windowEnd, ZoneId zone) {
        if (!RecurrenceRule.isRecurring(event.getRecurrenceRule())) {
            return List.of(toOccurrence(event, event.getStartAt()));
        }
        Duration duration = Duration.between(event.getStartAt(), event.getEndAt());
        if (duration.isNegative()) {
            duration = Duration.ofHours(1);
        }
        RecurrenceRule rule = RecurrenceRule.parse(event.getRecurrenceRule());
        List<Instant> starts = rule.expand(event.getStartAt(), windowStart, windowEnd, zone,
                event.getEndAt().plus(Duration.ofDays(365)), MAX_OCCURRENCES);
        List<CalendarDtos.Occurrence> occurrences = new ArrayList<>(starts.size());
        for (Instant start : starts) {
            occurrences.add(new CalendarDtos.Occurrence(
                    event.getId() + "@" + start.toEpochMilli(),
                    event.getId(),
                    event.getTitle(),
                    event.getEventType(),
                    start,
                    start.plus(duration),
                    event.getColor(),
                    event.getTaskId()));
        }
        return occurrences;
    }

    private CalendarDtos.Occurrence toOccurrence(CalendarEvent event, Instant start) {
        return new CalendarDtos.Occurrence(
                event.getId(),
                event.getId(),
                event.getTitle(),
                event.getEventType(),
                start,
                event.getEndAt(),
                event.getColor(),
                event.getTaskId());
    }

    private void apply(CalendarEvent event, CalendarDtos.EventRequest request) {
        event.setTitle(request.title().strip());
        if (request.description() != null) {
            event.setDescription(request.description());
        }
        if (request.eventType() != null) {
            event.setEventType(request.eventType());
        }
        event.setStartAt(request.startAt());
        event.setEndAt(request.endAt());
        event.setAllDay(Boolean.TRUE.equals(request.allDay()));
        event.setRecurrenceRule(request.recurrenceRule() == null || request.recurrenceRule().isBlank()
                ? null : request.recurrenceRule().trim());
        event.setTaskId(blankToNull(request.taskId()));
        event.setGoalId(blankToNull(request.goalId()));
        event.setHabitId(blankToNull(request.habitId()));
        event.setLocation(request.location());
        event.setColor(request.color());
    }

    private void validateRange(Instant start, Instant end) {
        if (!end.isAfter(start)) {
            throw AppException.badRequest("The event end time must be after its start time");
        }
        if (Duration.between(start, end).toDays() > 366) {
            throw AppException.badRequest("A single event cannot span more than a year");
        }
    }

    private void validateLinks(String userId, String taskId) {
        if (taskId != null && !taskId.isBlank()) {
            taskRepository.findByIdAndUserIdAndDeletedAtIsNull(taskId, userId)
                    .orElseThrow(() -> AppException.badRequest("The referenced task does not exist"));
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public CalendarDtos.EventResponse toResponse(CalendarEvent event) {
        return new CalendarDtos.EventResponse(
                event.getId(),
                event.getTitle(),
                event.getDescription(),
                event.getEventType(),
                event.getStartAt(),
                event.getEndAt(),
                event.isAllDay(),
                event.getRecurrenceRule(),
                event.getTaskId(),
                event.getGoalId(),
                event.getHabitId(),
                event.getLocation(),
                event.getColor(),
                event.getExternalSource(),
                event.getCreatedAt());
    }

    /**
 * Upcoming task deadlines as calendar items. The range bounds are optional in the API, so an omitted one
 * widens to the whole of time instead of failing: without bounds this is every open deadline.
 */
@Transactional(readOnly = true)
    public List<CalendarDtos.EventResponse> deadlineEvents(String userId, LocalDate from, LocalDate to) {
        ZoneId zone = userZoneService.zoneOf(userId);
        LocalDate today = LocalDate.now(zone);
        Instant start = from == null ? DateSupport.startOfDay(today.minusMonths(12), zone)
                : DateSupport.startOfDay(from, zone);
        Instant end = to == null ? DateSupport.endOfDay(today.plusMonths(12), zone)
                : DateSupport.endOfDay(to, zone);
        return taskRepository.findDueBetween(userId,
                        List.of(com.lifeos.entity.enums.TaskStatus.TODO, com.lifeos.entity.enums.TaskStatus.IN_PROGRESS),
                        start, end).stream()
                .map(task -> new CalendarDtos.EventResponse(
                        task.getId(),
                        task.getTitle(),
                        "Task deadline",
                        EventType.DEADLINE,
                        task.getDeadline(),
                        task.getDeadline().plus(Duration.ofMinutes(30)),
                        false,
                        null,
                        task.getId(),
                        task.getGoalId(),
                        null,
                        null,
                        null,
                        null,
                        task.getCreatedAt()))
                .toList();
    }
}