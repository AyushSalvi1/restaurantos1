package com.lifeos.repository;

import com.lifeos.entity.FocusSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface FocusSessionRepository extends JpaRepository<FocusSession, String> {

    Optional<FocusSession> findByIdAndUserId(String id, String userId);

    List<FocusSession> findByUserIdOrderByStartedAtDesc(String userId);

    @Query("""
            select f from FocusSession f
            where f.userId = :userId and f.startedAt >= :from and f.startedAt < :to
            order by f.startedAt asc
            """)
    List<FocusSession> findBetween(@Param("userId") String userId,
                                   @Param("from") Instant from,
                                   @Param("to") Instant to);

    @Query("select coalesce(sum(f.actualMinutes), 0) from FocusSession f where f.userId = :userId and f.completed = true and f.startedAt >= :from and f.startedAt < :to")
    long sumMinutesBetween(@Param("userId") String userId,
                           @Param("from") Instant from,
                           @Param("to") Instant to);

    @Query("select coalesce(sum(f.actualMinutes), 0) from FocusSession f where f.userId = :userId and f.completed = true and f.startedAt >= :from")
    long sumMinutesSince(@Param("userId") String userId, @Param("from") Instant from);

    @Query("select coalesce(sum(f.actualMinutes), 0) from FocusSession f where f.completed = true")
    long sumAllCompletedMinutes();

    long countByUserIdAndCompletedTrueAndStartedAtBetween(String userId, Instant from, Instant to);

    @Query("""
            select f from FocusSession f
            where f.userId = :userId and f.taskId = :taskId and f.startedAt >= :from
            order by f.startedAt desc
            """)
    List<FocusSession> findForTaskSince(@Param("userId") String userId,
                                        @Param("taskId") String taskId,
                                        @Param("from") Instant from);

    @Query("""
            select count(f) from FocusSession f
            where f.userId = :userId and f.completed = true and f.startedAt between :from and :to
            """)
    long countCompletedBetween(@Param("userId") String userId,
                               @Param("from") Instant from,
                               @Param("to") Instant to);
}