package com.lifeos.dto;

import com.lifeos.entity.enums.LearningStatus;
import com.lifeos.entity.enums.Origin;
import com.lifeos.entity.enums.ResourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Learning management payloads: goals, topics, sessions, resources and skills. */
public final class LearningDtos {

    private LearningDtos() {
    }

    public record LearningGoalRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 20000) String description,
            @Size(max = 48) String category,
            Instant targetDate,
            LearningStatus status,
            String skillId,
            @Valid List<TopicRequest> topics
    ) {
    }

    public record TopicRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 20000) String description,
            @Min(0) @Max(100000) Integer estimatedMinutes,
            Integer position
    ) {
    }

    public record TopicResponse(
            String id,
            String title,
            String description,
            int position,
            boolean completed,
            Instant completedAt,
            int estimatedMinutes
    ) {
    }

    public record SessionRequest(
            String learningGoalId,
            String topicId,
            @NotNull Instant startedAt,
            Instant endedAt,
            @Min(1) @Max(1440) Integer minutes,
            @Size(max = 20000) String notes,
            @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal quizScore
    ) {
    }

    public record SessionResponse(
            String id,
            String learningGoalId,
            String learningGoalTitle,
            String topicId,
            String topicTitle,
            Instant startedAt,
            Instant endedAt,
            int minutes,
            String notes,
            BigDecimal quizScore
    ) {
    }

    public record ResourceRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 500) String url,
            ResourceType resourceType,
            String learningGoalId,
            Boolean completed
    ) {
    }

    public record ResourceResponse(
            String id,
            String title,
            String url,
            ResourceType resourceType,
            String learningGoalId,
            boolean completed
    ) {
    }

    public record SkillRequest(@NotBlank @Size(max = 120) String name, @Size(max = 48) String category,
                               @Min(1) @Max(5) Integer proficiency) {
    }

    public record SkillResponse(String id, String name, String category, int proficiency) {
    }

    public record LearningGoalResponse(
            String id,
            String title,
            String description,
            String category,
            Instant targetDate,
            int progress,
            LearningStatus status,
            BigDecimal hoursSpent,
            String skillId,
            Origin source,
            boolean aiConfirmed,
            long topicCount,
            long completedTopicCount,
            List<TopicResponse> topics
    ) {
    }

    public record LearningStats(
            long activeGoals,
            double hoursThisWeek,
            double hoursTotal,
            int minutesThisWeek,
            long sessionsThisWeek,
            double averageQuizScore,
            List<LearningGoalResponse> topGoals
    ) {
    }

    /** AI-generated roadmap. Persisted only after explicit user confirmation. */
    public record RoadmapProposal(
            String title,
            String description,
            String category,
            Integer targetInDays,
            List<RoadmapStage> stages,
            List<String> resourceSuggestions,
            String rationale,
            boolean requiresConfirmation
    ) {
        public record RoadmapStage(String title, String description, Integer estimatedHours, List<String> topics) {
        }
    }

    public record ConfirmRoadmapRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 20000) String description,
            @Size(max = 48) String category,
            Instant targetDate,
            @NotNull @Valid List<RoadmapStageRequest> stages
    ) {
    }

    public record RoadmapStageRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 20000) String description,
            @Min(1) @Max(10000) Integer estimatedHours,
            @NotNull @Valid List<@NotBlank @Size(max = 200) String> topics
    ) {
    }

    public record DailyStudyPoint(LocalDate date, int minutes, int topicsCompleted) {
    }
}