package com.lifeos.service;

import com.lifeos.ai.AIProvider;
import com.lifeos.ai.AiMessage;
import com.lifeos.ai.AiProviderRegistry;
import com.lifeos.ai.AiRequest;
import com.lifeos.ai.AiResponse;
import com.lifeos.ai.Prompts;
import com.lifeos.dto.AiDtos;
import com.lifeos.dto.HabitDtos;
import com.lifeos.entity.CalendarEvent;
import com.lifeos.entity.Habit;
import com.lifeos.entity.Task;
import com.lifeos.entity.UserPreference;
import com.lifeos.entity.enums.EnergyRequirement;
import com.lifeos.entity.enums.Priority;
import com.lifeos.entity.enums.TaskStatus;
import com.lifeos.repository.CalendarEventRepository;
import com.lifeos.repository.TaskRepository;
import com.lifeos.repository.UserPreferenceRepository;
import com.lifeos.util.DateSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds a realistic day plan.
 *
 * <p>The schedule is computed deterministically first: fixed calendar events are placed, habits are
 * slotted, and remaining tasks are ordered by deadline, priority and energy fit, then packed into the
 * gaps. Because the layout is reproducible, an AI narrative can explain it without being able to
 * contradict it — the model never invents blocks, it only describes the blocks that already exist.</p>
 *
 * <p>Block ids are derived from the record each block stands for, so recomputing the same day returns the
 * same ids. That is what lets a client move a block and send the change back: {@link #applyEdits} rebuilds
 * the plan and matches the incoming edits against those stable ids.</p>
 */
@Service
public class DayPlanService {

    private static final int DEFAULT_AVAILABLE_MINUTES = 480;
    private static final int DEFAULT_FOCUS_BLOCK = 50;
    private static final int DEFAULT_BREAK = 10;
    private static final int MIN_BLOCK_MINUTES = 10;

    private final TaskRepository taskRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final HabitService habitService;
    private final UserPreferenceRepository preferenceRepository;
    private final AiProviderRegistry providerRegistry;
    private final UserZoneService userZoneService;

    public DayPlanService(TaskRepository taskRepository,
                          CalendarEventRepository calendarEventRepository,
                          HabitService habitService,
                          UserPreferenceRepository preferenceRepository,
                          AiProviderRegistry providerRegistry,
                          UserZoneService userZoneService) {
        this.taskRepository = taskRepository;
        this.calendarEventRepository = calendarEventRepository;
        this.habitService = habitService;
        this.preferenceRepository = preferenceRepository;
        this.providerRegistry = providerRegistry;
        this.userZoneService = userZoneService;
    }

    @Transactional(readOnly = true)
    public AiDtos.PlanDayResponse plan(String userId, AiDtos.PlanDayRequest request) {
        ZoneId zone = userZoneService.zoneOf(userId);
        LocalDate date = request.date() == null ? LocalDate.now(zone) : request.date();
        UserPreference preference = preferenceRepository.findByUserId(userId).orElse(null);

        int available = request.availableMinutes() != null ? request.availableMinutes() : DEFAULT_AVAILABLE_MINUTES;
        int focusBlock = request.focusBlockMinutes() != null ? request.focusBlockMinutes()
                : preference == null ? DEFAULT_FOCUS_BLOCK : preference.getPreferredFocusMinutes();
        int breakMinutes = request.breakMinutes() != null ? request.breakMinutes()
                : preference == null ? DEFAULT_BREAK : preference.getBreakMinutes();
        boolean includeHabits = request.includeHabits() == null || request.includeHabits();
        boolean includeBreaks = request.includeBreaks() != null && request.includeBreaks();
        int energy = request.energyLevel() == null ? 3 : request.energyLevel();
        List<String> excluded = request.excludeTaskIds() == null ? List.of() : request.excludeTaskIds();

        List<String> warnings = new ArrayList<>();
        List<AiDtos.PlanBlock> blocks = new ArrayList<>();
        List<String> unscheduled = new ArrayList<>();

        Instant dayStart = DateSupport.startOfDay(date, zone);
        Instant windowStart = dayStart.plus(Duration.ofMinutes(startMinutes(preference)));
        Instant windowEnd = dayStart.plus(Duration.ofMinutes(endMinutes(preference)));
        int windowMinutes = Math.max(MIN_BLOCK_MINUTES, (int) Duration.between(windowStart, windowEnd).toMinutes());
        int budgetMinutes = Math.min(available, windowMinutes);
        if (available > windowMinutes) {
            warnings.add("Your working window only holds " + windowMinutes
                    + " minutes, so the request for " + available + " minutes was capped.");
        }

        blocks.addAll(fixedEvents(userId, date, zone));
        if (includeHabits) {
            blocks.addAll(habitBlocks(userId, date, zone, windowStart, windowEnd));
        }

        List<Task> candidates = candidates(userId, date, zone, excluded);
        if (candidates.isEmpty()) {
            warnings.add("No open tasks are due on or before " + date + ".");
        }

        List<Interval> free = freeSlots(blocks, windowStart, windowEnd);
        int remaining = budgetMinutes - usedMinutes(blocks);

        for (Task task : candidates) {
            int estimate = task.getEstimatedMinutes() == null ? focusBlock : task.getEstimatedMinutes();
            if (remaining < MIN_BLOCK_MINUTES) {
                unscheduled.add(task.getId());
                continue;
            }
            if (estimate > remaining) {
                unscheduled.add(task.getId());
                continue;
            }
            Interval slot = firstSlot(free, estimate, focusBlock);
            if (slot == null) {
                unscheduled.add(task.getId());
                continue;
            }
            blocks.add(taskBlock(task, slot, estimate, energy, zone));
            remaining -= estimate + (includeBreaks ? breakMinutes : 0);
        }

        if (!unscheduled.isEmpty()) {
            warnings.add(unscheduled.size() + " task(s) did not fit: either they need more time than the day has "
                    + "left, or a fixed event occupies their slot. Nothing was silently dropped.");
        }
        blocks.sort(Comparator.comparing(AiDtos.PlanBlock::startAt));

        int scheduled = usedMinutes(blocks);
        int breaks = blocks.stream().mapToInt(block -> "BREAK".equals(block.kind()) ? block.durationMinutes() : 0).sum();
        double utilisation = budgetMinutes == 0 ? 0 : Math.round(scheduled * 1000.0 / budgetMinutes) / 10.0;

        String rationale = Boolean.TRUE.equals(request.useAi())
                ? explain(userId, date, blocks, unscheduled, zone)
                : "Ordered by deadline, then priority, then energy fit. Fixed events and habits were placed first.";

        return new AiDtos.PlanDayResponse(date, budgetMinutes, scheduled, breaks, utilisation,
                blocks, unscheduled, warnings, rationale,
                Boolean.TRUE.equals(request.useAi()) ? providerRegistry.active().name() : "rules");
    }

    @Transactional
    public AiDtos.PlanDayResponse applyEdits(String userId, AiDtos.PlanUpdateRequest request) {
        if (request.blocks() == null || request.blocks().isEmpty()) {
            throw new IllegalArgumentException("At least one block change is required");
        }
        AiDtos.PlanDayRequest rebuild = new AiDtos.PlanDayRequest(null, null, null, null, null, null, null,
                Boolean.TRUE, List.of());
        AiDtos.PlanDayResponse rebuilt = plan(userId, rebuild);

        Map<String, AiDtos.PlanBlock> byId = new LinkedHashMap<>();
        rebuilt.blocks().forEach(block -> byId.put(block.id(), block));

        Map<String, AiDtos.PlanBlock> updated = new LinkedHashMap<>();
        for (AiDtos.PlanUpdateBlockRequest change : request.blocks()) {
            AiDtos.PlanBlock original = byId.get(change.blockId());
            if (original == null) {
                throw new IllegalArgumentException("Unknown plan block: " + change.blockId());
            }
            Instant start = change.startAt() == null ? original.startAt() : change.startAt();
            Instant end = change.endAt() == null ? original.endAt() : change.endAt();
            if (!end.isAfter(start)) {
                throw new IllegalArgumentException("A plan block must end after it starts");
            }
            updated.put(change.blockId(), new AiDtos.PlanBlock(
                    original.id(), original.date(), start, end,
                    (int) Duration.between(start, end).toMinutes(),
                    original.kind(),
                    change.title() == null || change.title().isBlank() ? original.title() : change.title(),
                    original.detail(),
                    change.taskId() == null ? original.taskId() : change.taskId(),
                    original.goalId(), original.habitId(), original.priority(), original.energyFit(),
                    original.movable(), change.locked() != null && change.locked()));
        }

        List<AiDtos.PlanBlock> merged = rebuilt.blocks().stream()
                .map(block -> updated.getOrDefault(block.id(), block))
                .sorted(Comparator.comparing(AiDtos.PlanBlock::startAt))
                .toList();

        int scheduled = merged.stream().mapToInt(AiDtos.PlanBlock::durationMinutes).sum();
        int breaks = merged.stream().mapToInt(block -> "BREAK".equals(block.kind()) ? block.durationMinutes() : 0).sum();
        double utilisation = rebuilt.availableMinutes() == 0 ? 0
                : Math.round(scheduled * 1000.0 / rebuilt.availableMinutes()) / 10.0;

        return new AiDtos.PlanDayResponse(rebuilt.date(), rebuilt.availableMinutes(), scheduled, breaks, utilisation,
                merged, rebuilt.unscheduledTaskIds(),
                List.of("Edits are held for this session. Confirm the plan to write it to your calendar."),
                "Your manual edits were applied on top of the generated plan.",
                "user-edited");
    }

    // --------------------------------------------------------------- planning

    private List<Task> candidates(String userId, LocalDate date, ZoneId zone, List<String> excluded) {
        Instant endOfDay = DateSupport.endOfDay(date, zone);
        Instant now = Instant.now();
        return taskRepository.findByUserIdAndStatusInAndDeletedAtIsNull(userId,
                        List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS)).stream()
                .filter(task -> !excluded.contains(task.getId()))
                .filter(task -> task.getDeadline() == null || !task.getDeadline().isAfter(endOfDay))
                .filter(task -> task.getRecurrenceRule() == null
                        || task.getDeadline() != null || !task.getDeadline().isBefore(now))
                .sorted(Comparator
                        .comparing((Task task) -> deadlineRank(task, date))
                        .thenComparing(task -> priorityRank(task.getPriority()))
                        .thenComparing(task -> energyRank(task.getEnergyRequirement()))
                        .thenComparing(Task::getPosition))
                .toList();
    }

    private int deadlineRank(Task task, LocalDate date) {
        if (task.getDeadline() == null) {
            return Integer.MAX_VALUE;
        }
        LocalDate deadline = task.getDeadline().atZone(ZoneId.of("UTC")).toLocalDate();
        return Math.toIntExact(ChronoUnit.DAYS.between(date, deadline));
    }

    private int priorityRank(Priority priority) {
        return 4 - (priority == null ? Priority.MEDIUM.weight() : priority.weight());
    }

    private int energyRank(EnergyRequirement requirement) {
        return switch (requirement == null ? EnergyRequirement.MEDIUM : requirement) {
            case HIGH -> 0;
            case MEDIUM -> 1;
            case LOW -> 2;
        };
    }

    private AiDtos.PlanBlock taskBlock(Task task, Interval slot, int estimate, int energy, ZoneId zone) {
        return new AiDtos.PlanBlock(
                "task:" + task.getId(),
                slot.start().atZone(zone).toLocalDate(),
                slot.start(),
                slot.start().plus(estimate, ChronoUnit.MINUTES),
                estimate,
                "TASK",
                task.getTitle(),
                task.getDescription() == null ? null : truncate(task.getDescription(), 200),
                task.getId(),
                task.getGoalId(),
                null,
                task.getPriority() == null ? "MEDIUM" : task.getPriority().name(),
                energyFit(task.getEnergyRequirement(), energy),
                task.getDeadline() == null,
                false);
    }

    private String energyFit(EnergyRequirement requirement, int energyLevel) {
        EnergyRequirement needed = requirement == null ? EnergyRequirement.MEDIUM : requirement;
        boolean matching = switch (needed) {
            case HIGH -> energyLevel >= 4;
            case MEDIUM -> energyLevel == 3 || energyLevel == 2;
            case LOW -> energyLevel <= 2;
        };
        return matching ? "MATCH" : "SUBOPTIMAL";
    }

    private List<AiDtos.PlanBlock> fixedEvents(String userId, LocalDate date, ZoneId zone) {
        Instant from = DateSupport.startOfDay(date, zone);
        Instant to = DateSupport.startOfDay(date.plusDays(1), zone);
        List<AiDtos.PlanBlock> blocks = new ArrayList<>();
        for (CalendarEvent event : calendarEventRepository.findByUserIdAndStartAtBetweenOrderByStartAtAsc(
                userId, from, to)) {
            Instant end = event.getEndAt() == null ? event.getStartAt().plus(1, ChronoUnit.HOURS) : event.getEndAt();
            blocks.add(new AiDtos.PlanBlock(
                    "event:" + event.getId(),
                    date,
                    event.getStartAt(),
                    end,
                    (int) Math.max(0, Duration.between(event.getStartAt(), end).toMinutes()),
                    "EVENT",
                    event.getTitle(),
                    event.getLocation() == null ? event.getDescription() : event.getLocation(),
                    event.getTaskId(),
                    event.getGoalId(),
                    event.getHabitId(),
                    "FIXED",
                    "FIXED",
                    false,
                    true));
        }
        return blocks;
    }

    private List<AiDtos.PlanBlock> habitBlocks(String userId, LocalDate date, ZoneId zone,
                                               Instant windowStart, Instant windowEnd) {
        List<AiDtos.PlanBlock> blocks = new ArrayList<>();
        List<HabitDtos.HabitResponse> habits = habitService.list(userId, false);
        Instant cursor = windowStart;
        for (HabitDtos.HabitResponse habit : habits) {
            if (!habitService.isScheduled(requireHabit(userId, habit.id()), date)) {
                continue;
            }
            cursor = cursor.plus(15, ChronoUnit.MINUTES);
            if (cursor.isAfter(windowEnd)) {
                break;
            }
            blocks.add(new AiDtos.PlanBlock(
                    "habit:" + habit.id(),
                    date,
                    cursor,
                    cursor.plus(10, ChronoUnit.MINUTES),
                    10,
                    "HABIT",
                    habit.name(),
                    "Scheduled on " + habit.targetDays() + " days per week",
                    null,
                    null,
                    habit.id(),
                    "ROUTINE",
                    "LOW",
                    true,
                    false));
            cursor = cursor.plus(10, ChronoUnit.MINUTES);
        }
        return blocks;
    }

    private Habit requireHabit(String userId, String habitId) {
        return habitService.requireOwned(userId, habitId);
    }

    private List<Interval> freeSlots(List<AiDtos.PlanBlock> blocks, Instant windowStart, Instant windowEnd) {
        List<Interval> busy = blocks.stream()
                .map(block -> new Interval(block.startAt(), block.endAt()))
                .sorted(Comparator.comparing(Interval::start))
                .toList();
        List<Interval> free = new ArrayList<>();
        Instant cursor = windowStart;
        for (Interval interval : busy) {
            if (interval.start().isAfter(cursor)) {
                free.add(new Interval(cursor, interval.start()));
            }
            if (interval.end().isAfter(cursor)) {
                cursor = interval.end();
            }
        }
        if (cursor.isBefore(windowEnd)) {
            free.add(new Interval(cursor, windowEnd));
        }
        return free;
    }

    private Interval firstSlot(List<Interval> free, int estimate, int focusBlock) {
        for (Interval interval : free) {
            long available = Duration.between(interval.start(), interval.end()).toMinutes();
            if (available >= estimate + focusBlock && estimate <= focusBlock) {
                return new Interval(interval.start(), interval.start().plus(estimate, ChronoUnit.MINUTES));
            }
        }
        for (Interval interval : free) {
            long available = Duration.between(interval.start(), interval.end()).toMinutes();
            if (available >= estimate) {
                return new Interval(interval.start(), interval.start().plus(estimate, ChronoUnit.MINUTES));
            }
        }
        return null;
    }

    private int usedMinutes(List<AiDtos.PlanBlock> blocks) {
        return blocks.stream().mapToInt(AiDtos.PlanBlock::durationMinutes).sum();
    }

    private int startMinutes(UserPreference preference) {
        LocalTime start = preference == null || preference.getDayStart() == null
                ? LocalTime.of(8, 0) : preference.getDayStart();
        return start.toSecondOfDay() / 60;
    }

    private int endMinutes(UserPreference preference) {
        LocalTime end = preference == null || preference.getDayEnd() == null
                ? LocalTime.of(22, 0) : preference.getDayEnd();
        return end.toSecondOfDay() / 60;
    }

    private String explain(String userId, LocalDate date, List<AiDtos.PlanBlock> blocks,
                           List<String> unscheduled, ZoneId zone) {
        List<String> lines = new ArrayList<>();
        lines.add("Date: " + date);
        lines.add("Planned minutes: " + usedMinutes(blocks));
        lines.add("Unscheduled tasks: " + unscheduled.size());
        for (AiDtos.PlanBlock block : blocks) {
            lines.add(block.startAt().atZone(zone).toLocalTime() + " " + block.kind()
                    + " " + block.title() + " (" + block.durationMinutes() + " min"
                    + (block.energyFit() == null ? "" : ", energy " + block.energyFit()) + ")");
        }
        AIProvider provider = providerRegistry.active();
        AiResponse response = provider.complete(AiRequest.of(List.of(
                AiMessage.system(Prompts.PLAN_RATIONALE),
                AiMessage.user(Prompts.contextBlock("THE SCHEDULE", lines)
                        + "\nExplain the ordering only. Do not add or remove anything."))));
        return response.text() == null || response.text().isBlank()
                ? "Ordered by deadline, then priority, then energy fit."
                : response.text();
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private record Interval(Instant start, Instant end) {
    }
}
