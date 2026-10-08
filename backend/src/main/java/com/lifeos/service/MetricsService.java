package com.lifeos.service;

import com.lifeos.entity.ProductivityMetric;
import com.lifeos.entity.Task;
import com.lifeos.entity.enums.TaskStatus;
import com.lifeos.repository.CalendarEventRepository;
import com.lifeos.repository.FinanceTransactionRepository;
import com.lifeos.repository.FocusSessionRepository;
import com.lifeos.repository.HabitLogRepository;
import com.lifeos.repository.HabitRepository;
import com.lifeos.repository.LearningSessionRepository;
import com.lifeos.repository.ProductivityMetricRepository;
import com.lifeos.repository.TaskRepository;
import com.lifeos.repository.TaskSpecifications;
import com.lifeos.util.DateSupport;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maintains the daily {@link ProductivityMetric} rollup that powers analytics, insights and the
 * dashboard score. Incremental updates happen on write; a nightly job backfills anything missed.
 *
 * <p>The score weights are fixed and published so a user can always see how it was computed.</p>
 */
@Service
public class MetricsService {

    static final double WEIGHT_TASK_COMPLETION = 40;
    static final double WEIGHT_FOCUS = 25;
    static final double WEIGHT_HABITS = 20;
    static final double WEIGHT_STUDY = 15;
    static final double TARGET_FOCUS_MINUTES = 120;
    static final double TARGET_STUDY_MINUTES = 60;

    private final ProductivityMetricRepository metricRepository;
    private final TaskRepository taskRepository;
    private final FocusSessionRepository focusSessionRepository;
    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;
    private final LearningSessionRepository learningSessionRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final FinanceTransactionRepository transactionRepository;
    private final UserZoneService userZoneService;

    public MetricsService(ProductivityMetricRepository metricRepository,
                          TaskRepository taskRepository,
                          FocusSessionRepository focusSessionRepository,
                          HabitRepository habitRepository,
                          HabitLogRepository habitLogRepository,
                          LearningSessionRepository learningSessionRepository,
                          CalendarEventRepository calendarEventRepository,
                          FinanceTransactionRepository transactionRepository,
                          UserZoneService userZoneService) {
        this.metricRepository = metricRepository;
        this.taskRepository = taskRepository;
        this.focusSessionRepository = focusSessionRepository;
        this.habitRepository = habitRepository;
        this.habitLogRepository = habitLogRepository;
        this.learningSessionRepository = learningSessionRepository;
        this.calendarEventRepository = calendarEventRepository;
        this.transactionRepository = transactionRepository;
        this.userZoneService = userZoneService;
    }

    @Transactional
    public ProductivityMetric forDate(String userId, LocalDate date) {
        return metricRepository.findByUserIdAndMetricDate(userId, date)
                .orElseGet(() -> {
                    ProductivityMetric metric = new ProductivityMetric();
                    metric.setUserId(userId);
                    metric.setMetricDate(date);
                    return metricRepository.save(metric);
                });
    }

    @Transactional
    public void recordTaskCreated(String userId, Task task) {
        ZoneId zone = userZoneService.zoneOf(userId);
        LocalDate date = task.getCreatedAt() == null ? LocalDate.now(zone)
                : DateSupport.toLocalDate(task.getCreatedAt(), zone);
        ProductivityMetric metric = forDate(userId, date);
        metric.setTasksCreated(metric.getTasksCreated() + 1);
        metric.setTasksPlanned(Math.max(metric.getTasksPlanned(),
                (int) taskRepository.countByUserIdAndStatusAndDeletedAtIsNull(userId, TaskStatus.TODO)
                        + (int) taskRepository.countByUserIdAndStatusAndDeletedAtIsNull(userId, TaskStatus.IN_PROGRESS)));
        recomputeScore(metric);
        metricRepository.save(metric);
    }

    @Transactional
    public void recordStatusChange(String userId, Task task, TaskStatus previousStatus) {
        ZoneId zone = userZoneService.zoneOf(userId);
        Instant reference = task.getCompletedAt() != null ? task.getCompletedAt()
                : (task.getUpdatedAt() != null ? task.getUpdatedAt() : Instant.now());
        LocalDate date = DateSupport.toLocalDate(reference, zone);
        ProductivityMetric metric = forDate(userId, date);

        if (task.getStatus() == TaskStatus.COMPLETED && previousStatus != TaskStatus.COMPLETED) {
            metric.setTasksCompleted(metric.getTasksCompleted() + 1);
            metric.setCompletedMinutes(metric.getCompletedMinutes() + task.getActualMinutes());
            if (task.getEstimatedMinutes() != null) {
                metric.setPlannedMinutes(metric.getPlannedMinutes() + task.getEstimatedMinutes());
            }
        } else if (previousStatus == TaskStatus.COMPLETED && task.getStatus() != TaskStatus.COMPLETED) {
            metric.setTasksCompleted(Math.max(0, metric.getTasksCompleted() - 1));
            metric.setCompletedMinutes(Math.max(0, metric.getCompletedMinutes() - task.getActualMinutes()));
        } else {
            return;
        }
        recomputeScore(metric);
        metricRepository.save(metric);
    }

    @Transactional
    public void recordHabitCompletion(String userId, LocalDate date, int completedDelta) {
        ProductivityMetric metric = forDate(userId, date);
        metric.setHabitsCompleted(Math.max(0, metric.getHabitsCompleted() + completedDelta));
        metric.setHabitsPlanned(habitRepository.findActiveForUser(userId).size());
        recomputeScore(metric);
        metricRepository.save(metric);
    }

    @Transactional
    public void recordFocusMinutes(String userId, LocalDate date, int minutes) {
        ProductivityMetric metric = forDate(userId, date);
        metric.setFocusMinutes(metric.getFocusMinutes() + Math.max(0, minutes));
        recomputeScore(metric);
        metricRepository.save(metric);
    }

    @Transactional
    public void recordStudyMinutes(String userId, LocalDate date, int minutes) {
        ProductivityMetric metric = forDate(userId, date);
        metric.setStudyMinutes(metric.getStudyMinutes() + Math.max(0, minutes));
        recomputeScore(metric);
        metricRepository.save(metric);
    }

    /** Recomputes a single day from the underlying records; used by the nightly rollup. */
    @Transactional
    public ProductivityMetric recompute(String userId, LocalDate date) {
        ZoneId zone = userZoneService.zoneOf(userId);
        Instant from = DateSupport.startOfDay(date, zone);
        Instant to = DateSupport.endOfDay(date, zone);

        ProductivityMetric metric = forDate(userId, date);
        metric.setTasksCreated((int) taskRepository.findAll(
                Specification2.createdBetween(userId, from, to), Pageable.unpaged()).getTotalElements());
        metric.setTasksCompleted((int) taskRepository.countCompletedBetween(userId,
                List.of(TaskStatus.COMPLETED), from, to));
        metric.setFocusMinutes((int) focusSessionRepository.sumMinutesBetween(userId, from, to));
        metric.setStudyMinutes((int) learningSessionRepository.sumMinutesBetween(userId, from, to));
        metric.setHabitsCompleted((int) habitLogRepository.countCompletedBetween(userId, date, date));
        metric.setHabitsPlanned(habitRepository.findActiveForUser(userId).size());
        metric.setEventsCount((int) calendarEventRepository.countBetween(userId, from, to));
        metric.setTransactionsCount(transactionRepository.findInRange(userId, date, date).size());
        metric.setPlannedMinutes((int) taskRepository.sumEstimatedMinutes(userId,
                List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED)));
        metric.setCompletedMinutes((int) taskRepository.sumActualMinutesBetween(userId, from, to));
        recomputeScore(metric);
        return metricRepository.save(metric);
    }

    @Transactional
    public int rebuildRange(String userId, LocalDate from, LocalDate to) {
        ZoneId zone = userZoneService.zoneOf(userId);
        LocalDate today = LocalDate.now(zone);
        int rebuilt = 0;
        for (LocalDate day : DateSupport.daysBetween(from, to)) {
            if (day.isAfter(today)) {
                continue;
            }
            recompute(userId, day);
            rebuilt++;
        }
        return rebuilt;
    }

    @Transactional(readOnly = true)
    public List<ProductivityMetric> range(String userId, LocalDate from, LocalDate to) {
        return metricRepository.findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(userId, from, to);
    }

    @Transactional(readOnly = true)
    public ProductivityMetric forDateReadOnly(String userId, LocalDate date) {
        return metricRepository.findByUserIdAndMetricDate(userId, date).orElse(null);
    }

    @Transactional(readOnly = true)
    public ProductivityMetric today(String userId) {
        return forDateReadOnly(userId, LocalDate.now(userZoneService.zoneOf(userId)));
    }

    /** Fills every day in the range so charts never show gaps that were never computed. */
    @Transactional(readOnly = true)
    public List<ProductivityMetric> rangeFilled(String userId, LocalDate from, LocalDate to) {
        Map<LocalDate, ProductivityMetric> byDate = range(userId, from, to).stream()
                .collect(Collectors.toMap(ProductivityMetric::getMetricDate, m -> m, (a, b) -> a, HashMap::new));
        List<ProductivityMetric> result = new ArrayList<>();
        for (LocalDate day : DateSupport.daysBetween(from, to)) {
            ProductivityMetric existing = byDate.get(day);
            if (existing == null) {
                existing = new ProductivityMetric();
                existing.setUserId(userId);
                existing.setMetricDate(day);
            }
            result.add(existing);
        }
        return result;
    }

    static void recomputeScore(ProductivityMetric metric) {
        int completionTarget = Math.max(1, metric.getTasksPlanned() > 0 ? metric.getTasksPlanned() : 3);
        double completion = Math.min(1.0, metric.getTasksCompleted() / (double) completionTarget);
        double focus = Math.min(1.0, metric.getFocusMinutes() / TARGET_FOCUS_MINUTES);
        double habits = metric.getHabitsPlanned() == 0
                ? 0
                : Math.min(1.0, metric.getHabitsCompleted() / (double) metric.getHabitsPlanned());
        double study = Math.min(1.0, metric.getStudyMinutes() / TARGET_STUDY_MINUTES);
        double score = (completion * WEIGHT_TASK_COMPLETION)
                + (focus * WEIGHT_FOCUS)
                + (habits * WEIGHT_HABITS)
                + (study * WEIGHT_STUDY);
        metric.setProductivityScore((int) Math.round(Math.min(100, Math.max(0, score))));
    }

    /** Small helper so the specification is composed with the ownership rule in one expression. */
    private static final class Specification2 {
        private Specification2() {
        }

        static org.springframework.data.jpa.domain.Specification<Task> createdBetween(String userId, Instant from, Instant to) {
            return org.springframework.data.jpa.domain.Specification.allOf(
                    TaskSpecifications.ownedBy(userId),
                    TaskSpecifications.createdBetween(from, to));
        }
    }
}