package com.lifeos.exception;

import com.lifeos.entity.ErrorLog;
import com.lifeos.repository.ErrorLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Persists unexpected failures so administrators can review them.
 * Runs in its own transaction so a rollback of the business transaction never loses the report.
 */
@Service
public class ErrorLogService {

    private static final Logger log = LoggerFactory.getLogger(ErrorLogService.class);
    private static final int MAX_TRACE_LENGTH = 8000;

    private final ErrorLogRepository repository;

    public ErrorLogService(ErrorLogRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Exception ex, HttpServletRequest request, int status) {
        try {
            ErrorLog entry = new ErrorLog();
            entry.setSeverity(status >= 500 ? "ERROR" : "WARN");
            entry.setMessage(truncate(ex.getMessage(), 1000));
            entry.setPath(request == null ? null : truncate(request.getRequestURI(), 300));
            entry.setMethod(request == null ? null : request.getMethod());
            entry.setUserId((String) request.getAttribute(RequestIdFilter.AUTHENTICATED_USER_ATTRIBUTE));
            entry.setExceptionClass(ex.getClass().getName());
            entry.setStackTrace(truncate(stackTrace(ex), 8000));
            repository.save(entry);
        } catch (Exception recordingFailure) {
            log.warn("Unable to persist error log: {}", recordingFailure.getMessage());
        }
    }

    private String stackTrace(Exception ex) {
        StringWriter writer = new StringWriter();
        ex.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}