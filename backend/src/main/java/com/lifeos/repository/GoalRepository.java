package com.lifeos.repository;

import com.lifeos.entity.Goal;
import com.lifeos.entity.enums.GoalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<Goal, String> {

    Optional<Goal> findByIdAndUserIdAndDeletedAtIsNull(String id, String userId);

    @Query("""
            select g from Goal g
            where g.userId = :userId and g.deletedAt is null
            order by g.position asc, g.createdAt desc
            """)
    List<Goal> findAllForUser(@Param("userId") String userId);

    @Query("""
            select g from Goal g
            where g.userId = :userId and g.deletedAt is null and g.status = :status
            order by g.position asc, g.createdAt desc
            """)
    List<Goal> findByStatus(@Param("userId") String userId, @Param("status") GoalStatus status);

    @Query("select g from Goal g where g.userId = :userId and g.deletedAt is null and g.targetDate is not null and g.targetDate between :from and :to order by g.targetDate asc")
    List<Goal> findWithTargetDateBetween(@Param("userId") String userId,
                                         @Param("from") Instant from,
                                         @Param("to") Instant to);

    @Query("""
            select g from Goal g
            where g.userId = :userId and g.deletedAt is null
              and (:status is null or g.status = :status)
              and (:query is null or lower(g.title) like lower(concat('%', :query, '%'))
                   or lower(coalesce(g.description, '')) like lower(concat('%', :query, '%')))
            """)
    Page<Goal> search(@Param("userId") String userId,
                      @Param("query") String query,
                      @Param("status") GoalStatus status,
                      Pageable pageable);

    long countByUserIdAndDeletedAtIsNullAndStatus(String userId, GoalStatus status);

    long countByUserIdAndDeletedAtIsNull(String userId);

    @Query("select count(g) from Goal g where g.userId = :userId and g.deletedAt is null and g.parentGoalId = :goalId")
    long countSubGoals(@Param("userId") String userId, @Param("goalId") String goalId);
}