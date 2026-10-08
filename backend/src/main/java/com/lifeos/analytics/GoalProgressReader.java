package com.lifeos.analytics;

import com.lifeos.dto.AnalyticsDtos;
import com.lifeos.entity.Goal;
import com.lifeos.entity.enums.GoalStatus;
import com.lifeos.repository.GoalMilestoneRepository;
import com.lifeos.repository.GoalRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Goal dimension: mean recorded progress of goals that have not been archived or abandoned. */
@Component
public class GoalProgressReader {

    private final GoalRepository goalRepository;
    private final GoalMilestoneRepository milestoneRepository;

    public GoalProgressReader(GoalRepository goalRepository, GoalMilestoneRepository milestoneRepository) {
        this.goalRepository = goalRepository;
        this.milestoneRepository = milestoneRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.BalanceDimension dimension(String userId) {
        List<Goal> goals = goalRepository.findAllForUser(userId).stream()
                .filter(goal -> goal.getDeletedAt() == null)
                .filter(goal -> goal.getStatus() == GoalStatus.ACTIVE || goal.getStatus() == GoalStatus.PAUSED)
                .toList();
        if (goals.isEmpty()) {
            return BalanceScoreService.insufficient("goals", "Goals",
                    "No active goals, so goal progress cannot be scored.");
        }
        double mean = goals.stream().mapToInt(Goal::getProgress).average().orElse(0);
        int onTrack = (int) goals.stream().filter(goal -> goal.getProgress() >= 50).count();
        long milestonesDone = goals.stream()
                .mapToLong(goal -> milestoneRepository.countByGoalIdAndCompletedTrue(goal.getId()))
                .sum();
        long milestonesTotal = goals.stream()
                .mapToLong(goal -> milestoneRepository.countByGoalId(goal.getId()))
                .sum();
        return new AnalyticsDtos.BalanceDimension("goals", "Goals",
                (int) Math.round(mean), 100,
                "Mean of the progress you recorded on active goals.",
                List.of("Active goals: " + goals.size(),
                        "Goals at 50% or more: " + onTrack,
                        "Milestones completed: " + milestonesDone + "/" + milestonesTotal,
                        "Mean progress: " + BalanceScoreService.round(mean) + "%"),
                true);
    }
}