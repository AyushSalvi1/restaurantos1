package com.lifeos.bootstrap;

import com.lifeos.config.AppProperties;
import com.lifeos.entity.Budget;
import com.lifeos.entity.CalendarEvent;
import com.lifeos.entity.FinanceTransaction;
import com.lifeos.entity.FocusSession;
import com.lifeos.entity.Goal;
import com.lifeos.entity.GoalMilestone;
import com.lifeos.entity.Habit;
import com.lifeos.entity.HabitLog;
import com.lifeos.entity.JournalEntry;
import com.lifeos.entity.KnowledgeChunk;
import com.lifeos.entity.KnowledgeDocument;
import com.lifeos.entity.LearningGoal;
import com.lifeos.entity.SavingsGoal;
import com.lifeos.entity.Skill;
import com.lifeos.entity.Task;
import com.lifeos.entity.User;
import com.lifeos.entity.UserPreference;
import com.lifeos.entity.enums.BudgetPeriod;
import com.lifeos.entity.enums.Difficulty;
import com.lifeos.entity.enums.EnergyRequirement;
import com.lifeos.entity.enums.EventType;
import com.lifeos.entity.enums.FocusMode;
import com.lifeos.entity.enums.GoalStatus;
import com.lifeos.entity.enums.GoalType;
import com.lifeos.entity.enums.HabitFrequency;
import com.lifeos.entity.enums.JournalMood;
import com.lifeos.entity.enums.KnowledgeStatus;
import com.lifeos.entity.enums.LearningStatus;
import com.lifeos.entity.enums.Priority;
import com.lifeos.entity.enums.ProductivityStyle;
import com.lifeos.entity.enums.Role;
import com.lifeos.entity.enums.TaskStatus;
import com.lifeos.entity.enums.TransactionType;
import com.lifeos.entity.enums.UserStatus;
import com.lifeos.rag.EmbeddingService;
import com.lifeos.repository.BudgetRepository;
import com.lifeos.repository.CalendarEventRepository;
import com.lifeos.repository.FinanceTransactionRepository;
import com.lifeos.repository.FocusSessionRepository;
import com.lifeos.repository.GoalMilestoneRepository;
import com.lifeos.repository.GoalRepository;
import com.lifeos.repository.HabitLogRepository;
import com.lifeos.repository.HabitRepository;
import com.lifeos.repository.JournalEntryRepository;
import com.lifeos.repository.KnowledgeChunkRepository;
import com.lifeos.repository.KnowledgeDocumentRepository;
import com.lifeos.repository.LearningGoalRepository;
import com.lifeos.repository.SavingsGoalRepository;
import com.lifeos.repository.SkillRepository;
import com.lifeos.repository.TaskRepository;
import com.lifeos.repository.UserPreferenceRepository;
import com.lifeos.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates a single demo account with a spread of records across every life domain.
 *
 * <p>Everything else LIFEOS shows is derived from a user's own records, so an empty database renders as an
 * empty product: no trends, no insights, no balance scores, nothing for the assistant to reason over.
 * Seeding makes those features inspectable without hand-entering a month of data first.</p>
 *
 * <p>It is opt-in ({@code lifeos.app.seed-data-enabled}) and idempotent: if the demo account already
 * exists, nothing is written, so restarts never duplicate data. Only one account is created and it holds
 * no real person's information.</p>
 */
@Component
@ConditionalOnProperty(name = "lifeos.app.seed-data-enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private static final String SAMPLE_DOCUMENT = """
            Sleep and recovery notes

            Consistent wake time matters more than total hours in bed. Anchor the wake time first, then let
            sleep onset follow from the accumulated sleep pressure. Caffeine after midday shortens deep
            sleep even when total time stays stable, so the last coffee is the highest-leverage change.

            Morning light exposure within an hour of waking improves sleep the following night. A cool dark
            room around eighteen degrees helps most people fall asleep faster and reduces night waking.

            Movement earlier in the day raises deep sleep proportion. A twenty minute walk after lunch is
            enough to change the shape of the night without changing the total.
            """;

    private final AppProperties appProperties;
    private final PasswordEncoder passwordEncoder;
    private final EmbeddingService embeddingService;
    private final UserRepository userRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final GoalRepository goalRepository;
    private final GoalMilestoneRepository milestoneRepository;
    private final TaskRepository taskRepository;
    private final HabitRepository habitRepository;
    private final HabitLogRepository habitLogRepository;
    private final FocusSessionRepository focusSessionRepository;
    private final FinanceTransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final SavingsGoalRepository savingsGoalRepository;
    private final JournalEntryRepository journalRepository;
    private final LearningGoalRepository learningGoalRepository;
    private final SkillRepository skillRepository;
    private final CalendarEventRepository calendarRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;

    public DemoDataSeeder(AppProperties appProperties,
                          PasswordEncoder passwordEncoder,
                          EmbeddingService embeddingService,
                          UserRepository userRepository,
                          UserPreferenceRepository preferenceRepository,
                          GoalRepository goalRepository,
                          GoalMilestoneRepository milestoneRepository,
                          TaskRepository taskRepository,
                          HabitRepository habitRepository,
                          HabitLogRepository habitRepositoryLog,
                          FocusSessionRepository focusSessionRepository,
                          FinanceTransactionRepository transactionRepository,
                          BudgetRepository budgetRepository,
                          SavingsGoalRepository savingsGoalRepository,
                          JournalEntryRepository journalRepository,
                          LearningGoalRepository learningGoalRepository,
                          SkillRepository skillRepository,
                          CalendarEventRepository calendarRepository,
                          KnowledgeDocumentRepository documentRepository,
                          KnowledgeChunkRepository chunkRepository) {
        this.appProperties = appProperties;
        this.passwordEncoder = passwordEncoder;
        this.embeddingService = embeddingService;
        this.userRepository = userRepository;
        this.preferenceRepository = preferenceRepository;
        this.goalRepository = goalRepository;
        this.milestoneRepository = milestoneRepository;
        this.taskRepository = taskRepository;
        this.habitRepository = habitRepository;
        this.habitLogRepository = habitRepositoryLog;
        this.focusSessionRepository = focusSessionRepository;
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.savingsGoalRepository = savingsGoalRepository;
        this.journalRepository = journalRepository;
        this.learningGoalRepository = learningGoalRepository;
        this.skillRepository = skillRepository;
        this.calendarRepository = calendarRepository;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = appProperties.seedEmail().strip().toLowerCase();
        if (userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email).isPresent()) {
            log.info("Demo data already present for {}; skipping seed", email);
            return;
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(appProperties.seedPassword()));
        user.setFullName("Demo User");
        user.setTimezone("UTC");
        user.setLocale("en");
        user.setRole(Role.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(true);
        user.setOnboardingCompleted(true);
        user.setOccupation("Software Engineer");
        user = userRepository.save(user);
        String userId = user.getId();

        seedPreferences(userId);
        SeededGoals goals = seedGoals(userId);
        seedTasks(userId, goals);
        seedHabitsAndLogs(userId);
        seedFocusSessions(userId, goals.health().getId());
        seedFinance(userId);
        seedJournal(userId);
        seedLearning(userId);
        seedCalendar(userId, goals.health().getId());
        seedKnowledge(userId);

        log.info("""
                Demo data seeded. Sign in as {} with the configured demo password.
                Records: 3 goals, tasks, 3 habits with 21 days of logs, focus sessions, \
                transactions, journal entries, a learning goal and one indexed document.""", email);
    }

    // ------------------------------------------------------------- preferences

    private void seedPreferences(String userId) {
        UserPreference preferences = new UserPreference();
        preferences.setUserId(userId);
        preferences.setWorkingHoursStart(LocalTime.of(9, 0));
        preferences.setWorkingHoursEnd(LocalTime.of(17, 30));
        preferences.setDayStart(LocalTime.of(7, 0));
        preferences.setDayEnd(LocalTime.of(22, 30));
        preferences.setPreferredProductivityStyle(ProductivityStyle.BALANCED);
        preferences.setPreferredFocusMinutes(50);
        preferences.setBreakMinutes(10);
        preferences.setWeeklyProductivityHours(new BigDecimal("32.00"));
        preferences.setAreasOfInterest("sleep quality, strength training, systems thinking");
        preferences.setCurrentSkills("Java,TypeScript,PostgreSQL");
        preferences.setPrimaryGoalAreas("health,career,finance");
        preferenceRepository.save(preferences);
    }

    // ------------------------------------------------------------------ goals

    private SeededGoals seedGoals(String userId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        Goal health = goal(userId, "Sleep and recovery", "health", GoalType.LONG_TERM, 45, Priority.HIGH,
                "Wake at the same time daily and lift deep sleep above eighty percent.",
                today.plusDays(75), 0, "#16a34a");
        Goal career = goal(userId, "Ship the platform rewrite", "career", GoalType.LONG_TERM, 62,
                Priority.HIGH,
                "Reach a deployable release with migrations applied.", today.plusDays(40), 1, "#2563eb");
        Goal finance = goal(userId, "Six months of runway", "finance", GoalType.SHORT_TERM, 30,
                Priority.MEDIUM,
                "Save enough to cover six months of fixed costs.", today.plusDays(120), 2, "#ca8a04");
        goalRepository.saveAll(List.of(health, career, finance));

        milestoneRepository.saveAll(List.of(
                milestone(userId, health.getId(), "Wake time held for 21 consecutive days", 100, true, 0),
                milestone(userId, health.getId(), "Caffeine before noon only", 60, false, 1),
                milestone(userId, career.getId(), "Migrations green on MySQL", 100, true, 0),
                milestone(userId, career.getId(), "Load test under 500 rps", 35, false, 1)));

        return new SeededGoals(health, career);
    }

    /** The two goals tasks link against. */
    private record SeededGoals(Goal health, Goal career) {
    }

    private Goal goal(String userId, String title, String category, GoalType type, int progress, Priority priority,
                      String description, LocalDate targetDate, int position, String color) {
        Goal goal = new Goal();
        goal.setUserId(userId);
        goal.setTitle(title);
        goal.setCategory(category);
        goal.setGoalType(type);
        goal.setProgress(progress);
        goal.setPriority(priority);
        goal.setDescription(description);
        goal.setTargetDate(targetDate.atStartOfDay().toInstant(ZoneOffset.UTC));
        goal.setPosition(position);
        goal.setColor(color);
        goal.setStatus(GoalStatus.ACTIVE);
        return goal;
    }

    private GoalMilestone milestone(String userId, String goalId, String title, int progress, boolean done,
                                    int position) {
        GoalMilestone milestone = new GoalMilestone();
        milestone.setUserId(userId);
        milestone.setGoalId(goalId);
        milestone.setTitle(title);
        milestone.setProgress(progress);
        milestone.setCompleted(done);
        milestone.setPosition(position);
        return milestone;
    }

    // ------------------------------------------------------------------ tasks

    private void seedTasks(String userId, SeededGoals goals) {
        String healthGoalId = goals.health().getId();
        String careerGoalId = goals.career().getId();

        taskRepository.saveAll(List.of(
                task(userId, "Cut the nightly caffeine", "health", TaskStatus.IN_PROGRESS, Priority.HIGH,
                        20, healthGoalId, 0),
                task(userId, "Add the missing schema validation test", "engineering",
                        TaskStatus.IN_PROGRESS, Priority.HIGH, 90, careerGoalId, 1),
                task(userId, "Write the onboarding walkthrough", "documentation", TaskStatus.TODO,
                        Priority.MEDIUM, 120, careerGoalId, 2),
                task(userId, "Review the monthly finance review", "finance", TaskStatus.TODO,
                        Priority.MEDIUM, 45, null, 3),
                task(userId, "Renew gym membership", "health", TaskStatus.TODO, Priority.LOW, 10,
                        healthGoalId, 4),
                task(userId, "Migrate the audit log table", "engineering", TaskStatus.COMPLETED, Priority.HIGH,
                        150, careerGoalId, 5),
                task(userId, "Plan the week around deep work", "planning", TaskStatus.COMPLETED,
                        Priority.MEDIUM, 30, null, 6)));
    }

    private Task task(String userId, String title, String category, TaskStatus status, Priority priority,
                      int estimatedMinutes, String goalId, int position) {
        Task task = new Task();
        task.setUserId(userId);
        task.setTitle(title);
        task.setCategory(category);
        task.setStatus(status);
        task.setPriority(priority);
        task.setEstimatedMinutes(estimatedMinutes);
        task.setGoalId(goalId);
        task.setPosition(position);
        task.setDifficulty(Difficulty.MEDIUM);
        task.setEnergyRequirement(EnergyRequirement.MEDIUM);
        task.setTags(category);
        if (status == TaskStatus.COMPLETED) {
            task.setActualMinutes(Math.max(1, estimatedMinutes - 5));
            task.setCompletedAt(Instant.now().minusSeconds(86_400L));
        }
        return task;
    }

    // ----------------------------------------------------------------- habits

    private void seedHabitsAndLogs(String userId) {
        Habit water = habit(userId, "Morning water", "health", HabitFrequency.DAILY, 1);
        Habit movement = habit(userId, "Thirty minutes of movement", "health", HabitFrequency.DAILY, 1);
        Habit reading = habit(userId, "Read twenty pages", "learning", HabitFrequency.DAILY, 1);
        habitRepository.saveAll(List.of(water, movement, reading));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<HabitLog> logs = new ArrayList<>();
        for (int daysAgo = 0; daysAgo < 21; daysAgo++) {
            LocalDate day = today.minusDays(daysAgo);
            boolean weekend = day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY;
            logs.add(log(userId, water.getId(), day, !weekend && daysAgo % 7 != 3));
            logs.add(log(userId, movement.getId(), day, daysAgo % 3 != 0));
            logs.add(log(userId, reading.getId(), day, daysAgo % 4 != 1));
        }
        habitLogRepository.saveAll(logs);
    }

    private Habit habit(String userId, String name, String category, HabitFrequency frequency, int timesPerPeriod) {
        Habit habit = new Habit();
        habit.setUserId(userId);
        habit.setName(name);
        habit.setCategory(category);
        habit.setFrequencyType(frequency);
        habit.setTimesPerPeriod(timesPerPeriod);
        return habit;
    }

    private HabitLog log(String userId, String habitId, LocalDate day, boolean completed) {
        HabitLog log = new HabitLog();
        log.setUserId(userId);
        log.setHabitId(habitId);
        log.setLogDate(day);
        log.setCompleted(completed);
        return log;
    }

    // ------------------------------------------------------------------ focus

    private void seedFocusSessions(String userId, String healthGoalId) {
        Instant now = Instant.now();
        List<FocusSession> sessions = new ArrayList<>();
        int[] minutes = {25, 50, 50, 25, 75, 50, 25, 50, 50, 25, 75, 50};
        for (int index = 0; index < minutes.length; index++) {
            int daysAgo = index / 2;
            LocalDate day = LocalDate.now(ZoneOffset.UTC).minusDays(daysAgo);
            if (day.getDayOfWeek() == DayOfWeek.SATURDAY || day.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            Instant started = day.atTime(9 + (index % 4), 0).toInstant(ZoneOffset.UTC);
            if (started.isAfter(now)) {
                continue;
            }
            FocusSession session = new FocusSession();
            session.setUserId(userId);
            session.setGoalId(healthGoalId);
            session.setMode(minutes[index] >= 50 ? FocusMode.DEEP_WORK : FocusMode.POMODORO_25_5);
            session.setPlannedMinutes(minutes[index]);
            // A realistic amount of drift rather than perfect completion on every session.
            session.setActualMinutes(minutes[index] - (index % 4));
            session.setStartedAt(started);
            session.setEndedAt(started.plusSeconds(session.getActualMinutes() * 60L));
            session.setCompleted(true);
            session.setInterruptedCount(index % 3);
            session.setRating(3 + (index % 3));
            session.setOutcome(index % 3 == 0 ? "Interrupted once" : null);
            sessions.add(session);
        }
        focusSessionRepository.saveAll(sessions);
    }

    // ---------------------------------------------------------------- finance

    private void seedFinance(String userId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<FinanceTransaction> transactions = new ArrayList<>();
        for (int monthsAgo = 2; monthsAgo >= 0; monthsAgo--) {
            LocalDate month = today.minusMonths(monthsAgo).withDayOfMonth(1);
            transactions.add(transaction(userId, TransactionType.INCOME, "4200.00", "salary", "Monthly salary",
                    month));
            transactions.add(transaction(userId, TransactionType.EXPENSE, "1450.00", "housing", "Rent",
                    month.withDayOfMonth(2)));
            transactions.add(transaction(userId, TransactionType.EXPENSE, "520.00", "food", "Groceries",
                    month.withDayOfMonth(4)));
            transactions.add(transaction(userId, TransactionType.EXPENSE, "180.00", "transport", "Transit pass",
                    month.withDayOfMonth(5)));
            transactions.add(transaction(userId, TransactionType.EXPENSE, "96.00", "health", "Gym",
                    month.withDayOfMonth(6)));
            transactions.add(transaction(userId, TransactionType.EXPENSE, "64.00", "utilities", "Utilities",
                    month.withDayOfMonth(8)));
            transactions.add(transaction(userId, TransactionType.EXPENSE, "42.00", "learning", "Books",
                    month.withDayOfMonth(11)));
        }
        transactionRepository.saveAll(transactions);

        Budget budget = new Budget();
        budget.setUserId(userId);
        budget.setCategory("food");
        budget.setPeriod(BudgetPeriod.MONTHLY);
        budget.setAmount(new BigDecimal("600.00"));
        budget.setStartDate(today.withDayOfMonth(1).minusMonths(2));
        budget.setEndDate(today.withDayOfMonth(1).plusMonths(1).minusDays(1));
        budgetRepository.save(budget);

        SavingsGoal savings = new SavingsGoal();
        savings.setUserId(userId);
        savings.setName("Emergency fund");
        savings.setTargetAmount(new BigDecimal("18000.00"));
        savings.setSavedAmount(new BigDecimal("6500.00"));
        savings.setTargetDate(today.plusDays(240));
        savingsGoalRepository.save(savings);
    }

    private FinanceTransaction transaction(String userId, TransactionType type, String amount, String category,
                                           String description, LocalDate occurredOn) {
        FinanceTransaction transaction = new FinanceTransaction();
        transaction.setUserId(userId);
        transaction.setTransactionType(type);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setCategory(category);
        transaction.setDescription(description);
        transaction.setOccurredOn(occurredOn);
        return transaction;
    }

    // ----------------------------------------------------------------- journal

    private void seedJournal(String userId) {
        journalRepository.saveAll(List.of(
                journal(userId, "Better nights after cutting the evening coffee", """
                        Two weeks without caffeine after four in the afternoon and the difference is not
                        subtle. I am awake less often and the first hour of the day is usable instead of
                        spent recovering. The trade is that the evening is less social, which is a real cost
                        and not a small one.""",
                        JournalMood.GOOD, 4, today(2), "sleep,caffeine"),
                journal(userId, "The rewrite is behind the hard part", """
                        Migrations run clean on MySQL now, which was the piece I kept deferring. What is
                        left is mostly wiring and the parts where I have to decide what not to build.""",
                        JournalMood.GOOD, 4, today(5), "work,shipping"),
                journal(userId, "Overloaded the week again", """
                        Four deep sessions in a row and nothing left for the people I live with. The
                        planning looked fine on paper. It is not a time problem, it is that I keep saying
                        yes to the urgent thing over the important one.""",
                        JournalMood.NEUTRAL, 3, today(9), "balance,overcommitment"),
                journal(userId, "Ran the half marathon", """
                        Slower than training for and finished comfortably, which is the right outcome. The
                        legs were fine, the pacing was not. Sleeping well for the last month is most of
                        why it did not fall apart at ten kilometres.""",
                        JournalMood.GREAT, 5, today(16), "running,training")));
    }

    private JournalEntry journal(String userId, String title, String content, JournalMood mood, int moodScore,
                                 LocalDate entryDate, String tags) {
        JournalEntry entry = new JournalEntry();
        entry.setUserId(userId);
        entry.setTitle(title);
        entry.setContent(content);
        entry.setEntryDate(entryDate);
        entry.setMood(mood);
        entry.setMoodScore(moodScore);
        entry.setTags(tags);
        entry.setWordCount(content.split("\\s+").length);
        return entry;
    }

    private LocalDate today(int daysAgo) {
        return LocalDate.now(ZoneOffset.UTC).minusDays(daysAgo);
    }

    // --------------------------------------------------------------- learning

    private void seedLearning(String userId) {
        Skill postgres = new Skill();
        postgres.setUserId(userId);
        postgres.setName("PostgreSQL");
        postgres.setCategory("data");
        postgres.setProficiency(3);
        skillRepository.save(postgres);

        LearningGoal learning = new LearningGoal();
        learning.setUserId(userId);
        learning.setTitle("Query tuning and index design");
        learning.setDescription("Understand planner behaviour well enough to fix slow queries without guessing.");
        learning.setCategory("data");
        learning.setProgress(40);
        learning.setStatus(LearningStatus.ACTIVE);
        learning.setHoursSpent(new BigDecimal("18.50"));
        learning.setSkillId(postgres.getId());
        learning.setTargetDate(Instant.now().plusSeconds(60L * 60L * 24L * 45L));
        learningGoalRepository.save(learning);
    }

    // --------------------------------------------------------------- calendar

    private void seedCalendar(String userId, String healthGoalId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        calendarRepository.saveAll(List.of(
                event(userId, "Morning walk", EventType.EVENT,
                        today.atTime(7, 30).atZone(ZoneOffset.UTC).toInstant(),
                        today.atTime(8, 0).atZone(ZoneOffset.UTC).toInstant(),
                        "Thirty minutes before the desk opens.", healthGoalId),
                event(userId, "Weekly review", EventType.EVENT,
                        today.plusDays(1).atTime(16, 0).atZone(ZoneOffset.UTC).toInstant(),
                        today.plusDays(1).atTime(17, 0).atZone(ZoneOffset.UTC).toInstant(),
                        "Clear the board and set the three that matter.", null),
                event(userId, "Dentist", EventType.EVENT,
                        today.plusDays(4).atTime(9, 15).atZone(ZoneOffset.UTC).toInstant(),
                        today.plusDays(4).atTime(10, 0).atZone(ZoneOffset.UTC).toInstant(),
                        "Six month check.", null)));
    }

    private CalendarEvent event(String userId, String title, EventType type, Instant start, Instant end,
                                String description, String goalId) {
        CalendarEvent event = new CalendarEvent();
        event.setUserId(userId);
        event.setTitle(title);
        event.setEventType(type);
        event.setStartAt(start);
        event.setEndAt(end);
        event.setDescription(description);
        event.setGoalId(goalId);
        return event;
    }

    // -------------------------------------------------------------- knowledge

    /**
     * Seeds an already-indexed document rather than uploading one, because ingestion runs asynchronously
     * after the upload transaction commits. Writing the chunks here means the retrieval path is populated
     * by the time the seeder returns, so the assistant has real passages to cite.
     */
    private void seedKnowledge(String userId) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setUserId(userId);
        document.setTitle("Sleep and recovery notes");
        document.setFilename("sleep-and-recovery.txt");
        document.setContentType("text/plain");
        document.setExtension("txt");
        document.setSizeBytes(SAMPLE_DOCUMENT.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        document.setStatus(KnowledgeStatus.READY);
        document.setWordCount(SAMPLE_DOCUMENT.split("\\s+").length);
        document.setChunkCount(1);
        document = documentRepository.save(document);

        KnowledgeChunk chunk = new KnowledgeChunk();
        chunk.setDocumentId(document.getId());
        chunk.setUserId(userId);
        chunk.setChunkIndex(0);
        chunk.setContent(SAMPLE_DOCUMENT.strip());
        chunk.setTokenEstimate(SAMPLE_DOCUMENT.split("\\s+").length);
        chunk.setEmbedding(embeddingService.serialise(
                embeddingService.embed(List.of(SAMPLE_DOCUMENT.strip())).get(0)));
        chunk.setEmbeddingModel(embeddingService.model());
        chunkRepository.save(chunk);
    }
}