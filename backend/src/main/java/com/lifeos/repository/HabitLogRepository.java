package com.lifeos.repository;

import com.lifeos.entity.HabitLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface HabitLogRepository extends JpaRepository<HabitLog, String> {

    Optional<HabitLog> findByHabitIdAndLogDate(String habitId, LocalDate logDate);

    List<HabitLog> findByUserIdAndLogDateBetween(String userId, LocalDate from, LocalDate to);

    List<HabitLog> findByUserIdAndHabitIdAndLogDateBetween(String userId, String habitId, LocalDate from, LocalDate to);

    List<HabitLog> findByUserIdAndHabitIdInAndLogDateBetween(String userId, Collection<String> habitIds, LocalDate from, LocalDate to);

    @Query("select count(h) from HabitLog h where h.userId = :userId and h.completed = true and h.logDate between :from and :to")
    long countCompletedBetween(@Param("userId") String userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select count(h) from HabitLog h where h.userId = :userId and h.logDate between :from and :to")
    long countLogsBetween(@Param("userId") String userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    long countByUserIdAndHabitIdAndCompletedTrueAndLogDateBetween(String userId, String habitId, LocalDate from, LocalDate to);

    void deleteByHabitId(String habitId);
}