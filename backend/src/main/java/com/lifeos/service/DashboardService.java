package com.lifeos.service;

import com.lifeos.analytics.AnalyticsService;
import com.lifeos.analytics.BalanceScoreService;
import com.lifeos.analytics.InsightService;
import com.lifeos.analytics.PredictionService;
import com.lifeos.dto.AiDtos;
import com.lifeos.dto.AnalyticsDtos;
import com.lifeos.dto.DashboardDtos;
import com.lifeos.dto.FinanceDtos;
import com.lifeos.dto.InsightDtos;
import com.lifeos.dto.LearningDtos;
import com.lifeos.dto.TaskDtos;
import com.lifeos.entity.Goal;
import com.lifeos.entity.ProductivityMetric;
import com.lifeos.entity.Task;
import com.lifeos.entity.User;
import com.lifeos.entity.enums.Priority;
import com.lifeos.entity.enums.TaskStatus;
import com.lifeos.repository.GoalRepository;
import com.lifeos.repository.ProductivityMetricRepository;
import com.lifeos.repository.TaskRepository;
import com.lifeos.repository.UserRepository;
import com.lifeos.util.DateSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Assembles the dashboard.
 *
 * <p>Every number here is read from stored rows. Where a user has no history the field is reported as
 * zero or an empty list and {@code dataGaps} explains what is missing, rather than a plausible-looking
 * placeholder being shown.</p>
 */
@Service
public class DashboardService {

    private static final int FOCUS_ITEMS = 5;
    private static final int GOAL_BARS = 5;
    private static final int UPCOMING_EVENTS = 8;
    private static final LocalTime EVENING = LocalTime.of(17, 0);

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final GoalRepository goalRepository;
    private final ProductivityMetricRepository metricRepository;
    private final TaskService taskService;
    private final GoalService goalService;
    private final HabitService habitService;
    private final CalendarService calendarService;
    private final FocusService focusService;
    private final FinanceService financeService;
    private final LearningService learningService;
    private final MetricsService metricsService;
    private final BalanceScoreService balanceScoreService;
    private final InsightService insightService;
    private final PredictionService predictionService;
    private final RecommendationService recommendationService;
    private final AnalyticsService analyticsService;
    private final UserZoneService userZoneService;

    public DashboardService(UserRepository userRepository,
                            TaskRepository taskRepository,
                            GoalRepository goalRepository,
                            ProductivityMetricRepository metricRepository,
                            TaskService taskService,
                            GoalService goalService,
                            HabitService habitService,
                            CalendarService calendarService,
                            FocusService focusService,
                            FinanceService financeService,
                            LearningService learningService,
                            MetricsService metricsService,
                            BalanceScoreService balanceScoreService,
                            InsightService insightService,
                            PredictionService predictionService,
                            RecommendationService recommendationService,
                            AnalyticsService analyticsService,
                            UserZoneService userZoneService) {
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.goalRepository = goalRepository;
        this.metricRepository = metricRepository;
        this.taskService = taskService;
        this.goalService = goalService;
        this.habitService = habitService;
        this.calendarService = calendarService;
        this.focusService = focusService;
        this.financeService = financeService;
        this.learningService = learningService;
        this.metricsService = metricsService;
        this.balanceScoreService = balanceScoreService;
        this.insightService = insightService;
        this.predictionService = predictionService;
        this.recommendationService = recommendationService;
        this.analyticsService = analyticsService;
        this.userZoneService = userZoneService;
    }

    @Transactional(readOnly = true)
    public DashboardDtos.DashboardResponse dashboard(String userId) {
        ZoneId zone = userZoneService.zoneOf(userId);
        LocalDate today = LocalDate.now(zone);
        Instant dayStart = DateSupport.startOfDay(today, zone);
        Instant dayEnd = DateSupport.startOfDay(today.plusDays(1), zone);

        AnalyticsDtos.BalanceScore balance = balanceScoreService.compute(userId);
        AnalyticsDtos.AnalyticsSummary summary = analyticsService.summary(userId, today.minusDays(29), today);

        return new DashboardDtos.DashboardResponse(
                greeting(userId, today, zone),
                zone.getId(),
                todaySummary(userId, today, zone, dayStart, dayEnd),
                todaysFocus(userId, today, zone),
                goalBars(userId),
                habitMini(userId, today),
                calendarService.upcoming(userId, dayStart, dayEnd, UPCOMING_EVENTS),
                summaryCards(summary),
                financeService.overview(userId),
                learningService.statistics(userId),
                summary.score() == 0 ? 0 : (int) Math.round(summary.score()),
                productivityExplanation(userId, today),
                balance,
                insightService.recent(userId, 5),
                recommendationService.recommend(userId).recommendations(),
                predictionService.active(userId).stream()
                        .map(prediction -> new DashboardDtos.DashboardResponse.PredictionResponse(
                                prediction.id(), prediction.predictionType(), prediction.label(),
                                prediction.probability(), prediction.factors()))
                        .toList(),
                dataGaps(userId, today));
    }

    @Transactional(readOnly = true)
    public DashboardDtos.TodayResponse today(String userId) {
        ZoneId zone = userZoneService.zoneOf(userId);
        LocalDate today = LocalDate.now(zone);
        Instant dayStart = DateSupport.startOfDay(today, zone);
        Instant dayEnd = DateSupport.startOfDay(today.plusDays(1), zone);

        return new DashboardDtos.TodayResponse(
                today,
                greeting(userId, today, zone),
                todaySummary(userId, today, zone, dayStart, dayEnd),
                todaysFocus(userId, today, zone),
                habitMini(userId, today),
                calendarService.upcoming(userId, dayStart, dayEnd, UPCOMING_EVENTS),
                taskService.overdue(userId, zone).stream().map(taskService::toResponse).toList(),
                taskService.dueOn(userId, today, zone).stream().map(taskService::toResponse).toList(),
                balanceScoreService.compute(userId),
                recommendationService.recommend(userId).recommendations());
    }

    // ------------------------------------------------------------ components

    private DashboardDtos.Greeting greeting(String userId, LocalDate today, ZoneId zone) {
        String displayName = userRepository.findByIdAndDeletedAtIsNull(userId)
                .map(User::getFullName)
                .filter(name -> name != null && !name.isBlank())
                .map(name -> name.strip().split("\\s+")[0])
                .orElse("there");
        LocalTime now = LocalTime.now(zone);
        String salutation = now.isBefore(LocalTime.of(12, 0)) ? "Good morning"
                : now.isBefore(EVENING) ? "Good afternoon" : "Good evening";
        return new DashboardDtos.Greeting(salutation,
                salutation + ", " + displayName + ". Here is what your records show for today.",
                displayName, today, zone.getId(), Instant.now());
    }

    private DashboardDtos.TodaySummary todaySummary(String userId, LocalDate today, ZoneId zone,
                                                    Instant dayStart, Instant dayEnd) {
        List<Task> dueToday = taskService.dueOn(userId, today, zone);
        long overdue = taskService.overdue(userId, zone).size();
        long open = taskRepository.countByUserIdAndStatusAndDeletedAtIsNull(userId, TaskStatus.TODO)
                + taskRepository.countByUserIdAndStatusAndDeletedAtIsNull(userId, TaskStatus.IN_PROGRESS);
        int estimated = dueToday.stream()
                .mapToInt(task -> task.getEstimatedMinutes() == null ? 0 : task.getEstimatedMinutes())
                .sum();
        long completed = metricRepository.findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(userId, today, today)
                .stream().mapToLong(ProductivityMetric::getTasksCompleted).findFirst().orElse(0);
        int habitsScheduled = habitService.list(userId, false).size();
        long habitsDone = habitService.list(userId, false).stream()
                .filter(com.lifeos.dto.HabitDtos.HabitResponse::completedToday).count();

        return new DashboardDtos.TodaySummary(
                dueToday.size(), completed, overdue, open, estimated,
                focusService.minutesOn(userId, today),
                metricRepository.findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(userId, today, today)
                        .stream().mapToInt(ProductivityMetric::getStudyMinutes).findFirst().orElse(0),
                habitsDone, habitsScheduled,
                calendarService.eventCount(userId, dayStart, dayEnd));
    }

    /**
     * The "focus on this" list. Ordering is explicit and explained per item: overdue work first, then
     * the nearest deadline, then priority. Blocked tasks are listed with the blockers named, since
     * starting them would be wasted effort.
     */
    private List<DashboardDtos.FocusItem> todaysFocus(String userId, LocalDate today, ZoneId zone) {
        List<Task> candidates = new ArrayList<>();
        candidates.addAll(taskService.overdue(userId, zone));
        taskService.dueOn(userId, today, zone).stream()
                .filter(task -> candidates.stream().noneMatch(existing -> existing.getId().equals(task.getId())))
                .forEach(candidates::add);

        List<DashboardDtos.FocusItem> items = new ArrayList<>();
        int rank = 1;
        for (Task task : candidates) {
            if (rank > FOCUS_ITEMS) {
                break;
            }
            boolean overdue = task.getDeadline() != null && task.getDeadline().isBefore(Instant.now());
            List<String> blockedBy = taskService.dependencies(userId, task.getId());
            items.add(new DashboardDtos.FocusItem(
                    rank++,
                    task.getId(),
                    task.getTitle(),
                    task.getPriority(),
                    task.getCategory(),
                    task.getDeadline(),
                    task.getEstimatedMinutes(),
                    reason(task, overdue, today, zone),
                    !blockedBy.isEmpty(),
                    blockedBy));
        }
        return items;
    }

    private String reason(Task task, boolean overdue, LocalDate today, ZoneId zone) {
        if (overdue) {
            return "Overdue since " + task.getDeadline().atZone(zone).toLocalDate();
        }
        if (task.getDeadline() != null) {
            long days = ChronoUnit.DAYS.between(today, task.getDeadline().atZone(zone).toLocalDate());
            if (days == 0) {
                return "Due today";
            }
            if (days == 1) {
                return "Due tomorrow";
            }
            if (days <= 7) {
                return "Due in " + days + " days";
            }
        }
        Priority priority = task.getPriority() == null ? Priority.MEDIUM : task.getPriority();
        return priority == Priority.LOW
                ? "No deadline; listed because it is still open"
                : priority.name() + " priority with no deadline";
    }

    private List<DashboardDtos.ProgressBar> goalBars(String userId) {
        return goalService.summaries(userId).stream()
                .filter(summary -> summary.status() == com.lifeos.entity.enums.GoalStatus.ACTIVE)
                .sorted(Comparator.comparing(com.lifeos.dto.GoalDtos.GoalSummary::progress).reversed())
                .limit(GOAL_BARS)
                .map(summary -> new DashboardDtos.ProgressBar(
                        summary.id(),
                        summary.title(),
                        summary.progress(),
                        "/goals/" + summary.id(),
                        summary.completedTaskCount() + " of " + summary.taskCount() + " tasks done"))
                .toList();
    }

    private List<DashboardDtos.HabitMini> habitMini(String userId, LocalDate today) {
        return habitService.list(userId, false).stream()
                .sorted(Comparator.comparing(com.lifeos.dto.HabitDtos.HabitResponse::name))
                .map(habit -> new DashboardDtos.HabitMini(
                        habit.id(), habit.name(), habit.completedToday(), habit.currentStreak(),
                        habit.reminderTime() == null ? null : habit.reminderTime().toString()))
                .toList();
    }

    private List<DashboardDtos.CountCard> summaryCards(AnalyticsDtos.AnalyticsSummary summary) {
        List<DashboardDtos.CountCard> cards = new ArrayList<>();
        cards.add(new DashboardDtos.CountCard("Tasks completed", summary.tasksCompleted(),
                summary.tasksCreated() == 0
                        ? "nothing created in this period"
                        : summary.taskCompletionRate() + "% of created work finished", "/tasks"));
        cards.add(new DashboardDtos.CountCard("Focus minutes", summary.focusMinutes(),
                summary.focusSessions() + " sessions", "/focus"));
        cards.add(new DashboardDtos.CountCard("Study minutes", summary.studyMinutes(),
                "recorded learning time", "/learning"));
        cards.add(new DashboardDtos.CountCard("Habit consistency", (long) Math.round(summary.habitConsistencyRate()),
                summary.habitCompletions() + " of " + summary.habitScheduled() + " check-ins", "/habits"));
        cards.add(new DashboardDtos.CountCard("Net this period", 0,
                summary.savings().signum() == 0 ? "income matched spending" : summary.savings().signum() > 0
                        ? "spending below income" : "spending above income", "/finance"));
        return cards;
    }

    private String productivityExplanation(String userId, LocalDate today) {
        ProductivityMetric today1 = metricsService.forDateReadOnly(userId, today);
        if (today1 == null) {
            return "No productivity metric has been recorded for today yet. It appears once you complete a task, "
                    + "log focus time or check in a habit.";
        }
        return "Today's score blends tasks completed (" + today1.getTasksCompleted() + "), focus minutes ("
                + today1.getFocusMinutes() + "), habit check-ins (" + today1.getHabitsCompleted()
                + ") and study minutes (" + today1.getStudyMinutes() + ") into a single 0-100 value.";
    }

    private List<String> dataGaps(String userId, LocalDate today) {
        List<String> gaps = new ArrayList<>();
        if (taskRepository.countByUserIdAndDeletedAtIsNull(userId) == 0) {
            gaps.add("No tasks recorded yet.");
        }
        if (habitService.activeCount(userId) == 0) {
            gaps.add("No habits recorded yet.");
        }
        if (goalService.activeCount(userId) == 0) {
            gaps.add("No active goals yet.");
        }
        if (focusService.totalMinutes(userId) == 0) {
            gaps.add("No focus sessions logged yet.");
        }
        List<Goal> goals = goalRepository.findAllForUser(userId).stream()
                .filter(goal -> goal.getDeletedAt() == null).toList();
        if (goals.isEmpty()) {
            gaps.add("Dashboard metrics for " + today + " are empty because nothing has been recorded yet.");
        }
        return gaps;
    }
}
