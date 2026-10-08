package com.lifeos.analytics;

import com.lifeos.dto.AnalyticsDtos;
import com.lifeos.entity.LearningGoal;
import com.lifeos.entity.LearningTopic;
import com.lifeos.repository.LearningGoalRepository;
import com.lifeos.repository.LearningTopicRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Learning dimension: topic completion across learning goals that are still in progress. */
@Component
public class LearningProgressReader {

    private final LearningGoalRepository learningGoalRepository;
    private final LearningTopicRepository learningTopicRepository;

    public LearningProgressReader(LearningGoalRepository learningGoalRepository,
                                  LearningTopicRepository learningTopicRepository) {
        this.learningGoalRepository = learningGoalRepository;
        this.learningTopicRepository = learningTopicRepository;
    }

    @Transactional(readOnly = true)
    public AnalyticsDtos.BalanceDimension dimension(String userId) {
        List<LearningGoal> goals = learningGoalRepository.findAllForUser(userId);
        if (goals.isEmpty()) {
            return BalanceScoreService.insufficient("learning", "Learning",
                    "No learning goals, so learning progress cannot be scored.");
        }
        long topicsTotal = 0;
        long topicsDone = 0;
        double progressSum = 0;
        int withTopics = 0;
        for (LearningGoal goal : goals) {
            List<LearningTopic> topics = learningTopicRepository.findByLearningGoalIdOrderByPositionAsc(goal.getId());
            progressSum += goal.getProgress();
            if (!topics.isEmpty()) {
                withTopics++;
                topicsTotal += topics.size();
                topicsDone += topics.stream().filter(LearningTopic::isCompleted).count();
            }
        }
        double progressMean = progressSum / goals.size();
        double score = topicsTotal > 0
                ? Math.round(topicsDone * 100.0 / topicsTotal * 0.6 + progressMean * 0.4)
                : Math.round(progressMean);
        List<String> inputs = List.of(
                "Learning goals: " + goals.size(),
                "Goals with a topic breakdown: " + withTopics,
                "Topics completed: " + topicsDone + "/" + topicsTotal,
                "Mean recorded progress: " + BalanceScoreService.round(progressMean) + "%",
                "Formula: 60% topic completion + 40% recorded goal progress");
        return new AnalyticsDtos.BalanceDimension("learning", "Learning",
                (int) Math.max(0, Math.min(100, score)), 100,
                "Topic completion weighted at 60% and recorded goal progress at 40%.",
                inputs,                 true);
    }
}