package com.lifeos.repository;

import com.lifeos.entity.GoalMilestone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GoalMilestoneRepository extends JpaRepository<GoalMilestone, String> {

    List<GoalMilestone> findByGoalIdOrderByPositionAsc(String goalId);

    List<GoalMilestone> findByGoalIdAndUserIdOrderByPositionAsc(String goalId, String userId);

    Optional<GoalMilestone> findByIdAndUserId(String id, String userId);

    long countByGoalIdAndCompletedFalse(String goalId);

    long countByGoalIdAndCompletedTrue(String goalId);

    long countByGoalId(String goalId);

    void deleteByGoalId(String goalId);
}