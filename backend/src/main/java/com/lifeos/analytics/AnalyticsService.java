package com.lifeos.analytics;

import com.lifeos.dto.AnalyticsDtos;
import com.lifeos.dto.FinanceDtos;
import com.lifeos.entity.ProductivityMetric;
import com.lifeos.entity.enums.TransactionType;
import com.lifeos.repository.FinanceTransactionRepository;
import com.lifeos.repository.FocusSessionRepository;
import com.lifeos.repository.HabitLogRepository;
import com.lifeos.service.FinanceService;
import com.lifeos.service.GoalService;
import com.lifeos.service.HabitService;
import com.lifeos.service.MetricsService;
import com.lifeos.service.UserZoneService;
import com.lifeos.util.DateSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.ToIntFunction;

/**
 * Read-only aggregation across productivity, focus, learning, habits, goals and finance.
 * Every figure is computed from stored rows; empty series stay empty rather than being
 * substituted with a plausible-looking placeholder.
 */
@Service
public class AnalyticsService {

    private static final int MAX_RANGE_DAYS = 730;

    private final MetricsService metricsService;
    private final TaskCountProvider taskCountProvider;
    private final FocusSessionRepository focusSessionRepository;
    private final HabitLogRepository habitLogRepository;
    private final FinanceTransactionRepository transactionRepository;
    private final GoalService goalService;
    private final HabitService habitService;
    private final FinanceService financeService;
    private final BalanceScoreService balanceScoreService;
    private final PredictionService predictionService;
    private final InsightService insightService;
    private final UserZoneService userZoneService;

    public AnalyticsService(MetricsService metricsService,
                            TaskCountProvider taskCountProvider,
                            FocusSessionRepository focusSessionRepository,
                            HabitLogRepository habitLogRepository,
                            FinanceTransactionRepository transactionRepository,
                            GoalService goalService,
                            HabitService habitService,
                            FinanceService financeService,
                            BalanceScoreService balanceScoreService,
                            PredictionService predictionService,
                            InsightService insightService,
                            UserZoneService userZoneService) {
        this.metricsService = metricsService;
        this.taskCountProvider = taskCountProvider;
        this.focusSessionRepository = focusSessionRepository;
        this.habitLogRepository = habitLogRepository;
        this.transactionRepository = transactionRepository;
        this.goalService = goalService;
        this.habitService = habitService;
        this.financeService = financeService;
        this.balanceScoreService = balanceScoreService;
        this.predictionService = predictionService;
        this.insightService = insightService;
        this.userZoneService = userZoneService;
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.RangeQuery resolveRange(String userId, LocalDate from, LocalDate to) {
        ZoneId zone = userZoneService.zoneOf(userId);
        LocalDate today = LocalDate.now(zone);
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        if (end.isBefore(start)) {
            LocalDate swap = start;
            start = end;
            end = swap;
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            start = end.minusDays(MAX_RANGE_DAYS);
        }
        return new AnalyticsDtos.RangeQuery(start, end);
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.AnalyticsResponse full(String userId, LocalDate from, LocalDate to) {
        AnalyticsDtos.RangeQuery range = resolveRange(userId, from, to);
        ZoneId zone = userZoneService.zoneOf(userId);
        List<ProductivityMetric> metrics = metricsService.rangeFilled(userId, range.from(), range.to());
        int days = Math.max(1, (int) ChronoUnit.DAYS.between(range.from(), range.to()) + 1);

        List<AnalyticsDtos.ProductivityPoint> daily = new ArrayList<>(metrics.size());
        Map<LocalDate, List<AnalyticsDtos.ProductivityPoint>> byWeek = new TreeMap<>();
        Map<java.time.YearMonth, List<AnalyticsDtos.ProductivityPoint>> byMonth = new TreeMap<>();

        for (ProductivityMetric metric : metrics) {
            AnalyticsDtos.ProductivityPoint point = new AnalyticsDtos.ProductivityPoint(
                    metric.getMetricDate(),
                    metric.getTasksCompleted(),
                    metric.getTasksCreated(),
                    metric.getFocusMinutes(),
                    metric.getStudyMinutes(),
                    metric.getHabitsCompleted(),
                    metric.getProductivityScore());
            daily.add(point);
            byWeek.computeIfAbsent(metric.getMetricDate().with(java.time.DayOfWeek.MONDAY),
                    ignored -> new ArrayList<>()).add(point);
            byMonth.computeIfAbsent(java.time.YearMonth.from(metric.getMetricDate()),
                    ignored -> new ArrayList<>()).add(point);
        }

        List<AnalyticsDtos.WeeklyProductivity> weekly = new ArrayList<>();
        byWeek.forEach((week, points) -> weekly.add(new AnalyticsDtos.WeeklyProductivity(
                week,
                (int) sum(points, AnalyticsDtos.ProductivityPoint::tasksCompleted),
                (int) sum(points, AnalyticsDtos.ProductivityPoint::focusMinutes),
                (int) sum(points, AnalyticsDtos.ProductivityPoint::studyMinutes),
                round(average(points, AnalyticsDtos.ProductivityPoint::productivityScore)))));

        List<AnalyticsDtos.MonthlyProductivity> monthly = new ArrayList<>();
        byMonth.forEach((month, points) -> monthly.add(new AnalyticsDtos.MonthlyProductivity(
                new AnalyticsDtos.MonthlyProductivity.YearMonthHolder(month.getYear(), month.getMonthValue()),
                (int) sum(points, AnalyticsDtos.ProductivityPoint::tasksCompleted),
                (int) sum(points, AnalyticsDtos.ProductivityPoint::focusMinutes),
                (int) sum(points, AnalyticsDtos.ProductivityPoint::studyMinutes),
                round(average(points, AnalyticsDtos.ProductivityPoint::productivityScore)),
                round(average(points, AnalyticsDtos.ProductivityPoint::productivityScore) / 100.0))));

        long tasksCreated = daily.stream().mapToLong(AnalyticsDtos.ProductivityPoint::tasksCreated).sum();
        long tasksCompleted = daily.stream().mapToLong(AnalyticsDtos.ProductivityPoint::tasksCompleted).sum();
        long tasksMissed = taskCountProvider.countMissedInRange(userId, range.from(), range.to());
        int focusMinutes = daily.stream().mapToInt(AnalyticsDtos.ProductivityPoint::focusMinutes).sum();
        int studyMinutes = daily.stream().mapToInt(AnalyticsDtos.ProductivityPoint::studyMinutes).sum();
        long habitCompletions = habitLogRepository.countCompletedBetween(userId, range.from(), range.to());
        long habitScheduled = (long) habitService.list(userId, false).size() * days;
        double completionRate = tasksCreated + tasksCompleted == 0 ? 0
                : round(tasksCompleted * 100.0 / (tasksCreated + tasksCompleted));

        BigDecimal income = nullSafe(transactionRepository.sumAmount(userId, TransactionType.INCOME, range.from(), range.to()));
        BigDecimal expenses = nullSafe(transactionRepository.sumAmount(userId, TransactionType.EXPENSE, range.from(), range.to()));

        AnalyticsDtos.AnalyticsSummary summary = new AnalyticsDtos.AnalyticsSummary(
                range.from(), range.to(), zone.getId(),
                tasksCreated, tasksCompleted, tasksMissed, completionRate,
                round(average(daily, AnalyticsDtos.ProductivityPoint::productivityScore)),
                focusMinutes, studyMinutes,
                focusSessionRepository.countCompletedBetween(userId,
                        DateSupport.startOfDay(range.from(), zone),
                        DateSupport.startOfDay(range.to().plusDays(1), zone)),
                habitCompletions,
                habitScheduled == 0 ? 0 : round(habitCompletions * 100.0 / habitScheduled),
                habitScheduled,
                daily.stream().filter(point -> point.tasksCompleted() > 0).count(),
                income, expenses, income.subtract(expenses),
                daily.stream().mapToLong(point -> point.productivityScore()).sum(),
                round(average(daily, AnalyticsDtos.ProductivityPoint::productivityScore)));

        List<AnalyticsDtos.GoalProgressPoint> goalProgress = goalService.summaries(userId).stream()
                .map(item -> new AnalyticsDtos.GoalProgressPoint(
                        item.id(), item.title(), item.progress(), item.status().name(),
                        item.completedTaskCount(), item.taskCount()))
                .toList();

        List<AnalyticsDtos.CategoryShare> spendingByCategory =
                serviceShares(financeService.overview(userId).expenseByCategory());
        List<AnalyticsDtos.CategoryShare> incomeByCategory = repositoryShares(
                transactionRepository.sumByCategory(userId, TransactionType.INCOME, range.from(), range.to()));

        List<String> dataGaps = detectGaps(userId, tasksCompleted, focusMinutes, studyMinutes,
                habitCompletions, habitScheduled);

        return new AnalyticsDtos.AnalyticsResponse(
                range.from(), range.to(), summary, daily, weekly, monthly, goalProgress,
                spendingByCategory, incomeByCategory,
                insightService.recent(userId, 20),
                predictionService.active(userId),
                balanceScoreService.compute(userId),
                dataGaps);
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.AnalyticsSummary summary(String userId, LocalDate from, LocalDate to) {
        return full(userId, from, to).summary();
    }

    private List<String> detectGaps(String userId, long tasksCompleted, int focusMinutes, int studyMinutes,
                                    long habitCompletions, long habitScheduled) {
        List<String> gaps = new ArrayList<>();
        if (tasksCompleted == 0) {
            gaps.add("No completed tasks recorded in this period yet.");
        }
        if (focusMinutes == 0) {
            gaps.add("No focus sessions logged in this period.");
        }
        if (studyMinutes == 0) {
            gaps.add("No study time logged in this period.");
        }
        if (habitCompletions == 0 && habitScheduled > 0) {
            gaps.add("No habit check-ins recorded in this period.");
        }
        if (goalService.activeCount(userId) == 0) {
            gaps.add("No active goals yet. Goals add the context LIFEOS uses for suggestions.");
        }
        return gaps;
    }

    /** Converts repository category totals into shares, largest first, percentages summing to 100. */
    private static List<AnalyticsDtos.CategoryShare> repositoryShares(
            List<FinanceTransactionRepository.CategoryTotal> totals) {
        BigDecimal grandTotal = totals.stream()
                .map(FinanceTransactionRepository.CategoryTotal::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (grandTotal.signum() == 0) {
            return List.of();
        }
        return totals.stream()
                .map(total -> new AnalyticsDtos.CategoryShare(
                        total.getCategory(),
                        total.getTotal(),
                        round(total.getTotal().multiply(BigDecimal.valueOf(100))
                                .divide(grandTotal, 4, java.math.RoundingMode.HALF_UP).doubleValue())))
                .toList();
    }

    /** Converts service-level category totals into shares. */
    private static List<AnalyticsDtos.CategoryShare> serviceShares(List<FinanceDtos.CategoryTotal> totals) {
        return totals.stream()
                .map(total -> new AnalyticsDtos.CategoryShare(total.category(), total.total(), total.sharePercent()))
                .toList();
    }

    private static BigDecimal nullSafe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static long sum(List<AnalyticsDtos.ProductivityPoint> points,
                            ToIntFunction<AnalyticsDtos.ProductivityPoint> accessor) {
        return points.stream().mapToLong(accessor::applyAsInt).sum();
    }

    private static double average(List<AnalyticsDtos.ProductivityPoint> points,
                                  ToIntFunction<AnalyticsDtos.ProductivityPoint> accessor) {
        if (points.isEmpty()) {
            return 0;
        }
        return sum(points, accessor) / (double) points.size();
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}