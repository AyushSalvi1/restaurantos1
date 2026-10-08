package com.lifeos.repository;

import com.lifeos.entity.LearningGoal;
import com.lifeos.entity.enums.LearningStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LearningGoalRepository extends JpaRepository<LearningGoal, String> {

    Optional<LearningGoal> findByIdAndUserIdAndDeletedAtIsNull(String id, String userId);

    @Query("select g from LearningGoal g where g.userId = :userId and g.deletedAt is null order by g.createdAt desc")
    List<LearningGoal> findAllForUser(@Param("userId") String userId);

    @Query("select g from LearningGoal g where g.userId = :userId and g.deletedAt is null and g.status = :status order by g.createdAt desc")
    List<LearningGoal> findByStatus(@Param("userId") String userId, @Param("status") LearningStatus status);

    Page<LearningGoal> findByUserIdAndDeletedAtIsNull(String userId, Pageable pageable);

    long countByUserIdAndDeletedAtIsNullAndStatus(String userId, LearningStatus status);

    @Query("select coalesce(sum(g.hoursSpent), 0) from LearningGoal g where g.userId = :userId and g.deletedAt is null")
    double sumHoursSpent(@Param("userId") String userId);
}