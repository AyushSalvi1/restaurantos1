package com.lifeos.service;

import com.lifeos.analytics.BalanceScoreService;
import com.lifeos.analytics.PredictionService;
import com.lifeos.dto.AiDtos;
import com.lifeos.dto.AnalyticsDtos;
import com.lifeos.entity.Budget;
import com.lifeos.entity.Habit;
import com.lifeos.entity.Task;
import com.lifeos.entity.enums.TaskStatus;
import com.lifeos.entity.enums.TransactionType;
import com.lifeos.repository.BudgetRepository;
import com.lifeos.repository.FinanceTransactionRepository;
import com.lifeos.repository.HabitLogRepository;
import com.lifeos.repository.HabitRepository;
import com.lifeos.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Produces the "what should I do next" list on the dashboard.
 *
 * <p>Recommendations are rule-based, and each one carries the measurements that triggered it plus a
 * real link into the screen where the user can act. Nothing is suggested to a user who has not
 * recorded enough to justify it: an empty list is more honest than a generic tip.</p>
 */
@Service
public class RecommendationService {

    private static final int MIN_FOR_TASK_PRESSURE = 3;
    private static final int MIN_FOR_HABIT_SLIP = 1;
    private static final int BUDGET_WATCH_PERCENT = 70;
    private static final int WEAK_DIMENSION_THRESHOLD = 50;
    private static final int MAX_RESULTS = 6;

    private final TaskRepository taskRepository;
    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;
    private final BudgetRepository budgetRepository;
    private final FinanceTransactionRepository transactionRepository;
    private final PredictionService predictionService;
    private final BalanceScoreService balanceScoreService;
    private final UserZoneService userZoneService;

    public RecommendationService(TaskRepository taskRepository,
                                 HabitRepository habitRepository,
                                 HabitLogRepository habitLogRepository,
                                 BudgetRepository budgetRepository,
                                 FinanceTransactionRepository transactionRepository,
                                 PredictionService predictionService,
                                 BalanceScoreService balanceScoreService,
                                 UserZoneService userZoneService) {
        this.taskRepository = taskRepository;
        this.habitRepository = habitRepository;
        this.habitLogRepository = habitLogRepository;
        this.budgetRepository = budgetRepository;
        this.transactionRepository = transactionRepository;
        this.predictionService = predictionService;
        this.balanceScoreService = balanceScoreService;
        this.userZoneService = userZoneService;
    }

    @Transactional(readOnly = true)
    public AiDtos.RecommendationsResponse recommend(String userId) {
        ZoneId zone = userZoneService.zoneOf(userId);
        LocalDate today = LocalDate.now(zone);
        List<AiDtos.Recommendation> recommendations = new ArrayList<>();

        recommendations.addAll(overduePressure(userId, zone));
        recommendations.addAll(deadlineCrowding(userId, zone, today));
        recommendations.addAll(habitSlips(userId, today));
        recommendations.addAll(budgetWatch(userId, today));
        recommendations.addAll(weakDimensions(userId));
        recommendations.addAll(predictionFollowUps(userId));

        recommendations.sort(Comparator.comparingInt(AiDtos.Recommendation::score).reversed());
        return new AiDtos.RecommendationsResponse(
                recommendations.stream().limit(MAX_RESULTS).toList(),
                Instant.now().toString(),
                "rules");
    }

    private List<AiDtos.Recommendation> overduePressure(String userId, ZoneId zone) {
        List<Task> overdue = taskRepository.findOverdue(userId, List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS),
                Instant.now());
        if (overdue.size() < MIN_FOR_TASK_PRESSURE) {
            return List.of();
        }
        List<String> data = new ArrayList<>();
        data.add("Overdue tasks: " + overdue.size());
        overdue.stream()
                .filter(task -> task.getDeadline() != null)
                .min(Comparator.comparing(Task::getDeadline))
                .ifPresent(oldest -> data.add("Oldest deadline: "
                        + oldest.getDeadline().atZone(zone).toLocalDate()));
        return List.of(new AiDtos.Recommendation(
                UUID.randomUUID().toString(),
                "OVERDUE_TASKS",
                overdue.size() + " tasks are past their deadline",
                "Starting with the oldest usually unblocks the most, since later work on the same project "
                        + "tends to depend on it.",
                Math.min(100, 60 + overdue.size() * 3),
                "Review overdue tasks",
                "/tasks?filter=overdue",
                data));
    }

    private List<AiDtos.Recommendation> deadlineCrowding(String userId, ZoneId zone, LocalDate today) {
        Instant limit = today.plusDays(2).atStartOfDay(zone).toInstant();
        List<Task> dueSoon = taskRepository.findDueBetween(userId,
                List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS), Instant.now(), limit);
        if (dueSoon.size() < MIN_FOR_TASK_PRESSURE) {
            return List.of();
        }
        long estimated = dueSoon.stream()
                .mapToLong(task -> task.getEstimatedMinutes() == null ? 0 : task.getEstimatedMinutes())
                .sum();
        return List.of(new AiDtos.Recommendation(
                UUID.randomUUID().toString(),
                "DEADLINE_CROWDING",
                dueSoon.size() + " tasks are due within 48 hours",
                "That is roughly " + Math.round(estimated / 60.0) + " hours of estimated work. Planning it now "
                        + "is easier than recovering from it later.",
                Math.min(95, 50 + dueSoon.size() * 2),
                "Plan the next day",
                "/ai/plan-day",
                List.of("Tasks due within 48 hours: " + dueSoon.size(),
                        "Combined estimate: " + estimated + " minutes")));
    }

    private List<AiDtos.Recommendation> habitSlips(String userId, LocalDate today) {
        LocalDate from = today.minusDays(13);
        List<AiDtos.Recommendation> out = new ArrayList<>();
        for (Habit habit : habitRepository.findByUserIdAndArchivedFalse(userId)) {
            long lastWeek = habitLogRepository.countByUserIdAndHabitIdAndCompletedTrueAndLogDateBetween(
                    userId, habit.getId(), today.minusDays(6), today);
            long priorWeek = habitLogRepository.countByUserIdAndHabitIdAndCompletedTrueAndLogDateBetween(
                    userId, habit.getId(), from, today.minusDays(7));
            if (priorWeek <= MIN_FOR_HABIT_SLIP || lastWeek >= priorWeek) {
                continue;
            }
            int dropPercent = (int) Math.round((priorWeek - lastWeek) * 100.0 / priorWeek);
            out.add(new AiDtos.Recommendation(
                    UUID.randomUUID().toString(),
                    "HABIT_SLIP",
                    "\"" + habit.getName() + "\" is below its usual pace",
                    "You logged it " + lastWeek + " time(s) this week against " + priorWeek + " the week before. "
                            + "Reducing the target for one week is usually easier than restarting at full size.",
                    Math.min(95, 45 + dropPercent / 2),
                    "Log the habit",
                    "/habits",
                    List.of("Last 7 days: " + lastWeek,
                            "Previous 7 days: " + priorWeek,
                            "Target: " + habit.getTargetDays() + " days per week")));
        }
        return out;
    }

    private List<AiDtos.Recommendation> budgetWatch(String userId, LocalDate today) {
        LocalDate monthStart = today.withDayOfMonth(1);
        List<AiDtos.Recommendation> out = new ArrayList<>();
        for (Budget budget : budgetRepository.findActiveOn(userId, today)) {
            BigDecimal limit = budget.getAmount() == null ? BigDecimal.ZERO : budget.getAmount();
            if (limit.signum() <= 0) {
                continue;
            }
            BigDecimal spent = transactionRepository.sumAmountByCategory(userId, TransactionType.EXPENSE,
                    budget.getCategory(), monthStart, today);
            if (spent == null) {
                spent = BigDecimal.ZERO;
            }
            double usage = spent.doubleValue() / limit.doubleValue() * 100.0;
            if (usage < BUDGET_WATCH_PERCENT) {
                continue;
            }
            out.add(new AiDtos.Recommendation(
                    UUID.randomUUID().toString(),
                    "BUDGET_WATCH",
                    budget.getCategory() + " spending is at " + Math.round(usage) + "% of its budget",
                    usage >= 100
                            ? "This budget has been passed. Recording what drove it makes next month easier."
                            : "Still inside the budget, but the trend is worth a look before the month ends.",
                    Math.min(95, 40 + (int) Math.round(usage / 2)),
                    "Open finance",
                    "/finance",
                    List.of("Spent this month: " + spent.setScale(2, RoundingMode.HALF_UP),
                            "Budget: " + limit.setScale(2, RoundingMode.HALF_UP),
                            "Usage: " + Math.round(usage) + "%")));
        }
        return out;
    }

    private List<AiDtos.Recommendation> weakDimensions(String userId) {
        AnalyticsDtos.BalanceScore score = balanceScoreService.compute(userId);
        List<AiDtos.Recommendation> out = new ArrayList<>();
        for (AnalyticsDtos.BalanceDimension dimension : score.dimensions()) {
            if (!dimension.sufficientData() || dimension.score() >= WEAK_DIMENSION_THRESHOLD) {
                continue;
            }
            out.add(new AiDtos.Recommendation(
                    UUID.randomUUID().toString(),
                    "BALANCE_GAP",
                    dimension.label() + " is the weakest area at " + dimension.score() + " of 100",
                    "The other dimensions are carrying more of your week than this one. That describes where your "
                            + "effort is going; it is not a verdict on you.",
                    Math.min(90, 40 + (WEAK_DIMENSION_THRESHOLD - dimension.score())),
                    "See the breakdown",
                    "/analytics/balance",
                    dimension.inputs().isEmpty()
                            ? List.of("Score: " + dimension.score())
                            : dimension.inputs()));
        }
        return out;
    }

    private List<AiDtos.Recommendation> predictionFollowUps(String userId) {
        return predictionService.active(userId).stream()
                .filter(prediction -> prediction.probability() >= 50)
                .map(prediction -> new AiDtos.Recommendation(
                        prediction.id(),
                        "PREDICTION_" + prediction.predictionType(),
                        prediction.label(),
                        prediction.disclaimer(),
                        Math.min(90, prediction.probability()),
                        "Open " + prediction.subjectType().toLowerCase(Locale.ROOT),
                        "/analytics/predictions",
                        prediction.factors()))
                .toList();
    }
}
