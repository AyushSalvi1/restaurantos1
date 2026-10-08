package com.lifeos.service;

import com.lifeos.dto.HabitDtos;
import com.lifeos.entity.Habit;
import com.lifeos.entity.HabitLog;
import com.lifeos.entity.enums.HabitFrequency;
import com.lifeos.exception.AppException;
import com.lifeos.repository.HabitLogRepository;
import com.lifeos.repository.HabitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Habits, tracking and streaks. Deliberately factual: the API reports what happened and offers a
 * neutral suggestion, with no guilt framing or streak-shaming language.
 */
@Service
public class HabitService {

    private static final int WINDOW_DAYS = 30;
    private static final int LOOKBACK_WEEKS = 26;

    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;
    private final MetricsService metricsService;
    private final UserZoneService userZoneService;

    public HabitService(HabitRepository habitRepository,
                        HabitLogRepository habitLogRepository,
                        MetricsService metricsService,
                        UserZoneService userZoneService) {
        this.habitRepository = habitRepository;
        this.habitLogRepository = habitLogRepository;
        this.metricsService = metricsService;
        this.userZoneService = userZoneService;
    }

    @Transactional(readOnly = true)
    public List<HabitDtos.HabitResponse> list(String userId, boolean includeArchived) {
        List<Habit> habits = includeArchived
                ? habitRepository.findByUserIdOrderByCreatedAtAsc(userId)
                : habitRepository.findActiveForUser(userId);
        LocalDate today = LocalDate.now(userZoneService.zoneOf(userId));
        return habits.stream().map(habit -> toResponse(habit, today)).toList();
    }

    @Transactional(readOnly = true)
    public HabitDtos.HabitResponse get(String userId, String habitId) {
        Habit habit = requireOwned(userId, habitId);
        return toResponse(habit, LocalDate.now(userZoneService.zoneOf(userId)));
    }

    @Transactional(readOnly = true)
    public Habit requireOwned(String userId, String habitId) {
        return habitRepository.findByIdAndUserId(habitId, userId)
                .orElseThrow(() -> AppException.notFound("Habit not found"));
    }

    @Transactional
    public HabitDtos.HabitResponse create(String userId, HabitDtos.HabitRequest request) {
        Habit habit = new Habit();
        habit.setUserId(userId);
        apply(habit, request);
        habitRepository.save(habit);
        return toResponse(habit, LocalDate.now(userZoneService.zoneOf(userId)));
    }

    @Transactional
    public HabitDtos.HabitResponse update(String userId, String habitId, HabitDtos.HabitRequest request) {
        Habit habit = requireOwned(userId, habitId);
        apply(habit, request);
        habitRepository.save(habit);
        return toResponse(habit, LocalDate.now(userZoneService.zoneOf(userId)));
    }

    @Transactional
    public void delete(String userId, String habitId) {
        Habit habit = requireOwned(userId, habitId);
        habit.setArchived(true);
        habitRepository.save(habit);
        habitLogRepository.deleteByHabitId(habit.getId());
    }

    // -------------------------------------------------------------- tracking

    @Transactional
    public HabitDtos.HabitLogResponse record(String userId, String habitId, HabitDtos.HabitLogRequest request) {
        Habit habit = requireOwned(userId, habitId);
        HabitLog log = habitLogRepository.findByHabitIdAndLogDate(habitId, request.logDate())
                .orElseGet(HabitLog::new);
        boolean wasCompleted = log.isCompleted();
        boolean nowCompleted = request.completed() == null || request.completed();

        log.setHabitId(habitId);
        log.setUserId(userId);
        log.setLogDate(request.logDate());
        log.setCompleted(nowCompleted);
        log.setQuantity(request.quantity() == null ? BigDecimal.ONE : request.quantity());
        log.setNote(request.note());
        habitLogRepository.save(log);

        if (wasCompleted != nowCompleted) {
            metricsService.recordHabitCompletion(userId, request.logDate(), nowCompleted ? 1 : -1);
        }
        return toLogResponse(log);
    }

    @Transactional
    public HabitDtos.HabitLogResponse toggle(String userId, String habitId, LocalDate date) {
        HabitLog existing = habitLogRepository.findByHabitIdAndLogDate(habitId, date).orElse(null);
        boolean completed = existing != null && existing.isCompleted();
        return record(userId, habitId, new HabitDtos.HabitLogRequest(date, !completed, BigDecimal.ONE, null));
    }

    @Transactional(readOnly = true)
    public List<HabitDtos.HabitLogResponse> logs(String userId, String habitId, LocalDate from, LocalDate to) {
        requireOwned(userId, habitId);
        return habitLogRepository.findByUserIdAndHabitIdAndLogDateBetween(userId, habitId, from, to).stream()
                .map(this::toLogResponse).toList();
    }

    @Transactional(readOnly = true)
    public HabitDtos.HabitTrend trend(String userId, String habitId, int days) {
        Habit habit = requireOwned(userId, habitId);
        LocalDate today = LocalDate.now(userZoneService.zoneOf(userId));
        int window = Math.min(Math.max(days, 7), 180);
        LocalDate from = today.minusDays(window - 1L);

        Map<LocalDate, Boolean> completed = new HashMap<>();
        habitLogRepository.findByUserIdAndHabitIdAndLogDateBetween(userId, habitId, from, today)
                .forEach(log -> completed.put(log.getLogDate(), log.isCompleted()));

        List<HabitDtos.HabitTrendPoint> points = new ArrayList<>();
        for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
            points.add(new HabitDtos.HabitTrendPoint(day,
                    Boolean.TRUE.equals(completed.get(day)),
                    isScheduled(habit, day)));
        }
        return new HabitDtos.HabitTrend(habitId, habit.getName(), points);
    }

    @Transactional(readOnly = true)
    public List<HabitDtos.HabitStats> statistics(String userId) {
        LocalDate today = LocalDate.now(userZoneService.zoneOf(userId));
        LocalDate from = today.minusDays(WINDOW_DAYS - 1L);
        List<Habit> habits = habitRepository.findActiveForUser(userId);

        Map<String, List<HabitLog>> logsByHabit = new HashMap<>();
        if (!habits.isEmpty()) {
            habitLogRepository.findByUserIdAndHabitIdInAndLogDateBetween(userId,
                            habits.stream().map(Habit::getId).toList(), from.minusDays(LOOKBACK_WEEKS * 7L), today)
                    .forEach(log -> logsByHabit.computeIfAbsent(log.getHabitId(), ignored -> new ArrayList<>()).add(log));
        }

        List<HabitDtos.HabitStats> stats = new ArrayList<>();
        for (Habit habit : habits) {
            List<HabitLog> logs = logsByHabit.getOrDefault(habit.getId(), List.of());
            Set<LocalDate> completedDays = new HashSet<>();
            logs.stream().filter(HabitLog::isCompleted).forEach(log -> completedDays.add(log.getLogDate()));

            int completed30 = 0;
            int scheduled30 = 0;
            for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
                if (isScheduled(habit, day)) {
                    scheduled30++;
                    if (completedDays.contains(day)) {
                        completed30++;
                    }
                }
            }
            double rate = scheduled30 == 0 ? 0 : (completed30 * 100.0 / scheduled30);
            int currentStreak = currentStreak(habit, completedDays, today);
            int longestStreak = longestStreak(habit, completedDays);

            stats.add(new HabitDtos.HabitStats(
                    habit.getId(),
                    habit.getName(),
                    currentStreak,
                    longestStreak,
                    completed30,
                    scheduled30,
                    Math.round(rate * 10) / 10.0,
                    Math.max(0, scheduled30 - completed30),
                    strongestDay(habit, completedDays),
                    suggestion(habit, rate, scheduled30 - completed30)));
        }
        return stats;
    }

    // ---------------------------------------------------------------- helpers

    private void apply(Habit habit, HabitDtos.HabitRequest request) {
        habit.setName(request.name().strip());
        if (request.description() != null) {
            habit.setDescription(request.description());
        }
        habit.setCategory(request.category());
        if (request.frequencyType() != null) {
            habit.setFrequencyType(request.frequencyType());
        }
        if (request.timesPerPeriod() != null) {
            habit.setTimesPerPeriod(request.timesPerPeriod());
        }
        if (request.targetDays() != null && !request.targetDays().isEmpty()) {
            habit.setTargetDays(request.targetDays().stream()
                    .filter(day -> day != null && day >= 1 && day <= 7)
                    .sorted()
                    .map(String::valueOf)
                    .collect(java.util.stream.Collectors.joining(",")));
        }
        habit.setReminderTime(request.reminderTime());
        habit.setColor(request.color());
        if (request.archived() != null) {
            habit.setArchived(request.archived());
        }
    }

    public boolean isScheduled(Habit habit, LocalDate day) {
        if (habit.getFrequencyType() == HabitFrequency.DAILY) {
            return true;
        }
        String targetDays = habit.getTargetDays();
        if (targetDays == null || targetDays.isBlank()) {
            return true;
        }
        for (String value : targetDays.split(",")) {
            if (value.trim().equals(String.valueOf(day.getDayOfWeek().getValue()))) {
                return true;
            }
        }
        return false;
    }

    /** Consecutive scheduled occurrences with a completed log, allowing today to still be open. */
    private int currentStreak(Habit habit, Set<LocalDate> completedDays, LocalDate today) {
        int streak = 0;
        LocalDate cursor = today;
        if (isScheduled(habit, cursor) && !completedDays.contains(cursor)) {
            cursor = cursor.minusDays(1);
        }
        int guard = 0;
        while (guard++ < 400) {
            if (!isScheduled(habit, cursor)) {
                cursor = cursor.minusDays(1);
                continue;
            }
            if (completedDays.contains(cursor)) {
                streak++;
                cursor = cursor.minusDays(1);
            } else {
                break;
            }
        }
        return streak;
    }

    private int longestStreak(Habit habit, Set<LocalDate> completedDays) {
        if (completedDays.isEmpty()) {
            return 0;
        }
        List<LocalDate> sorted = completedDays.stream().sorted().toList();
        int best = 0;
        int run = 0;
        LocalDate previous = null;
        for (LocalDate day : sorted) {
            if (previous != null && isScheduled(habit, day)
                    && ChronoUnit.DAYS.between(previous, day) <= 2) {
                run++;
            } else {
                run = 1;
            }
            best = Math.max(best, run);
            previous = day;
        }
        return best;
    }

    private String strongestDay(Habit habit, Set<LocalDate> completedDays) {
        if (completedDays.isEmpty()) {
            return null;
        }
        Map<DayOfWeek, Integer> counts = new HashMap<>();
        completedDays.forEach(day -> counts.merge(day.getDayOfWeek(), 1, Integer::sum));
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> entry.getKey().getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH))
                .orElse(null);
    }

    private String suggestion(Habit habit, double rate, int missed) {
        if (missed == 0) {
            return "Fully completed over the last " + WINDOW_DAYS + " days.";
        }
        if (rate >= 80) {
            return "Steady at " + Math.round(rate) + "%. Consider scheduling it at the time you already " +
                    "succeed most often.";
        }
        if (rate >= 50) {
            return "You completed this " + Math.round(rate) + "% of scheduled days. Reducing the frequency to "
                    + habit.getFrequencyType().name().toLowerCase() + " x "
                    + Math.max(1, habit.getTimesPerPeriod() - 1) + " may make it easier to keep.";
        }
        return "Completed " + Math.round(rate) + "% of scheduled days. Try a smaller version: one repetition " +
                "counts as a completion.";
    }

    private HabitDtos.HabitResponse toResponse(Habit habit, LocalDate today) {
        LocalDate from = today.minusDays(WINDOW_DAYS - 1L);
        List<HabitLog> logs = habitLogRepository.findByUserIdAndHabitIdAndLogDateBetween(
                habit.getUserId(), habit.getId(), today.minusDays(LOOKBACK_WEEKS * 7L), today);

        Set<LocalDate> completedDays = new HashSet<>();
        logs.stream().filter(HabitLog::isCompleted).forEach(log -> completedDays.add(log.getLogDate()));
        LocalDate lastCompletedDate = completedDays.stream().max(Comparator.naturalOrder()).orElse(null);

        int completed30 = 0;
        int scheduled30 = 0;
        for (LocalDate day = from; !day.isAfter(today); day = day.plusDays(1)) {
            if (isScheduled(habit, day)) {
                scheduled30++;
                if (completedDays.contains(day)) {
                    completed30++;
                }
            }
        }

        return new HabitDtos.HabitResponse(
                habit.getId(),
                habit.getName(),
                habit.getDescription(),
                habit.getCategory(),
                habit.getFrequencyType(),
                habit.getTimesPerPeriod(),
                com.lifeos.util.Csv.splitToList(habit.getTargetDays()).stream().map(Integer::valueOf).toList(),
                habit.getReminderTime(),
                habit.getColor(),
                habit.isArchived(),
                currentStreak(habit, completedDays, today),
                longestStreak(habit, completedDays),
                completed30,
                scheduled30,
                scheduled30 == 0 ? 0 : Math.round((completed30 * 1000.0 / scheduled30)) / 10.0,
                lastCompletedDate,
                completedDays.contains(today),
                habit.getCreatedAt());
    }

    private HabitDtos.HabitLogResponse toLogResponse(HabitLog log) {
        return new HabitDtos.HabitLogResponse(log.getId(), log.getHabitId(), log.getLogDate(),
                log.isCompleted(), log.getQuantity(), log.getNote());
    }

    @Transactional(readOnly = true)
    public long activeCount(String userId) {
        return habitRepository.countByUserIdAndArchivedFalse(userId);
    }
}