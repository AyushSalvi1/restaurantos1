package com.lifeos.dto;

import com.lifeos.entity.enums.GoalStatus;
import com.lifeos.entity.enums.GoalType;
import com.lifeos.entity.enums.Origin;
import com.lifeos.entity.enums.Priority;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Goal, milestone and sub-goal payloads. */
public final class GoalDtos {

    private GoalDtos() {
    }

    public record GoalRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 20000) String description,
            @Size(max = 48) String category,
            GoalType goalType,
            Instant targetDate,
            @Min(0) @Max(100) Integer progress,
            Priority priority,
            GoalStatus status,
            @Size(max = 16) String color,
            String parentGoalId,
            Integer position,
            @Valid List<MilestoneRequest> milestones
    ) {
    }

    public record MilestoneRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 20000) String description,
            Instant dueDate,
            Boolean completed,
            @Min(0) @Max(100) Integer progress,
            Integer position
    ) {
    }

    public record MilestoneResponse(
            String id,
            String goalId,
            String title,
            String description,
            Instant dueDate,
            boolean completed,
            int progress,
            int position
    ) {
    }

    public record ProgressUpdateRequest(
            @Min(0) @Max(100) Integer progress,
            GoalStatus status
    ) {
    }

    public record GoalResponse(
            String id,
            String title,
            String description,
            String category,
            GoalType goalType,
            Instant targetDate,
            int progress,
            Priority priority,
            GoalStatus status,
            String color,
            Origin source,
            boolean aiConfirmed,
            String parentGoalId,
            int position,
            Instant createdAt,
            Instant updatedAt,
            long taskCount,
            long completedTaskCount,
            long milestoneCount,
            long completedMilestoneCount,
            List<MilestoneResponse> milestones,
            List<GoalResponse> subGoals
    ) {
    }

    public record GoalSummary(
            String id,
            String title,
            int progress,
            GoalStatus status,
            Instant targetDate,
            long taskCount,
            long completedTaskCount
    ) {
    }

    /** Result of asking the AI to decompose a goal. Nothing is persisted until the user confirms. */
    public record AiGoalProposal(
            String title,
            String description,
            String category,
            GoalType goalType,
            Priority priority,
            Integer targetInDays,
            List<AiMilestoneProposal> milestones,
            List<AiTaskProposal> suggestedTasks,
            String rationale,
            boolean requiresConfirmation
    ) {
        public static AiGoalProposal of(String title) {
            return new AiGoalProposal(title, null, null, GoalType.LONG_TERM, Priority.MEDIUM, null,
                    List.of(), List.of(), null, true);
        }
    }

    public record AiMilestoneProposal(String title, String description, Integer targetDay, List<String> topics) {
    }

    public record AiTaskProposal(String title, Priority priority, Integer estimatedMinutes, String milestoneTitle) {
    }

    /** User-edited proposal submitted for confirmation. */
    public record ConfirmProposalRequest(
            @NotNull @Valid GoalRequest goal,
            List<@Valid MilestoneRequest> milestones
    ) {
    }
}