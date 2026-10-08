package com.lifeos.service;

import com.lifeos.dto.FocusDtos;
import com.lifeos.entity.FocusSession;
import com.lifeos.entity.Task;
import com.lifeos.entity.enums.FocusMode;
import com.lifeos.entity.enums.GraphNodeType;
import com.lifeos.entity.enums.GraphRelation;
import com.lifeos.entity.enums.TaskStatus;
import com.lifeos.exception.AppException;
import com.lifeos.repository.FocusSessionRepository;
import com.lifeos.repository.TaskRepository;
import com.lifeos.util.DateSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pomodoro and deep-work sessions, plus the focus statistics derived from them. */
@Service
public class FocusService {

    private final FocusSessionRepository focusSessionRepository;
    private final TaskRepository taskRepository;
    private final MetricsService metricsService;
    private final LifeGraphService lifeGraphService;
    private final UserZoneService userZoneService;

    public FocusService(FocusSessionRepository focusSessionRepository,
                        TaskRepository taskRepository,
                        MetricsService metricsService,
                        LifeGraphService lifeGraphService,
                        UserZoneService userZoneService) {
        this.focusSessionRepository = focusSessionRepository;
        this.taskRepository = taskRepository;
        this.metricsService = metricsService;
        this.lifeGraphService = lifeGraphService;
        this.userZoneService = userZoneService;
    }

    @Transactional
    public FocusDtos.FocusSessionResponse start(String userId, FocusDtos.StartSessionRequest request) {
        FocusSession session = new FocusSession();
        session.setUserId(userId);
        session.setMode(request.mode() == null ? FocusMode.POMODORO_25_5 : request.mode());
        session.setPlannedMinutes(resolvePlannedMinutes(session.getMode(), request.plannedMinutes()));
        session.setStartedAt(request.startedAt() == null ? Instant.now() : request.startedAt());
        session.setTaskId(validateTask(userId, request.taskId()));
        session.setGoalId(blankToNull(request.goalId()));
        session.setCompleted(false);
        focusSessionRepository.save(session);

        if (session.getTaskId() != null) {
            lifeGraphService.link(userId, GraphNodeType.FOCUS_SESSION, session.getId(),
                    GraphNodeType.TASK, session.getTaskId(), GraphRelation.DERIVED_FROM, 1.0);
        }
        if (session.getGoalId() != null) {
            lifeGraphService.link(userId, GraphNodeType.FOCUS_SESSION, session.getId(),
                    GraphNodeType.GOAL, session.getGoalId(), GraphRelation.CONTRIBUTES_TO, 0.8);
        }
        return toResponse(session);
    }

    @Transactional
    public FocusDtos.FocusSessionResponse complete(String userId, String sessionId, FocusDtos.CompleteSessionRequest request) {
        FocusSession session = requireOwned(userId, sessionId);
        if (session.getEndedAt() != null) {
            throw AppException.conflict("This focus session has already been closed");
        }
        Instant endedAt = request.endedAt() == null ? Instant.now() : request.endedAt();
        int actualMinutes = request.actualMinutes() != null
                ? request.actualMinutes()
                : (int) Math.max(0, Duration.between(session.getStartedAt(), endedAt).toMinutes());

        session.setEndedAt(endedAt);
        session.setActualMinutes(actualMinutes);
        session.setCompleted(request.completed() == null || request.completed());
        session.setInterruptedCount(request.interruptedCount() == null ? 0 : request.interruptedCount());
        session.setOutcome(request.outcome());
        session.setNotes(request.notes());
        session.setRating(request.rating());
        focusSessionRepository.save(session);

        ZoneId zone = userZoneService.zoneOf(userId);
        metricsService.recordFocusMinutes(userId, DateSupport.toLocalDate(session.getStartedAt(), zone), actualMinutes);

        if (session.getTaskId() != null && actualMinutes > 0) {
            Task task = taskRepository.findByIdAndUserIdAndDeletedAtIsNull(session.getTaskId(), userId).orElse(null);
            if (task != null && session.isCompleted() && task.getActualMinutes() == 0) {
                task.setActualMinutes(actualMinutes);
                taskRepository.save(task);
            }
        }
        return toResponse(session);
    }

    @Transactional(readOnly = true)
    public FocusDtos.FocusSessionResponse get(String userId, String sessionId) {
        return toResponse(requireOwned(userId, sessionId));
    }

    @Transactional
    public void delete(String userId, String sessionId) {
        focusSessionRepository.delete(requireOwned(userId, sessionId));
    }

    @Transactional(readOnly = true)
    /**
 * Session history, newest first, optionally limited to a day range in the user's own timezone.
 *
 * <p>An absent bound must not narrow the result: the underlying range query compares against its bounds
 * directly, so passing nulls would return nothing at all. Each omitted bound is therefore widened to the
 * whole of time instead.</p>
 */
public List<FocusDtos.FocusSessionResponse> list(String userId, LocalDate from, LocalDate to, Integer limit) {
        List<FocusSession> sessions;
        if (from == null && to == null) {
            sessions = focusSessionRepository.findByUserIdOrderByStartedAtDesc(userId);
        } else {
            ZoneId zone = userZoneService.zoneOf(userId);
            Instant instantFrom = from == null ? Instant.EPOCH : DateSupport.startOfDay(from, zone);
            Instant instantTo = to == null ? DateSupport.endOfDay(LocalDate.now(zone), zone)
                    : DateSupport.endOfDay(to, zone);
            sessions = focusSessionRepository.findBetween(userId, instantFrom, instantTo);
        }
        List<FocusSession> newestFirst = sessions.stream()
                .sorted(java.util.Comparator.comparing(FocusSession::getStartedAt).reversed())
                .toList();
        if (limit != null && limit > 0 && newestFirst.size() > limit) {
            newestFirst = newestFirst.subList(0, limit);
        }
        return newestFirst.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public FocusDtos.FocusStats statistics(String userId, int days) {
        ZoneId zone = userZoneService.zoneOf(userId);
        LocalDate today = LocalDate.now(zone);
        LocalDate from = today.minusDays(Math.min(Math.max(days, 7), 365) - 1L);
        Instant instantFrom = DateSupport.startOfDay(from, zone);
        Instant instantTo = DateSupport.endOfDay(today, zone);

        List<FocusSession> sessions = focusSessionRepository.findBetween(userId, instantFrom, instantTo);
        List<FocusSession> completed = sessions.stream().filter(FocusSession::isCompleted).toList();
        int totalMinutes = completed.stream().mapToInt(FocusSession::getActualMinutes).sum();
        int averageMinutes = completed.isEmpty() ? 0 : totalMinutes / completed.size();

        Map<LocalDate, List<FocusSession>> byDay = new HashMap<>();
        sessions.forEach(session -> byDay
                .computeIfAbsent(DateSupport.toLocalDate(session.getStartedAt(), zone), ignored -> new ArrayList<>())
                .add(session));

        List<FocusDtos.FocusStats.DailyFocusPoint> daily = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
            List<FocusSession> daySessions = byDay.getOrDefault(day, List.of());
            daily.add(new FocusDtos.FocusStats.DailyFocusPoint(day,
                    daySessions.stream().filter(FocusSession::isCompleted).mapToInt(FocusSession::getActualMinutes).sum(),
                    daySessions.size()));
        }

        LocalDate weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1L);
        List<FocusSession> weekSessions = sessions.stream()
                .filter(session -> !DateSupport.toLocalDate(session.getStartedAt(), zone).isBefore(weekStart))
                .toList();

        return new FocusDtos.FocusStats(
                sessions.size(),
                completed.size(),
                totalMinutes,
                averageMinutes,
                sessions.isEmpty() ? 0 : Math.round((completed.size() * 1000.0 / sessions.size())) / 10.0,
                completed.stream()
                        .filter(session -> DateSupport.toLocalDate(session.getStartedAt(), zone).equals(today))
                        .mapToInt(FocusSession::getActualMinutes).sum(),
                weekSessions.size(),
                weekSessions.stream().filter(FocusSession::isCompleted).mapToInt(FocusSession::getActualMinutes).sum(),
                longestDailyRun(daily),
                daily);
    }

    @Transactional(readOnly = true)
    public int minutesOn(String userId, LocalDate date) {
        ZoneId zone = userZoneService.zoneOf(userId);
        return (int) focusSessionRepository.sumMinutesBetween(userId,
                DateSupport.startOfDay(date, zone), DateSupport.endOfDay(date, zone));
    }

    @Transactional(readOnly = true)
    public int totalMinutes(String userId) {
        return (int) focusSessionRepository.sumMinutesSince(userId, Instant.EPOCH);
    }

    @Transactional(readOnly = true)
    public FocusSession requireOwned(String userId, String sessionId) {
        return focusSessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> AppException.notFound("Focus session not found"));
    }

    private long longestDailyRun(List<FocusDtos.FocusStats.DailyFocusPoint> daily) {
        long best = 0;
        long run = 0;
        for (FocusDtos.FocusStats.DailyFocusPoint point : daily) {
            if (point.minutes() >= 25) {
                run++;
                best = Math.max(best, run);
            } else {
                run = 0;
            }
        }
        return best;
    }

    private int resolvePlannedMinutes(FocusMode mode, Integer requested) {
        if (requested != null && requested > 0) {
            return Math.min(requested, 600);
        }
        return switch (mode) {
            case POMODORO_25_5 -> 25;
            case POMODORO_50_10 -> 50;
            case DEEP_WORK -> 90;
            case CUSTOM -> 25;
        };
    }

    private String validateTask(String userId, String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        taskRepository.findByIdAndUserIdAndDeletedAtIsNull(taskId, userId)
                .orElseThrow(() -> AppException.notFound("The referenced task does not exist"));
        return taskId;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private FocusDtos.FocusSessionResponse toResponse(FocusSession session) {
        String taskTitle = session.getTaskId() == null ? null
                : taskRepository.findByIdAndUserIdAndDeletedAtIsNull(session.getTaskId(), session.getUserId())
                .map(Task::getTitle).orElse(null);
        return new FocusDtos.FocusSessionResponse(
                session.getId(),
                session.getTaskId(),
                taskTitle,
                session.getGoalId(),
                session.getMode(),
                session.getPlannedMinutes(),
                session.getActualMinutes(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.isCompleted(),
                session.getInterruptedCount(),
                session.getOutcome(),
                session.getNotes(),
                session.getRating());
    }

    /** Marks an in-progress task as started when a focus session is opened against it. */
    @Transactional
    public void markTaskInProgress(String userId, String taskId) {
        if (taskId == null) {
            return;
        }
        taskRepository.findByIdAndUserIdAndDeletedAtIsNull(taskId, userId).ifPresent(task -> {
            if (task.getStatus() == TaskStatus.TODO) {
                task.setStatus(TaskStatus.IN_PROGRESS);
                taskRepository.save(task);
            }
        });
    }
}