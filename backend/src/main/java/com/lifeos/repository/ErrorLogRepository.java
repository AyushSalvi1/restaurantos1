package com.lifeos.repository;

import com.lifeos.entity.ErrorLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;

public interface ErrorLogRepository extends JpaRepository<ErrorLog, String> {
    Page<ErrorLog> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByCreatedAtAfter(Instant instant);

    long countBySeverityAndCreatedAtAfter(String severity, Instant instant);
}