package com.lifeos.repository;

import com.lifeos.entity.LearningSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LearningSessionRepository extends JpaRepository<LearningSession, String> {

    List<LearningSession> findByUserIdOrderByStartedAtDesc(String userId);

    @Query("select s from LearningSession s where s.userId = :userId and s.startedAt >= :from and s.startedAt < :to order by s.startedAt asc")
    List<LearningSession> findBetween(@Param("userId") String userId,
                                      @Param("from") Instant from,
                                      @Param("to") Instant to);

    @Query("select coalesce(sum(s.minutes), 0) from LearningSession s where s.userId = :userId and s.startedAt between :from and :to")
    long sumMinutesBetween(@Param("userId") String userId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select coalesce(sum(s.minutes), 0) from LearningSession s where s.userId = :userId and s.learningGoalId = :goalId")
    long sumMinutesForGoal(@Param("userId") String userId, @Param("goalId") String goalId);

    @Query("select coalesce(avg(s.quizScore), 0) from LearningSession s where s.userId = :userId and s.quizScore is not null and s.startedAt >= :from")
    double avgQuizScoreSince(@Param("userId") String userId, @Param("from") Instant from);

    Page<LearningSession> findByUserIdOrderByStartedAtDesc(String userId, Pageable pageable);
}