package com.lifeos.analytics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeos.dto.AnalyticsDtos;
import com.lifeos.dto.GoalDtos;
import com.lifeos.entity.Budget;
import com.lifeos.entity.Habit;
import com.lifeos.entity.Prediction;
import com.lifeos.entity.ProductivityMetric;
import com.lifeos.entity.Task;
import com.lifeos.entity.enums.PredictionType;
import com.lifeos.entity.enums.TaskStatus;
import com.lifeos.entity.enums.TransactionType;
import com.lifeos.repository.BudgetRepository;
import com.lifeos.repository.FinanceTransactionRepository;
import com.lifeos.repository.HabitLogRepository;
import com.lifeos.repository.HabitRepository;
import com.lifeos.repository.PredictionRepository;
import com.lifeos.repository.ProductivityMetricRepository;
import com.lifeos.repository.TaskRepository;
import com.lifeos.repository.TaskSpecifications;
import com.lifeos.service.GoalService;
import com.lifeos.service.UserZoneService;
import com.lifeos.util.DateSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Predicts risk rather than promising outcomes.
 *
 * <p>Each model is a small, explainable calculation over rows the user actually created — for example
 * a deadline risk that weighs the days remaining against the pace the user has recently completed
 * work at. Every prediction stores its factors plus a disclaimer stating that it describes recorded
 * behaviour rather than a guaranteed future. Predictions expire and are regenerated rather than
 * updated in place, so a stale figure can never be presented as a current one.</p>
 */
@Service
public class PredictionService {

    private static final Logger log = LoggerFactory.getLogger(PredictionService.class);
    private static final int DEFAULT_HORIZON_DAYS = 14;
    private static final int MIN_PRODUCTION_DAYS = 14;
    private static final String DISCLAIMER = "Estimated from your recorded activity only. Not a guarantee of "
            + "what will happen.";

    private final PredictionRepository predictionRepository;
    private final TaskRepository taskRepository;
    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;
    private final BudgetRepository budgetRepository;
    private final FinanceTransactionRepository transactionRepository;
    private final ProductivityMetricRepository metricRepository;
    private final GoalService goalService;
    private final UserZoneService userZoneService;
    private final ObjectMapper objectMapper;

    public PredictionService(PredictionRepository predictionRepository,
                             TaskRepository taskRepository,
                             HabitRepository habitRepository,
                             HabitLogRepository habitLogRepository,
                             BudgetRepository budgetRepository,
                             FinanceTransactionRepository transactionRepository,
                             ProductivityMetricRepository metricRepository,
                             GoalService goalService,
                             UserZoneService userZoneService,
                             ObjectMapper objectMapper) {
        this.predictionRepository = predictionRepository;
        this.taskRepository = taskRepository;
        this.habitRepository = habitRepository;
        this.habitLogRepository = habitLogRepository;
        this.budgetRepository = budgetRepository;
        this.transactionRepository = transactionRepository;
        this.metricRepository = metricRepository;
        this.goalService = goalService;
        this.userZoneService = userZoneService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AnalyticsDtos.PredictionResponse> active(String userId) {
        return predictionRepository.findActive(userId, Instant.now()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AnalyticsDtos.PredictionResponse> history(String userId, PredictionType type, int days) {
        Instant since = Instant.now().minus(Duration.ofDays(Math.min(180, Math.max(1, days))));
        return predictionRepository
                .findByUserIdAndPredictionTypeAndExpiresAtAfterOrderByProbabilityDesc(userId, type, since)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public void dismiss(String userId, String predictionId) {
        predictionRepository.findByIdAndUserId(predictionId, userId)
                .ifPresent(predictionRepository::delete);
    }

    @Transactional
    public int regenerate(String userId) {
        ZoneId zone = userZoneService.zoneOf(userId);
        LocalDate today = LocalDate.now(zone);
        int created = 0;
        created += deadlineRisk(userId, today, zone);
        created += budgetOverspend(userId, today);
        created += habitConsistency(userId, today);
        created += goalCompletion(userId, today);
        created += productivityChange(userId, today);
        return created;
    }

    // ----------------------------------------------------------------- models

    /**
     * Deadline risk: for each open task with a deadline inside the horizon, compare the days remaining
     * against the number of tasks the user has actually been finishing per day.
     */
    private int deadlineRisk(String userId, LocalDate today, ZoneId zone) {
        double dailyThroughput = recentThroughput(userId, today);
        if (dailyThroughput <= 0) {
            log.debug("Skipping deadline risk for {}: no completed-task history to measure pace against", userId);
            return 0;
        }
        Instant now = Instant.now();
        Instant windowStart = DateSupport.startOfDay(today, zone);
        Instant windowEnd = DateSupport.startOfDay(today.plusDays(DEFAULT_HORIZON_DAYS), zone);
        Specification<Task> spec = Specification.allOf(
                TaskSpecifications.ownedBy(userId),
                TaskSpecifications.withStatuses(List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS)),
                TaskSpecifications.withDeadlineRange(windowStart, windowEnd));

        List<String> keep = new ArrayList<>();
        for (Task task : taskRepository.findAll(spec, Pageable.unpaged())) {
            long daysLeft = ChronoUnit.DAYS.between(today, task.getDeadline().atZone(zone).toLocalDate());
            if (daysLeft < 0) {
                continue;
            }
            int estimate = task.getEstimatedMinutes() == null ? 30 : task.getEstimatedMinutes();
            double slotsNeeded = Math.max(0.5, estimate / 60.0);
            double slotsAvailable = daysLeft * dailyThroughput;
            int probability = (int) Math.max(5, Math.min(95,
                    Math.round(100 - (slotsAvailable / slotsNeeded) * 50.0)));
            if (probability < 30) {
                continue;
            }
            keep.addAll(store(userId, PredictionType.DEADLINE_RISK, "TASK", task.getId(),
                    "Deadline risk: " + task.getTitle(), probability,
                    (int) Math.min(95, 45 + Math.round(Math.abs(100 - probability) / 2.0)),
                    List.of("Days until deadline: " + daysLeft,
                            "Estimated effort: " + estimate + " min",
                            "Your recent completion rate: " + round(dailyThroughput) + " tasks per day"),
                    DEFAULT_HORIZON_DAYS, now));
        }
        return replace(userId, PredictionType.DEADLINE_RISK, keep);
    }

    /**
     * Budget overspend: projects the month's spend to month end using the average daily spend so far,
     * then compares the projection with the budget limit.
     */
    private int budgetOverspend(String userId, LocalDate today) {
        Instant now = Instant.now();
        List<String> keep = new ArrayList<>();
        LocalDate monthStart = today.withDayOfMonth(1);
        for (Budget budget : budgetRepository.findActiveOn(userId, today)) {
            BigDecimal limit = budget.getAmount() == null ? BigDecimal.ZERO : budget.getAmount();
            if (limit.signum() <= 0) {
                continue;
            }
            LocalDate start = budget.getStartDate().isAfter(monthStart) ? budget.getStartDate() : monthStart;
            BigDecimal spent = transactionRepository.sumAmountByCategory(userId, TransactionType.EXPENSE,
                    budget.getCategory(), start, today);
            if (spent == null || spent.signum() <= 0) {
                continue;
            }
            int elapsed = Math.max(1, (int) ChronoUnit.DAYS.between(start, today) + 1);
            BigDecimal projected = spent
                    .multiply(BigDecimal.valueOf((double) today.lengthOfMonth() / elapsed))
                    .setScale(2, RoundingMode.HALF_UP);
            if (projected.compareTo(limit) <= 0) {
                continue;
            }
            double overRatio = projected.subtract(limit).divide(limit, 4, RoundingMode.HALF_UP).doubleValue();
            int probability = (int) Math.min(95, 40 + Math.round(overRatio * 200));
            keep.addAll(store(userId, PredictionType.BUDGET_OVERSPEND, "BUDGET", budget.getId(),
                    "Budget overspend risk: " + budget.getCategory(), probability, 65,
                    List.of("Spent so far: " + spent.setScale(2, RoundingMode.HALF_UP),
                            "Projected for the month: " + projected,
                            "Budget limit: " + limit.setScale(2, RoundingMode.HALF_UP),
                            "Days measured: " + elapsed),
                    today.lengthOfMonth() - today.getDayOfMonth(), now));
        }
        return replace(userId, PredictionType.BUDGET_OVERSPEND, keep);
    }

    /** Habit consistency: flags habits whose recent completion rate sits well below their own target. */
    private int habitConsistency(String userId, LocalDate today) {
        Instant now = Instant.now();
        LocalDate from = today.minusDays(MIN_PRODUCTION_DAYS - 1L);
        LocalDate halfway = today.minusDays(MIN_PRODUCTION_DAYS / 2L);
        List<String> keep = new ArrayList<>();
        for (Habit habit : habitRepository.findByUserIdAndArchivedFalse(userId)) {
            long done = habitLogRepository.countByUserIdAndHabitIdAndCompletedTrueAndLogDateBetween(
                    userId, habit.getId(), from, today);
            int targetPerWeek = habitTarget(habit);
            double expected = targetPerWeek * 2.0;
            double rate = done / expected;
            if (rate >= 0.6) {
                continue;
            }
            keep.addAll(store(userId, PredictionType.HABIT_CONSISTENCY, "HABIT", habit.getId(),
                    "Habit consistency risk: " + habit.getName(),
                    (int) Math.max(20, Math.min(90, Math.round((1 - rate) * 100))), 55,
                    List.of("Check-ins in the last 14 days: " + done,
                            "Target: " + targetPerWeek + " per week",
                            "Current rate: " + Math.round(rate * 100) + "% of target",
                            "Check-ins in the earlier half: "
                                    + habitLogRepository.countByUserIdAndHabitIdAndCompletedTrueAndLogDateBetween(
                                    userId, habit.getId(), from, halfway)),
                    DEFAULT_HORIZON_DAYS, now));
        }
        return replace(userId, PredictionType.HABIT_CONSISTENCY, keep);
    }

    /** Goal completion: flags goals whose target date is close relative to the work still open. */
    private int goalCompletion(String userId, LocalDate today) {
        Instant now = Instant.now();
        List<String> keep = new ArrayList<>();
        for (GoalDtos.GoalSummary summary : goalService.summaries(userId)) {
            if (summary.targetDate() == null) {
                continue;
            }
            LocalDate target = summary.targetDate().atZone(ZoneId.of("UTC")).toLocalDate();
            long daysLeft = ChronoUnit.DAYS.between(today, target);
            long remaining = summary.taskCount() - summary.completedTaskCount();
            if (daysLeft <= 0 || daysLeft > DEFAULT_HORIZON_DAYS || remaining <= 0) {
                continue;
            }
            int probability = (int) Math.max(10, Math.min(90,
                    Math.round(100.0 - (daysLeft * 100.0) / (remaining * 3.0))));
            keep.addAll(store(userId, PredictionType.GOAL_COMPLETION, "GOAL", summary.id(),
                    "Goal completion risk: " + summary.title(), probability, 50,
                    List.of("Days until target date: " + daysLeft,
                            "Tasks still open: " + remaining,
                            "Recorded progress: " + summary.progress() + "%"),
                    (int) daysLeft, now));
        }
        return replace(userId, PredictionType.GOAL_COMPLETION, keep);
    }

    /** Productivity change: flags a sustained move in the daily score across the last 14 days. */
    private int productivityChange(String userId, LocalDate today) {
        Instant now = Instant.now();
        List<ProductivityMetric> metrics = metricRepository
                .findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(userId,
                        today.minusDays(MIN_PRODUCTION_DAYS - 1L), today);
        if (metrics.size() < MIN_PRODUCTION_DAYS) {
            log.debug("Skipping productivity change for {}: only {} days of metrics", userId, metrics.size());
            return 0;
        }
        int split = MIN_PRODUCTION_DAYS / 2;
        double firstHalf = metrics.subList(0, split).stream()
                .mapToInt(ProductivityMetric::getProductivityScore).average().orElse(0);
        double secondHalf = metrics.subList(split, MIN_PRODUCTION_DAYS).stream()
                .mapToInt(ProductivityMetric::getProductivityScore).average().orElse(0);
        if (firstHalf == 0) {
            return 0;
        }
        double change = (secondHalf - firstHalf) * 100.0 / firstHalf;
        if (Math.abs(change) < 15) {
            return 0;
        }
        boolean up = change > 0;
        List<String> keep = store(userId, PredictionType.PRODUCTIVITY_CHANGE, "USER", "overall",
                up ? "Productivity is likely to keep rising" : "Productivity is likely to keep falling",
                (int) Math.min(90, 40 + Math.round(Math.abs(change) / 2)), 55,
                List.of("First week average: " + round(firstHalf),
                        "Second week average: " + round(secondHalf),
                        "Change: " + (up ? "+" : "") + round(change) + "%"),
                DEFAULT_HORIZON_DAYS, now);
        return replace(userId, PredictionType.PRODUCTIVITY_CHANGE, keep);
    }

    // -------------------------------------------------------------- internals

    private double recentThroughput(String userId, LocalDate today) {
        List<ProductivityMetric> metrics = metricRepository
                .findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(userId, today.minusDays(13), today);
        if (metrics.isEmpty()) {
            return 0;
        }
        return metrics.stream().mapToInt(ProductivityMetric::getTasksCompleted).average().orElse(0);
    }

    private int habitTarget(Habit habit) {
        if (habit.getTargetDays() == null || habit.getTargetDays().isBlank()) {
            return 7;
        }
        try {
            return Math.max(1, Math.min(7, Integer.parseInt(habit.getTargetDays().strip())));
        } catch (NumberFormatException ex) {
            return 7;
        }
    }

    private List<String> store(String userId, PredictionType type, String subjectType, String subjectId,
                              String label, int probability, int confidence, List<String> factors,
                              int horizonDays, Instant now) {
        predictionRepository.findByUserIdAndPredictionTypeAndSubjectTypeAndSubjectId(
                        userId, type, subjectType, subjectId)
                .ifPresent(predictionRepository::delete);
        Prediction prediction = new Prediction();
        prediction.setUserId(userId);
        prediction.setPredictionType(type);
        prediction.setSubjectType(subjectType);
        prediction.setSubjectId(subjectId);
        prediction.setLabel(label);
        prediction.setProbability(Math.max(1, Math.min(99, probability)));
        prediction.setConfidence(Math.max(1, Math.min(99, confidence)));
        prediction.setFactors(writeFactors(factors));
        prediction.setHorizonDays(Math.max(1, horizonDays));
        prediction.setExpiresAt(now.plus(Duration.ofDays(1)));
        predictionRepository.save(prediction);
        return List.of(prediction.getId());
    }

    /** Removes stored predictions of this type that the current run did not reproduce. */
    private int replace(String userId, PredictionType type, List<String> keepIds) {
        predictionRepository.findActive(userId, Instant.now()).stream()
                .filter(prediction -> prediction.getPredictionType() == type)
                .filter(prediction -> !keepIds.contains(prediction.getId()))
                .toList()
                .forEach(predictionRepository::delete);
        return keepIds.size();
    }

    private String writeFactors(List<String> factors) {
        try {
            return objectMapper.writeValueAsString(factors);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private List<String> readFactors(String factors) {
        if (factors == null || factors.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(factors, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    public AnalyticsDtos.PredictionResponse toResponse(Prediction prediction) {
        return new AnalyticsDtos.PredictionResponse(
                prediction.getId(),
                prediction.getPredictionType().name(),
                prediction.getSubjectType(),
                prediction.getSubjectId(),
                prediction.getLabel(),
                prediction.getProbability(),
                prediction.getConfidence(),
                readFactors(prediction.getFactors()),
                prediction.getHorizonDays(),
                prediction.getCreatedAt(),
                prediction.getExpiresAt(),
                DISCLAIMER);
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}