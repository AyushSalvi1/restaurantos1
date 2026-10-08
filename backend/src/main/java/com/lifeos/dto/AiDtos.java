package com.lifeos.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** AI assistant, daily planner and provider status payloads. */
public final class AiDtos {

    private AiDtos() {
    }

    public record ChatRequest(
            @NotBlank @Size(max = 8000) String message,
            String conversationId,
            List<String> contextScopes,
            @Min(1) @Max(40) Integer historyLimit
    ) {
    }

    public record ChatResponse(
            String conversationId,
            String messageId,
            String reply,
            List<String> contextUsed,
            String provider,
            String model,
            boolean grounded,
            List<KnowledgeDtos.SearchHit> citations,
            Instant createdAt
    ) {
    }

    public record ConversationSummary(
            String id,
            String title,
            int messageCount,
            Instant lastMessageAt,
            Instant createdAt
    ) {
    }

    public record MessageResponse(
            String id,
            String role,
            String content,
            String provider,
            String model,
            List<KnowledgeDtos.SearchHit> citations,
            Instant createdAt
    ) {
    }

    public record ConversationDetail(ConversationSummary summary, List<MessageResponse> messages) {
    }

    // ---------------------------------------------------------------- planning

    public record PlanDayRequest(
            LocalDate date,
            @Min(30) @Max(1440) Integer availableMinutes,
            @Min(10) @Max(240) Integer focusBlockMinutes,
            @Min(0) @Max(120) Integer breakMinutes,
            @Min(1) @Max(5) Integer energyLevel,
            Boolean includeHabits,
            Boolean includeBreaks,
            Boolean useAi,
            List<String> excludeTaskIds
    ) {
    }

    public record PlanBlock(
            String id,
            LocalDate date,
            Instant startAt,
            Instant endAt,
            int durationMinutes,
            String kind,
            String title,
            String detail,
            String taskId,
            String goalId,
            String habitId,
            String priority,
            String energyFit,
            boolean movable,
            boolean locked
    ) {
    }

    public record PlanDayResponse(
            LocalDate date,
            int availableMinutes,
            int scheduledMinutes,
            int breakMinutes,
            double utilisationPercent,
            List<PlanBlock> blocks,
            List<String> unscheduledTaskIds,
            List<String> warnings,
            String rationale,
            String generatedBy
    ) {
    }

    public record PlanUpdateBlockRequest(
            String blockId,
            Instant startAt,
            Instant endAt,
            @Size(max = 300) String title,
            String taskId,
            Boolean locked
    ) {
    }

    public record PlanUpdateRequest(List<PlanUpdateBlockRequest> blocks) {
    }

    public record Recommendation(
            String id,
            String type,
            String title,
            String rationale,
            int score,
            String actionLabel,
            String actionPath,
            List<String> supportingData
    ) {
    }

    public record RecommendationsResponse(
            List<Recommendation> recommendations,
            String generatedAt,
            String generatedBy
    ) {
    }

    // ------------------------------------------------------------- providers

    public record ProviderStatus(
            String configuredProvider,
            String activeProvider,
            boolean remoteProviderConfigured,
            boolean degraded,
            List<ProviderAvailability> providers,
            String note
    ) {
        public record ProviderAvailability(String name, boolean configured, String detail) {
        }
    }
}