package com.lifeos.common;

import java.time.Instant;
import java.util.List;

/**
 * Error payload returned for every non-2xx response.
 * Stack traces and internal exception messages are never included.
 */
public record ApiError(
        String code,
        String message,
        List<FieldViolation> fieldErrors,
        String path,
        String requestId,
        Instant timestamp
) {
    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(String code, String message, String path, String requestId) {
        return new ApiError(code, message, List.of(), path, requestId, Instant.now());
    }
}