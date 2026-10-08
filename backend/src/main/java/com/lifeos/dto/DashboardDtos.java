package com.lifeos.dto;

import com.lifeos.entity.enums.Priority;
import com.lifeos.entity.enums.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Dashboard payloads. Every field is derived from stored user data. */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record Greeting(
            String salutation,
            String message,
            String displayName,
            LocalDate date,
            String timezone,
            Instant serverTime
    ) {
    }

    public record FocusItem(
            int rank,
            String taskId,
            String title,
            Priority priority,
            String category,
            Instant deadline,
            Integer estimatedMinutes,
            String reason,
            boolean blocked,
            List<String> blockedBy
    ) {
    }

    public record CountCard(String label, long value, String trend, String link) {
    }

    public record ProgressBar(String id, String label, int progress, String link, String meta) {
    }

    public record UpcomingEvent(
            String id,
            String title,
            Instant startAt,
            Instant endAt,
            String type,
            String location,
            boolean allDay
    ) {
    }

    public record TodaySummary(
            long tasksDue,
            long tasksCompleted,
            long tasksOverdue,
            long openTasks,
            int estimatedMinutes,
            int focusMinutesToday,
            int studyMinutesToday,
            long habitsCompleted,
            long habitsScheduled,
            long calendarEvents
    ) {
    }

    public record HabitMini(
            String id,
            String name,
            boolean completedToday,
            int currentStreak,
            String reminderTime
    ) {
    }

    public record DashboardResponse(
            Greeting greeting,
            String timezone,
            TodaySummary today,
            List<FocusItem> todaysFocus,
            List<ProgressBar> goalProgress,
            List<HabitMini> habits,
            List<UpcomingEvent> upcomingEvents,
            List<CountCard> summaryCards,
            FinanceDtos.FinanceOverview finance,
            LearningDtos.LearningStats learning,
            int productivityScore,
            String productivityExplanation,
            AnalyticsDtos.BalanceScore lifeBalance,
            List<InsightDtos.InsightResponse> insights,
            List<AiDtos.Recommendation> recommendations,
            List<PredictionResponse> predictions,
            List<String> dataGaps
    ) {
        public record PredictionResponse(String id, String type, String label, int probability, List<String> factors) {
        }
    }

    public record TodayResponse(
            LocalDate date,
            Greeting greeting,
            TodaySummary summary,
            List<FocusItem> todaysFocus,
            List<HabitMini> habits,
            List<UpcomingEvent> schedule,
            List<TaskDtos.TaskResponse> overdue,
            List<TaskDtos.TaskResponse> dueToday,
            AnalyticsDtos.BalanceScore lifeBalance,
            List<AiDtos.Recommendation> recommendations
    ) {
    }
}