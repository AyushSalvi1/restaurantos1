package com.lifeos.repository;

import com.lifeos.entity.Task;
import com.lifeos.entity.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, String>, JpaSpecificationExecutor<Task> {

    Optional<Task> findByIdAndUserIdAndDeletedAtIsNull(String id, String userId);

    List<Task> findByUserIdAndStatusInAndDeletedAtIsNull(String userId, Collection<TaskStatus> statuses);

    @Query("""
            select t from Task t
            where t.userId = :userId and t.deletedAt is null and t.status in :statuses
              and t.deadline is not null and t.deadline between :from and :to
            order by t.deadline asc
            """)
    List<Task> findDueBetween(@Param("userId") String userId,
                              @Param("statuses") Collection<TaskStatus> statuses,
                              @Param("from") Instant from,
                              @Param("to") Instant to);

    @Query("""
            select t from Task t
            where t.userId = :userId and t.deletedAt is null and t.status in :statuses
              and (t.deadline is null or t.deadline < :now)
            order by t.deadline asc
            """)
    List<Task> findOverdue(@Param("userId") String userId,
                           @Param("statuses") Collection<TaskStatus> statuses,
                           @Param("now") Instant now);

    @Query("""
            select t from Task t
            where t.userId = :userId and t.deletedAt is null and t.completedAt is not null
              and t.completedAt between :from and :to
            """)
    List<Task> findCompletedBetween(@Param("userId") String userId,
                                    @Param("from") Instant from,
                                    @Param("to") Instant to);

    @Query("""
            select count(t) from Task t
            where t.userId = :userId and t.deletedAt is null
              and t.status in :statuses and t.completedAt is not null
              and t.completedAt between :from and :to
            """)
    long countCompletedBetween(@Param("userId") String userId,
                               @Param("statuses") Collection<TaskStatus> statuses,
                               @Param("from") Instant from,
                               @Param("to") Instant to);

    @Query("select coalesce(sum(t.actualMinutes), 0) from Task t where t.userId = :userId and t.completedAt between :from and :to")
    long sumActualMinutesBetween(@Param("userId") String userId,
                                 @Param("from") Instant from,
                                 @Param("to") Instant to);

    @Query("select coalesce(sum(t.estimatedMinutes), 0) from Task t where t.userId = :userId and t.deletedAt is null and t.status in :statuses")
    long sumEstimatedMinutes(@Param("userId") String userId,
                             @Param("statuses") Collection<TaskStatus> statuses);

    long countByUserIdAndStatusAndDeletedAtIsNull(String userId, TaskStatus status);

    long countByUserIdAndDeletedAtIsNull(String userId);

    @Query("""
            select t from Task t
            where t.userId = :userId and t.deletedAt is null
              and t.goalId in :goalIds
            order by t.position asc
            """)
    List<Task> findByGoalIds(@Param("userId") String userId, @Param("goalIds") Collection<String> goalIds);

    @Modifying
    @Query("update Task t set t.deletedAt = :deletedAt, t.status = 'CANCELLED' where t.id = :id and t.userId = :userId")
    int softDelete(@Param("id") String id, @Param("userId") String userId, @Param("deletedAt") Instant deletedAt);

    @Query("select t from Task t where t.userId = :userId and t.deletedAt is null and t.title like concat('%', :titlePart, '%')")
    List<Task> findByTitleContaining(@Param("userId") String userId, @Param("titlePart") String titlePart);

    List<Task> findByUserIdAndRecurrenceParentIdAndDeletedAtIsNull(String userId, String recurrenceParentId);
}