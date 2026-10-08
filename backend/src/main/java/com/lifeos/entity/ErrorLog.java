package com.lifeos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Structured record of unexpected server-side failures for the admin health panel. */
@Entity
@Table(name = "error_logs")
@Getter
@Setter
public class ErrorLog extends CreatedEntity {

    @Column(name = "severity", length = 16, nullable = false)
    private String severity = "ERROR";

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "path", length = 300)
    private String path;

    @Column(name = "method", length = 10)
    private String method;

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "exception_class", length = 255)
    private String exceptionClass;

    @Column(name = "stack_trace", columnDefinition = "TEXT")
    private String stackTrace;
}