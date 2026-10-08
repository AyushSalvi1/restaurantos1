package com.lifeos.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Administrative payloads. Deliberately excludes journal and knowledge document content. */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record SystemStats(
            long totalUsers,
            long activeUsers,
            long disabledUsers,
            long usersRegisteredLast7Days,
            long totalTasks,
            long totalGoals,
            long totalHabits,
            long totalDocuments,
            long totalChunks,
            long totalJournalEntries,
            long totalFocusMinutes,
            long errorsLast24h,
            long warningsLast24h,
            long aiRequestsLast24h,
            Instant generatedAt
    ) {
    }

    public record AdminUserResponse(
            String id,
            String email,
            String fullName,
            String role,
            String status,
            boolean emailVerified,
            boolean onboardingCompleted,
            Instant createdAt,
            Instant lastLoginAt,
            long taskCount,
            long goalCount,
            long activeSessions
    ) {
    }

    public record UpdateUserStatusRequest(@NotBlank String status) {
    }

    public record UpdateUserRoleRequest(@NotBlank String role) {
    }

    public record AuditLogResponse(
            String id,
            String actorUserId,
            String actorEmail,
            String action,
            String entityType,
            String entityId,
            String details,
            String ipAddress,
            Instant createdAt
    ) {
    }

    public record ErrorLogResponse(
            String id,
            String severity,
            String message,
            String path,
            String method,
            String userId,
            String exceptionClass,
            Instant createdAt
    ) {
    }

    public record SettingRequest(
            @NotBlank @Size(max = 120) String key,
            @Size(max = 10000) String value,
            @Size(max = 16) String valueType,
            @Size(max = 400) String description
    ) {
    }

    public record SettingResponse(String key, String value, String valueType, String description,
                                  Instant updatedAt, String updatedBy) {
    }

    public record CategoryRequest(
            @NotBlank @Size(max = 48) String name,
            @Size(max = 48) String categoryType,
            @Size(max = 16) String color,
            String parentCategory
    ) {
    }

    public record CategoryResponse(String id, String name, String categoryType, String color, String parentCategory) {
    }

    /**
     * Privacy-preserving usage metadata. Admins can see that a user journals or uploads
     * documents, never the content itself.
     */
    public record UserPrivacyMetadata(
            String userId,
            String email,
            long journalEntryCount,
            long knowledgeDocumentCount,
            long knowledgeChunkCount,
            Instant lastJournalEntryAt,
            Instant lastDocumentUploadAt,
            String policy
    ) {
    }

    public record AiConfigurationResponse(
            String configuredProvider,
            boolean geminiConfigured,
            boolean openAiConfigured,
            boolean ollamaConfigured,
            boolean embeddingFallbackActive,
            int vectorDimensions,
            Map<String, String> models,
            List<String> notes
    ) {
    }

    public record SearchFilter(
            @Size(max = 190) String query,
            @Min(1) @Max(100) Integer size,
            @Min(0) Integer page,
            List<String> roles
    ) {
    }

    public record CountResponse(long value) {
        public static CountResponse of(long value) {
            return new CountResponse(value);
        }
    }
}