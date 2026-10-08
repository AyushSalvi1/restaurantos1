package com.lifeos.dto;

import com.lifeos.entity.enums.InsightSeverity;
import com.lifeos.entity.enums.InsightType;

import java.time.LocalDate;
import java.util.List;

/** Insight and notification payloads. */
public final class InsightDtos {

    private InsightDtos() {
    }

    public record InsightResponse(
            String id,
            InsightType insightType,
            String title,
            String body,
            InsightSeverity severity,
            int confidence,
            List<String> factors,
            LocalDate periodStart,
            LocalDate periodEnd,
            String source,
            boolean dismissed,
            java.time.Instant createdAt
    ) {
    }

    public record DismissRequest(boolean dismissed) {
    }

    public record InsightGenerationResponse(
            List<InsightResponse> created,
            int evaluated,
            List<String> skippedReasons
    ) {
    }

    public record NotificationResponse(
            String id,
            String category,
            String title,
            String body,
            String link,
            String priority,
            boolean read,
            java.time.Instant createdAt,
            java.time.Instant readAt
    ) {
    }

    public record NotificationPage(
            List<NotificationResponse> notifications,
            long unreadCount,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    public record MarkAllReadRequest(String category) {
    }
}