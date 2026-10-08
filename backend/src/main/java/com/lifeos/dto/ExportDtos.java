package com.lifeos.dto;

import java.time.Instant;

/** Response returned by the data-export and account-deletion endpoints. */
public final class ExportDtos {

    private ExportDtos() {
    }

    public record ExportResponse(
            String exportId,
            String fileName,
            long sizeBytes,
            Instant generatedAt,
            String status,
            String message
    ) {
    }

    public record DeletionResponse(
            boolean deleted,
            Instant scheduledFor,
            String message
    ) {
    }
}