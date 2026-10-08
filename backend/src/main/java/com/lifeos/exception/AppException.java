package com.lifeos.exception;

import com.lifeos.common.ApiError;
import org.springframework.http.HttpStatus;

import java.util.List;

/** Base class for all deliberate, client-facing failures. */
public class AppException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<ApiError.FieldViolation> fieldErrors;

    public AppException(HttpStatus status, String code, String message) {
        this(status, code, message, List.of());
    }

    public AppException(HttpStatus status, String code, String message, List<ApiError.FieldViolation> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors == null ? List.of() : fieldErrors;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<ApiError.FieldViolation> getFieldErrors() {
        return fieldErrors;
    }

    public static AppException notFound(String message) {
        return new AppException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static AppException conflict(String message) {
        return new AppException(HttpStatus.CONFLICT, "CONFLICT", message);
    }

    public static AppException badRequest(String message) {
        return new AppException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", message);
    }

    public static AppException forbidden(String message) {
        return new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static AppException unauthorized(String message) {
        return new AppException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    public static AppException unprocessable(String message) {
        return new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "UNPROCESSABLE_ENTITY", message);
    }
}