package com.lifeos.dto;

import com.lifeos.entity.enums.GraphRelation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Analytics, insights, predictions, life balance and life graph payloads. */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record RangeQuery(LocalDate from, LocalDate to) {
        public record Normalized(LocalDate from, LocalDate to) {
        }
    }

    public record AnalyticsSummary(
            LocalDate from,
            LocalDate to,
            String timezone,
            long tasksCreated,
            long tasksCompleted,
            long tasksMissed,
            double taskCompletionRate,
            double averageProductivityScore,
            int focusMinutes,
            int studyMinutes,
            long focusSessions,
            long habitCompletions,
            double habitConsistencyRate,
            long habitScheduled,
            long calendarEvents,
            BigDecimal income,
            BigDecimal expenses,
            BigDecimal savings,
            long goalProgressPoints,
            double score
    ) {
    }

    public record ProductivityPoint(LocalDate date, int tasksCompleted, int tasksCreated, int focusMinutes,
                                    int studyMinutes, int habitsCompleted, int productivityScore) {
    }

    public record WeeklyProductivity(LocalDate weekStart, int tasksCompleted, int focusMinutes, int studyMinutes,
                                     double averageScore) {
    }

    public record MonthlyProductivity(YearMonthHolder month, int tasksCompleted, int focusMinutes, int studyMinutes,
                                      double averageScore, double completionRate) {
        public record YearMonthHolder(int year, int month) {
        }
    }

    public record GoalProgressPoint(String goalId, String title, int progress, String status, long tasksCompleted,
                                    long tasksTotal) {
    }

    public record CategoryShare(String category, java.math.BigDecimal total, double sharePercent) {
    }

    public record AnalyticsResponse(
            LocalDate from,
            LocalDate to,
            AnalyticsSummary summary,
            List<ProductivityPoint> daily,
            List<WeeklyProductivity> weekly,
            List<MonthlyProductivity> monthly,
            List<GoalProgressPoint> goalProgress,
            List<CategoryShare> spendingByCategory,
            List<CategoryShare> incomeByCategory,
            List<InsightDtos.InsightResponse> insights,
            List<PredictionResponse> predictions,
            BalanceScore lifeBalance,
            List<String> dataGaps
    ) {
    }

    // ---------------------------------------------------------- life balance

    public record BalanceDimension(
            String key,
            String label,
            int score,
            double weight,
            String explanation,
            List<String> inputs,
            boolean sufficientData
    ) {
    }

    public record BalanceScore(
            int overallScore,
            String grade,
            List<BalanceDimension> dimensions,
            String formula,
            String disclaimer,
            boolean sufficientData
    ) {
    }

    public record UpdateBalanceWeightsRequest(@NotNull java.util.Map<String, @Min(0) @Max(100) Integer> weights) {
    }

    // ------------------------------------------------------------ prediction

    public record PredictionResponse(
            String id,
            String predictionType,
            String subjectType,
            String subjectId,
            String label,
            int probability,
            int confidence,
            List<String> factors,
            int horizonDays,
            Instant createdAt,
            Instant expiresAt,
            String disclaimer
    ) {
    }

    // ------------------------------------------------------------- life graph

    public record GraphNode(
            String id,
            String type,
            String label,
            String subtitle,
            String status,
            String color,
            int weight,
            java.util.Map<String, Object> detail
    ) {
    }

    public record GraphEdge(
            String id,
            String source,
            String target,
            String relation,
            double weight
    ) {
    }

    public record GraphResponse(
            List<GraphNode> nodes,
            List<GraphEdge> edges,
            int nodeCount,
            int edgeCount,
            String focusNodeId,
            List<GraphNode> neighbours
    ) {
    }

    public record GraphNeighbourhoodResponse(
            GraphNode node,
            List<GraphEdge> edges,
            List<GraphNode> neighbours,
            List<String> pathToGoal
    ) {
    }

    public record CreateEdgeRequest(
            @NotNull com.lifeos.entity.enums.GraphNodeType sourceType,
            @NotNull String sourceId,
            @NotNull com.lifeos.entity.enums.GraphNodeType targetType,
            @NotNull String targetId,
            @NotNull GraphRelation relation,
            Double weight
    ) {
    }

    public record CreateNodeRequest(
            @NotNull com.lifeos.entity.enums.GraphNodeType type,
            @NotNull String label,
            String subtitle,
            @Min(1) @Max(100) Integer weight
    ) {
    }
}