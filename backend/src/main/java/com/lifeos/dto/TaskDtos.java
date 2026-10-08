package com.lifeos.dto;

import com.lifeos.entity.enums.Difficulty;
import com.lifeos.entity.enums.EnergyRequirement;
import com.lifeos.entity.enums.Priority;
import com.lifeos.entity.enums.TaskStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Task management payloads. */
public final class TaskDtos {

    private TaskDtos() {
    }

    public record TaskRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 20000) String description,
            @Size(max = 20000) String notes,
            TaskStatus status,
            Priority priority,
            @Size(max = 48) String category,
            Instant deadline,
            @Min(1) @Max(100000) Integer estimatedMinutes,
            @Min(0) @Max(100000) Integer actualMinutes,
            Difficulty difficulty,
            EnergyRequirement energyRequirement,
            List<String> tags,
            String goalId,
            String milestoneId,
            String projectId,
            @Size(max = 120) String recurrenceRule,
            Instant recurrenceEndDate,
            List<String> dependsOnTaskIds,
            Integer position
    ) {
        /** Widens the quick-create form into a full request so both paths share validation and defaults. */
        public static TaskRequest fromQuick(QuickCreateRequest quick) {
            return new TaskRequest(
                    quick.title(), null, null, null, quick.priority(), quick.category(), quick.deadline(),
                    quick.estimatedMinutes(), null, null, null, List.of(), quick.goalId(), null, null, null, null,
                    List.of(), null);
        }
    }

    public record QuickCreateRequest(
            @NotBlank @Size(max = 200) String title,
            Priority priority,
            Instant deadline,
            @Min(1) @Max(100000) Integer estimatedMinutes,
            String goalId,
            String category
    ) {
    }

    public record StatusUpdateRequest(@NotNull TaskStatus status, @Min(0) @Max(100000) Integer actualMinutes) {
    }

    public record CompleteRequest(@Min(0) @Max(100000) Integer actualMinutes, @Size(max = 20000) String notes) {
    }

    public record DependencyRequest(@NotBlank String taskId) {
    }

    public record ReorderRequest(@NotNull List<PositionedTask> tasks) {
    }

    public record PositionedTask(@NotBlank String id, @Min(0) Integer position) {
    }

    public record BulkActionRequest(
            @NotNull List<String> taskIds,
            @NotNull BulkAction action,
            TaskStatus status,
            Priority priority,
            String goalId,
            String projectId,
            String category
    ) {
    }

    public enum BulkAction {
        COMPLETE,
        REOPEN,
        CANCEL,
        DELETE,
        SET_PRIORITY,
        MOVE_TO_GOAL,
        MOVE_TO_PROJECT,
        SET_CATEGORY
    }

    public record BulkActionResult(int affected, List<String> failedIds, String message) {
    }

    public record DependencyResponse(String taskId, String title, TaskStatus status) {
    }

    public record TaskResponse(
            String id,
            String title,
            String description,
            String notes,
            TaskStatus status,
            Priority priority,
            String category,
            Instant deadline,
            Integer estimatedMinutes,
            int actualMinutes,
            Difficulty difficulty,
            EnergyRequirement energyRequirement,
            List<String> tags,
            String goalId,
            String milestoneId,
            String projectId,
            String recurrenceRule,
            String recurrenceParentId,
            Instant recurrenceEndDate,
            int position,
            Instant completedAt,
            Instant createdAt,
            Instant updatedAt,
            List<String> dependsOnTaskIds,
            List<DependencyResponse> blocks
    ) {
    }

    public record TaskBoardColumn(TaskStatus status, String label, List<TaskResponse> tasks) {
    }
}