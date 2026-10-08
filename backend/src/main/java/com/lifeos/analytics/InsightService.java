package com.lifeos.analytics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeos.dto.GoalDtos;
import com.lifeos.dto.InsightDtos;
import com.lifeos.entity.Budget;
import com.lifeos.entity.Habit;
import com.lifeos.entity.Insight;
import com.lifeos.entity.ProductivityMetric;
import com.lifeos.entity.enums.InsightSeverity;
import com.lifeos.entity.enums.InsightType;
import com.lifeos.entity.enums.NotificationCategory;
import com.lifeos.entity.enums.NotificationPriority;
import com.lifeos.entity.enums.TransactionType;
import com.lifeos.repository.BudgetRepository;
import com.lifeos.repository.FinanceTransactionRepository;
import com.lifeos.repository.HabitLogRepository;
import com.lifeos.repository.HabitRepository;
import com.lifeos.repository.InsightRepository;
import com.lifeos.repository.ProductivityMetricRepository;
import com.lifeos.service.GoalService;
import com.lifeos.service.NotificationService;
import com.lifeos.service.UserZoneService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Rule-based insight engine.
 *
 * <p>Every rule is deterministic and records the measurements it used, so an insight can always be
 * traced back to the rows that produced it. A rule that would otherwise compare a single day against
 * nothing is skipped, and the reason is returned to the caller in {@code skippedReasons}.</p>
 */
@Service
public class InsightService {

    private static final Logger log = LoggerFactory.getLogger(InsightService.class);
    private static final int MIN_DAYS = 7;
    private static final int OVERDUE_WINDOW_DAYS = 14;
    private static final int BUDGET_WARN_PERCENT = 80;

    private final InsightRepository insightRepository;
    private final ProductivityMetricRepository metricRepository;
    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;
    private final FinanceTransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final GoalService goalService;
    private final NotificationService notificationService;
    private final TaskCountProvider taskCountProvider;
    private final UserZoneService userZoneService;
    private final ObjectMapper objectMapper;

    public InsightService(InsightRepository insightRepository,
                          ProductivityMetricRepository metricRepository,
                          HabitRepository habitRepository,
                          HabitLogRepository habitLogRepository,
                          FinanceTransactionRepository transactionRepository,
                          BudgetRepository budgetRepository,
                          GoalService goalService,
                          NotificationService notificationService,
                          TaskCountProvider taskCountProvider,
                          UserZoneService userZoneService,
                          ObjectMapper objectMapper) {
        this.insightRepository = insightRepository;
        this.metricRepository = metricRepository;
        this.habitRepository = habitRepository;
        this.habitLogRepository = habitLogRepository;
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.goalService = goalService;
        this.notificationService = notificationService;
        this.taskCountProvider = taskCountProvider;
        this.userZoneService = userZoneService;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------- retrieval

    @Transactional(readOnly = true)
    public Page<InsightDtos.InsightResponse> page(String userId, int page, int size, InsightType type,
                                                  boolean includeDismissed) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        Page<Insight> result;
        if (type != null) {
            result = insightRepository.findByUserIdAndInsightTypeOrderByCreatedAtDesc(userId, type, pageable);
        } else if (includeDismissed) {
            result = insightRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        } else {
            result = insightRepository.findByUserIdAndDismissedFalseOrderByCreatedAtDesc(userId, pageable);
        }
        return result.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<InsightDtos.InsightResponse> recent(String userId, int limit) {
        return insightRepository
                .findByUserIdAndDismissedFalseOrderByCreatedAtDesc(userId,
                        PageRequest.of(0, Math.min(50, Math.max(1, limit))))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public InsightDtos.InsightResponse setDismissed(String userId, String insightId, boolean dismissed) {
        Insight insight = insightRepository.findByIdAndUserId(insightId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Insight not found"));
        insight.setDismissed(dismissed);
        return toResponse(insightRepository.save(insight));
    }

    // ------------------------------------------------------------- generation

    @Transactional
    public InsightDtos.InsightGenerationResponse generate(String userId) {
        LocalDate today = LocalDate.now(userZoneService.zoneOf(userId));
        List<String> skipped = new ArrayList<>();
        List<InsightDtos.InsightResponse> created = new ArrayList<>();
        int evaluated = 0;

        evaluated++;
        created.addAll(productivityTrend(userId, today, skipped));
        evaluated++;
        created.addAll(overdueTaskPressure(userId, today, skipped));
        evaluated++;
        created.addAll(habitConsistency(userId, today, skipped));
        evaluated++;
        created.addAll(budgetPressure(userId, today, skipped));
        evaluated++;
        created.addAll(goalDrift(userId, today, skipped));
        evaluated++;
        created.addAll(focusRhythm(userId, today, skipped));

        return new InsightDtos.InsightGenerationResponse(created, evaluated, skipped);
    }

    private List<InsightDtos.InsightResponse> productivityTrend(String userId, LocalDate today,
                                                                 List<String> skipped) {
        LocalDate recentFrom = today.minusDays(MIN_DAYS - 1L);
        LocalDate priorTo = recentFrom.minusDays(1);
        LocalDate priorFrom = priorTo.minusDays(MIN_DAYS - 1L);
        List<ProductivityMetric> recent = metrics(userId, recentFrom, today);
        List<ProductivityMetric> prior = metrics(userId, priorFrom, priorTo);
        if (recent.size() < MIN_DAYS || prior.size() < MIN_DAYS) {
            skipped.add("Productivity trend: needs " + (MIN_DAYS * 2) + " days of recorded metrics, found "
                    + (recent.size() + prior.size()) + ".");
            return List.of();
        }
        double recentAvg = meanScore(recent);
        double priorAvg = meanScore(prior);
        if (priorAvg == 0) {
            skipped.add("Productivity trend: the earlier window has no measurable baseline.");
            return List.of();
        }
        double change = (recentAvg - priorAvg) * 100.0 / priorAvg;
        if (Math.abs(change) < 10) {
            return List.of();
        }
        boolean up = change > 0;
        String body = String.format(
                "The last %d days averaged %.1f against %.1f for the %d days before, a %s of %.0f%%.",
                MIN_DAYS, recentAvg, priorAvg, MIN_DAYS, up ? "rise" : "drop", Math.abs(change));
        return store(userId, InsightType.PRODUCTIVITY,
                up ? "Your productivity is trending up" : "Your productivity is trending down",
                body,
                up ? InsightSeverity.INFO : InsightSeverity.WARNING,
                (int) Math.min(99, 40 + Math.round(Math.abs(change))),
                List.of("Recent average: " + round(recentAvg),
                        "Previous average: " + round(priorAvg),
                        "Change: " + (up ? "+" : "") + round(change) + "%"),
                priorFrom, today, "productivity-trend-" + recentFrom);
    }

    private List<InsightDtos.InsightResponse> overdueTaskPressure(String userId, LocalDate today,
                                                                   List<String> skipped) {
        long open = taskCountProvider.countOpen(userId);
        if (open == 0) {
            skipped.add("Overdue pressure: no open tasks.");
            return List.of();
        }
        long overdue = taskCountProvider.countMissedInRange(userId, today.minusDays(OVERDUE_WINDOW_DAYS - 1L), today);
        if (overdue == 0) {
            return List.of();
        }
        double share = overdue * 100.0 / open;
        return store(userId, InsightType.PLANNING, "Overdue work is building up",
                String.format("%d open tasks are past their deadline, which is %.0f%% of the %d you have open.",
                        overdue, share, open),
                share >= 40 ? InsightSeverity.CRITICAL : InsightSeverity.WARNING,
                (int) Math.min(99, 45 + Math.round(share)),
                List.of("Overdue tasks: " + overdue, "Open tasks: " + open,
                        "Overdue share: " + round(share) + "%"),
                today.minusDays(OVERDUE_WINDOW_DAYS - 1L), today, "overdue-pressure-" + today);
    }

    private List<InsightDtos.InsightResponse> habitConsistency(String userId, LocalDate today,
                                                               List<String> skipped) {
        List<Habit> habits = habitRepository.findByUserIdAndArchivedFalse(userId);
        if (habits.isEmpty()) {
            skipped.add("Habit consistency: no active habits.");
            return List.of();
        }
        List<String> declining = new ArrayList<>();
        List<String> improving = new ArrayList<>();
        for (Habit habit : habits) {
            long lastWeek = habitLogRepository.countByUserIdAndHabitIdAndCompletedTrueAndLogDateBetween(
                    userId, habit.getId(), today.minusDays(MIN_DAYS - 1L), today);
            long priorWeek = habitLogRepository.countByUserIdAndHabitIdAndCompletedTrueAndLogDateBetween(
                    userId, habit.getId(), today.minusDays((MIN_DAYS * 2) - 1L), today.minusDays(MIN_DAYS));
            if (priorWeek > lastWeek) {
                declining.add(habit.getName() + " (" + lastWeek + " vs " + priorWeek + ")");
            } else if (lastWeek > priorWeek) {
                improving.add(habit.getName() + " (" + lastWeek + " vs " + priorWeek + ")");
            }
        }
        if (declining.isEmpty() && improving.isEmpty()) {
            return List.of();
        }
        StringBuilder body = new StringBuilder();
        if (!declining.isEmpty()) {
            body.append("Fewer check-ins than the week before: ").append(String.join(", ", declining)).append(". ");
        }
        if (!improving.isEmpty()) {
            body.append("More check-ins than the week before: ").append(String.join(", ", improving)).append('.');
        }
        List<String> factors = new ArrayList<>();
        declining.forEach(item -> factors.add("Declining: " + item));
        improving.forEach(item -> factors.add("Improving: " + item));
        return store(userId, InsightType.HABIT,
                declining.isEmpty() ? "Habit check-ins are holding steady" : "Some habits are slipping",
                body.toString().trim(),
                declining.isEmpty() ? InsightSeverity.INFO : InsightSeverity.WARNING,
                declining.isEmpty() ? 55 : Math.min(95, 55 + declining.size() * 10),
                factors, today.minusDays((MIN_DAYS * 2) - 1L), today, "habit-consistency-" + today);
    }

    private List<InsightDtos.InsightResponse> budgetPressure(String userId, LocalDate today,
                                                             List<String> skipped) {
        List<Budget> budgets = budgetRepository.findActiveOn(userId, today);
        if (budgets.isEmpty()) {
            skipped.add("Budget pressure: no active budgets.");
            return List.of();
        }
        List<String> breached = new ArrayList<>();
        List<String> approaching = new ArrayList<>();
        for (Budget budget : budgets) {
            BigDecimal limit = budget.getAmount() == null ? BigDecimal.ZERO : budget.getAmount();
            if (limit.signum() <= 0) {
                continue;
            }
            LocalDate end = budget.getEndDate() == null ? today : budget.getEndDate();
            BigDecimal spent = transactionRepository.sumAmountByCategory(userId, TransactionType.EXPENSE,
                    budget.getCategory(), budget.getStartDate(), end);
            if (spent == null) {
                spent = BigDecimal.ZERO;
            }
            double usage = spent.doubleValue() / limit.doubleValue() * 100.0;
            if (usage >= 100) {
                breached.add(budget.getCategory() + " (" + spent.setScale(2, RoundingMode.HALF_UP)
                        + " of " + limit.setScale(2, RoundingMode.HALF_UP) + ")");
            } else if (usage >= BUDGET_WARN_PERCENT) {
                approaching.add(budget.getCategory() + " at " + Math.round(usage) + "%");
            }
        }
        if (breached.isEmpty() && approaching.isEmpty()) {
            return List.of();
        }
        StringBuilder body = new StringBuilder();
        if (!breached.isEmpty()) {
            body.append("Over the limit: ").append(String.join(", ", breached)).append(". ");
        }
        if (!approaching.isEmpty()) {
            body.append("Close to the limit: ").append(String.join(", ", approaching)).append('.');
        }
        List<String> factors = new ArrayList<>(breached);
        approaching.forEach(item -> factors.add("Approaching: " + item));
        return store(userId, InsightType.FINANCE,
                breached.isEmpty() ? "Spending is approaching a budget limit" : "A budget limit has been passed",
                body.toString().trim(),
                breached.isEmpty() ? InsightSeverity.WARNING : InsightSeverity.CRITICAL,
                breached.isEmpty() ? 55 : 75,
                factors, today.withDayOfMonth(1), today, "budget-pressure-" + today);
    }

    private List<InsightDtos.InsightResponse> goalDrift(String userId, LocalDate today,
                                                        List<String> skipped) {
        List<GoalDtos.GoalSummary> summaries = goalService.summaries(userId);
        if (summaries.isEmpty()) {
            skipped.add("Goal drift: no goals to evaluate.");
            return List.of();
        }
        List<String> drifting = new ArrayList<>();
        for (GoalDtos.GoalSummary summary : summaries) {
            if (summary.targetDate() == null || summary.taskCount() == 0) {
                continue;
            }
            LocalDate target = summary.targetDate().atZone(ZoneId.of("UTC")).toLocalDate();
            long daysLeft = ChronoUnit.DAYS.between(today, target);
            if (daysLeft <= 0) {
                continue;
            }
            long remaining = summary.taskCount() - summary.completedTaskCount();
            long pace = Math.max(1, Math.round((double) summary.taskCount() / Math.max(1, daysLeft)));
            long neededByNow = summary.completedTaskCount() + pace * MIN_DAYS;
            if (neededByNow > summary.taskCount()) {
                drifting.add(summary.title() + " is at " + summary.progress() + "% with " + remaining
                        + " tasks left and " + daysLeft + " days to its target date");
            }
        }
        if (drifting.isEmpty()) {
            return List.of();
        }
        return store(userId, InsightType.GOAL, "Some goals are behind the pace they need",
                String.join(". ", drifting) + ".",
                InsightSeverity.WARNING, 60, drifting, today.minusDays(29), today, "goal-drift-" + today);
    }

    private List<InsightDtos.InsightResponse> focusRhythm(String userId, LocalDate today,
                                                          List<String> skipped) {
        LocalDate from = today.minusDays(MIN_DAYS - 1L);
        List<ProductivityMetric> metrics = metrics(userId, from, today);
        if (metrics.size() < MIN_DAYS) {
            skipped.add("Focus rhythm: needs " + MIN_DAYS + " days of metrics, found " + metrics.size() + ".");
            return List.of();
        }
        int totalFocus = metrics.stream().mapToInt(ProductivityMetric::getFocusMinutes).sum();
        if (totalFocus == 0) {
            skipped.add("Focus rhythm: no focus minutes recorded in the window.");
            return List.of();
        }
        ProductivityMetric best = metrics.stream()
                .max(Comparator.comparingInt(ProductivityMetric::getFocusMinutes))
                .orElseThrow();
        int average = Math.round(totalFocus / (float) metrics.size());
        if (best.getFocusMinutes() <= average * 1.2) {
            return List.of();
        }
        return store(userId, InsightType.FOCUS, "Your strongest focus day is worth repeating",
                String.format("On %s you logged %d focused minutes against a daily average of %d over the last %d days.",
                        best.getMetricDate(), best.getFocusMinutes(), average, MIN_DAYS),
                InsightSeverity.INFO, 50,
                List.of("Strongest day: " + best.getMetricDate(),
                        "Minutes that day: " + best.getFocusMinutes(),
                        "Daily average: " + average),
                from, today, "focus-rhythm-" + today);
    }

    // -------------------------------------------------------------- internals

    private List<ProductivityMetric> metrics(String userId, LocalDate from, LocalDate to) {
        return metricRepository.findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(userId, from, to);
    }

    private static double meanScore(List<ProductivityMetric> metrics) {
        return metrics.stream().mapToInt(ProductivityMetric::getProductivityScore).average().orElse(0);
    }

    private List<InsightDtos.InsightResponse> store(String userId, InsightType type, String title, String body,
                                                    InsightSeverity severity, int confidence, List<String> factors,
                                                    LocalDate periodStart, LocalDate periodEnd, String dedupeKey) {
        if (insightRepository.findByUserIdAndDedupeKey(userId, dedupeKey).isPresent()) {
            return List.of();
        }
        Insight insight = new Insight();
        insight.setUserId(userId);
        insight.setInsightType(type);
        insight.setTitle(title);
        insight.setBody(body);
        insight.setSeverity(severity);
        insight.setConfidence(Math.max(1, Math.min(99, confidence)));
        insight.setFactors(writeFactors(factors));
        insight.setPeriodStart(periodStart);
        insight.setPeriodEnd(periodEnd);
        insight.setSource("RULE");
        insight.setDedupeKey(dedupeKey);
        insight.setDismissed(false);
        Insight saved = insightRepository.save(insight);
        if (severity != InsightSeverity.INFO) {
            notificationService.notify(userId, NotificationCategory.AI_INSIGHT,
                    severity == InsightSeverity.CRITICAL ? NotificationPriority.HIGH : NotificationPriority.NORMAL,
                    title, body, "/insights", dedupeKey);
        }
        return List.of(toResponse(saved));
    }

    private String writeFactors(List<String> factors) {
        try {
            return objectMapper.writeValueAsString(factors);
        } catch (Exception ex) {
            log.warn("Could not serialise insight factors: {}", ex.getMessage());
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

    public InsightDtos.InsightResponse toResponse(Insight insight) {
        return new InsightDtos.InsightResponse(
                insight.getId(),
                insight.getInsightType(),
                insight.getTitle(),
                insight.getBody(),
                insight.getSeverity(),
                insight.getConfidence(),
                readFactors(insight.getFactors()),
                insight.getPeriodStart(),
                insight.getPeriodEnd(),
                insight.getSource(),
                insight.isDismissed(),
                insight.getCreatedAt());
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}