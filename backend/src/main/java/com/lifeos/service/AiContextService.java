package com.lifeos.service;

import com.lifeos.entity.CalendarEvent;
import com.lifeos.entity.Goal;
import com.lifeos.entity.Habit;
import com.lifeos.entity.JournalEntry;
import com.lifeos.entity.LearningGoal;
import com.lifeos.entity.ProductivityMetric;
import com.lifeos.entity.Task;
import com.lifeos.entity.enums.TaskStatus;
import com.lifeos.entity.enums.TransactionType;
import com.lifeos.repository.CalendarEventRepository;
import com.lifeos.repository.FinanceTransactionRepository;
import com.lifeos.repository.FocusSessionRepository;
import com.lifeos.repository.GoalRepository;
import com.lifeos.repository.HabitLogRepository;
import com.lifeos.repository.HabitRepository;
import com.lifeos.repository.JournalEntryRepository;
import com.lifeos.repository.LearningGoalRepository;
import com.lifeos.repository.ProductivityMetricRepository;
import com.lifeos.repository.TaskRepository;
import com.lifeos.util.DateSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Builds the factual context the assistant is allowed to answer from.
 *
 * <p>Everything here is read straight from the user's own rows. Scopes are opt-in so a question about
 * habits does not drag the whole database into a prompt, and every line is a short factual statement
 * rather than free text, which keeps stored content from being read as instructions.</p>
 */
@Service
public class AiContextService {

    private static final int MAX_LINES_PER_SCOPE = 25;
    private static final int CONTEXT_DAYS = 14;
    private static final Set<String> DOCUMENT_MARKERS = Set.of(
            "document", "documents", "uploaded", "upload", "file", "files", "pdf", "docx",
            "attachment", "note", "notes", "paper", "article", "textbook", "manual");

    private final TaskRepository taskRepository;
    private final GoalRepository goalRepository;
    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;
    private final FocusSessionRepository focusSessionRepository;
    private final LearningGoalRepository learningGoalRepository;
    private final FinanceTransactionRepository transactionRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final CalendarEventRepository calendarEventRepository;
    private final ProductivityMetricRepository metricRepository;
    private final UserZoneService userZoneService;

    public AiContextService(TaskRepository taskRepository,
                            GoalRepository goalRepository,
                            HabitRepository habitRepository,
                            HabitLogRepository habitLogRepository,
                            FocusSessionRepository focusSessionRepository,
                            LearningGoalRepository learningGoalRepository,
                            FinanceTransactionRepository transactionRepository,
                            JournalEntryRepository journalEntryRepository,
                            CalendarEventRepository calendarEventRepository,
                            ProductivityMetricRepository metricRepository,
                            UserZoneService userZoneService) {
        this.taskRepository = taskRepository;
        this.goalRepository = goalRepository;
        this.habitRepository = habitRepository;
        this.habitLogRepository = habitLogRepository;
        this.focusSessionRepository = focusSessionRepository;
        this.learningGoalRepository = learningGoalRepository;
        this.transactionRepository = transactionRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.calendarEventRepository = calendarEventRepository;
        this.metricRepository = metricRepository;
        this.userZoneService = userZoneService;
    }

    /**
     * Decides whether a question should be answered from uploaded documents instead of live records.
     * Keyword matching alone would misfire constantly, so a document marker must be present; otherwise
     * the knowledge scope is only used when the user has explicitly asked about their library.
     */
    public boolean looksLikeDocumentQuestion(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String text = message.toLowerCase(Locale.ROOT);
        return DOCUMENT_MARKERS.stream().anyMatch(marker -> containsWord(text, marker));
    }

    @Transactional(readOnly = true)
    public ContextSnapshot build(String userId, List<String> scopes) {
        ZoneId zone = userZoneService.zoneOf(userId);
        LocalDate today = LocalDate.now(zone);
        List<String> lines = new ArrayList<>();
        List<String> used = new ArrayList<>();

        addIfRequested(scopes, "tasks", lines, used, () -> taskLines(userId, today, zone));
        addIfRequested(scopes, "goals", lines, used, () -> goalLines(userId));
        addIfRequested(scopes, "habits", lines, used, () -> habitLines(userId, today));
        addIfRequested(scopes, "focus", lines, used, () -> focusLines(userId, today, zone));
        addIfRequested(scopes, "learning", lines, used, () -> learningLines(userId, today, zone));
        addIfRequested(scopes, "finance", lines, used, () -> financeLines(userId, today));
        addIfRequested(scopes, "journal", lines, used, () -> journalLines(userId, today));
        addIfRequested(scopes, "calendar", lines, used, () -> upcomingEvents(userId, today, zone));

        if (scopes.isEmpty()) {
            lines.add("No context scopes were selected for this question.");
        }
        return new ContextSnapshot(List.copyOf(lines), List.copyOf(used), !used.isEmpty());
    }

    private void addIfRequested(List<String> scopes, String scope, List<String> lines, List<String> used,
                                 java.util.function.Supplier<List<String>> producer) {
        if (!scopes.contains(scope)) {
            return;
        }
        List<String> produced = producer.get();
        if (!produced.isEmpty()) {
            used.add(scope);
            lines.addAll(produced);
        }
    }

    // ---------------------------------------------------------------- scopes

    private List<String> taskLines(String userId, LocalDate today, ZoneId zone) {
        List<String> lines = new ArrayList<>();
        List<Task> open = taskRepository.findByUserIdAndStatusInAndDeletedAtIsNull(userId,
                List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS));
        lines.add("Open tasks: " + open.size());
        List<Task> overdue = open.stream()
                .filter(task -> task.getDeadline() != null && task.getDeadline().isBefore(Instant.now()))
                .toList();
        lines.add("Overdue tasks: " + overdue.size());
        overdue.stream().limit(8).forEach(task -> lines.add("OVERDUE: " + task.getTitle()
                + " (deadline " + task.getDeadline().atZone(zone).toLocalDate() + ")"));
        open.stream()
                .filter(task -> task.getDeadline() != null && !task.getDeadline().isBefore(Instant.now()))
                .sorted(java.util.Comparator.comparing(Task::getDeadline))
                .limit(10)
                .forEach(task -> lines.add("DUE: " + task.getTitle()
                        + " on " + task.getDeadline().atZone(zone).toLocalDate()
                        + " (" + (task.getEstimatedMinutes() == null ? "no estimate" : task.getEstimatedMinutes() + " min estimated")
                        + ")"));
        List<Task> completed = taskRepository.findCompletedBetween(userId,
                DateSupport.startOfDay(today.minusDays(CONTEXT_DAYS), zone), Instant.now());
        lines.add("Tasks completed in the last " + CONTEXT_DAYS + " days: " + completed.size());
        return trim(lines);
    }

    private List<String> goalLines(String userId) {
        List<String> lines = new ArrayList<>();
        List<Goal> goals = goalRepository.findAllForUser(userId).stream()
                .filter(goal -> goal.getDeletedAt() == null)
                .toList();
        lines.add("Goals: " + goals.size());
        goals.stream().limit(15).forEach(goal -> lines.add("GOAL: " + goal.getTitle()
                + " [" + goal.getStatus() + ", " + goal.getProgress() + "% complete"
                + (goal.getTargetDate() == null ? ""
                : ", target " + goal.getTargetDate().atZone(ZoneId.of("UTC")).toLocalDate()) + "]"));
        return trim(lines);
    }

    private List<String> habitLines(String userId, LocalDate today) {
        List<String> lines = new ArrayList<>();
        List<Habit> habits = habitRepository.findByUserIdAndArchivedFalse(userId);
        lines.add("Active habits: " + habits.size());
        LocalDate from = today.minusDays(13);
        for (Habit habit : habits) {
            long last14 = habitLogRepository.countByUserIdAndHabitIdAndCompletedTrueAndLogDateBetween(
                    userId, habit.getId(), from, today);
            long last7 = habitLogRepository.countByUserIdAndHabitIdAndCompletedTrueAndLogDateBetween(
                    userId, habit.getId(), today.minusDays(6), today);
            lines.add("HABIT: " + habit.getName() + " (target " + habit.getTargetDays()
                    + " days/week, " + last7 + " of the last 7 days, " + last14 + " of the last 14)");
        }
        return trim(lines);
    }

    private List<String> focusLines(String userId, LocalDate today, ZoneId zone) {
        List<String> lines = new ArrayList<>();
        Instant from = DateSupport.startOfDay(today.minusDays(CONTEXT_DAYS), zone);
        long minutes = focusSessionRepository.sumMinutesBetween(userId, from, Instant.now());
        long sessions = focusSessionRepository.countCompletedBetween(userId, from, Instant.now());
        lines.add("Focus in the last " + CONTEXT_DAYS + " days: " + minutes + " minutes across " + sessions + " sessions");
        focusSessionRepository.findBetween(userId, from, Instant.now()).stream()
                .limit(8)
                .forEach(session -> lines.add("SESSION: " + session.getActualMinutes() + " min"
                        + (session.getTaskId() == null ? "" : " on task " + session.getTaskId())
                        + " on " + session.getStartedAt().atZone(zone).toLocalDate()));
        List<ProductivityMetric> metrics = metricRepository.findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(
                userId, today.minusDays(6), today);
        if (!metrics.isEmpty()) {
            double average = metrics.stream().mapToInt(ProductivityMetric::getProductivityScore).average().orElse(0);
            lines.add("Average daily productivity score over 7 days: " + Math.round(average * 10) / 10.0);
        }
        return trim(lines);
    }

    private List<String> learningLines(String userId, LocalDate today, ZoneId zone) {
        List<String> lines = new ArrayList<>();
        List<LearningGoal> goals = learningGoalRepository.findAllForUser(userId);
        lines.add("Learning goals: " + goals.size());
        goals.stream().limit(12).forEach(goal -> lines.add("LEARNING GOAL: " + goal.getTitle()
                + " [" + goal.getStatus() + ", " + goal.getProgress() + "%]"));
        long studyMinutes = focusStudyMinutes(userId, today, zone);
        lines.add("Study time in the last " + CONTEXT_DAYS + " days: " + studyMinutes + " minutes");
        return trim(lines);
    }

    private long focusStudyMinutes(String userId, LocalDate today, ZoneId zone) {
        return metricRepository.findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(
                        userId, today.minusDays(CONTEXT_DAYS - 1L), today)
                .stream().mapToLong(ProductivityMetric::getStudyMinutes).sum();
    }

    private List<String> financeLines(String userId, LocalDate today) {
        List<String> lines = new ArrayList<>();
        LocalDate monthStart = today.withDayOfMonth(1);
        BigDecimal income = transactionRepository.sumAmount(userId, TransactionType.INCOME, monthStart, today);
        BigDecimal expenses = transactionRepository.sumAmount(userId, TransactionType.EXPENSE, monthStart, today);
        lines.add("Income this month: " + (income == null ? 0 : income));
        lines.add("Expenses this month: " + (expenses == null ? 0 : expenses));
        BigDecimal net = (income == null ? BigDecimal.ZERO : income)
                .subtract(expenses == null ? BigDecimal.ZERO : expenses);
        lines.add("Net this month: " + net);
        transactionRepository.sumByCategory(userId, TransactionType.EXPENSE, monthStart, today).stream()
                .limit(8)
                .forEach(total -> lines.add("SPEND: " + total.getCategory() + " " + total.getTotal()));
        return trim(lines);
    }

    private List<String> journalLines(String userId, LocalDate today) {
        List<String> lines = new ArrayList<>();
        List<JournalEntry> entries = journalEntryRepository
                .findByUserIdAndDeletedAtIsNullAndEntryDateBetweenOrderByEntryDateDesc(
                        userId, today.minusDays(CONTEXT_DAYS), today);
        lines.add("Journal entries in the last " + CONTEXT_DAYS + " days: " + entries.size());
        entries.stream().limit(10).forEach(entry -> lines.add("ENTRY " + entry.getEntryDate()
                + (entry.getTitle() == null ? "" : " \"" + entry.getTitle() + "\"")
                + " (" + entry.getWordCount() + " words, mood " + entry.getMood() + ")"));
        return trim(lines);
    }

    private List<String> upcomingEvents(String userId, LocalDate today, ZoneId zone) {
        List<String> lines = new ArrayList<>();
        Instant from = DateSupport.startOfDay(today, zone);
        Instant to = DateSupport.startOfDay(today.plusDays(3), zone);
        List<CalendarEvent> events =
                calendarEventRepository.findByUserIdAndStartAtBetweenOrderByStartAtAsc(userId, from, to);
        lines.add("Calendar events in the next 3 days: " + events.size());
        events.stream().limit(10).forEach(event -> lines.add("EVENT: " + event.getTitle()
                + " on " + event.getStartAt().atZone(zone).toLocalDate()
                + (event.getLocation() == null || event.getLocation().isBlank()
                ? "" : " at " + event.getLocation())));
        return lines;
    }

    // ------------------------------------------------------------- internals

    private List<String> trim(List<String> lines) {
        if (lines.size() <= MAX_LINES_PER_SCOPE) {
            return lines;
        }
        List<String> trimmed = new ArrayList<>(lines.subList(0, MAX_LINES_PER_SCOPE));
        trimmed.add("... " + (lines.size() - MAX_LINES_PER_SCOPE) + " more lines omitted");
        return trimmed;
    }

    private boolean containsWord(String text, String word) {
        int index = text.indexOf(word);
        while (index >= 0) {
            boolean leftBoundary = index == 0 || !Character.isLetterOrDigit(text.charAt(index - 1));
            int end = index + word.length();
            boolean rightBoundary = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (leftBoundary && rightBoundary) {
                return true;
            }
            index = text.indexOf(word, index + 1);
        }
        return false;
    }

    /** The facts supplied to a completion, plus which scopes actually contributed data. */
    public record ContextSnapshot(List<String> lines, List<String> scopes, boolean nonEmpty) {
    }
}
