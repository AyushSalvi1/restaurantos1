package com.lifeos.repository;

import com.lifeos.entity.ProductivityMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ProductivityMetricRepository extends JpaRepository<ProductivityMetric, String> {

    Optional<ProductivityMetric> findByUserIdAndMetricDate(String userId, LocalDate metricDate);

    List<ProductivityMetric> findByUserIdAndMetricDateBetweenOrderByMetricDateAsc(String userId, LocalDate from, LocalDate to);

    @Query("""
            select coalesce(sum(m.focusMinutes), 0) from ProductivityMetric m
            where m.userId = :userId and m.metricDate between :from and :to
            """)
    long sumFocusMinutes(@Param("userId") String userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select coalesce(sum(m.studyMinutes), 0) from ProductivityMetric m
            where m.userId = :userId and m.metricDate between :from and :to
            """)
    long sumStudyMinutes(@Param("userId") String userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select coalesce(avg(m.productivityScore), 0) from ProductivityMetric m
            where m.userId = :userId and m.metricDate between :from and :to
            """)
    double avgProductivityScore(@Param("userId") String userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select count(m) from ProductivityMetric m where m.userId = :userId and m.metricDate between :from and :to and (m.tasksCompleted > 0 or m.focusMinutes > 0 or m.habitsCompleted > 0 or m.studyMinutes > 0)")
    long countActiveDays(@Param("userId") String userId, @Param("from") LocalDate from, @Param("to") LocalDate to);
}