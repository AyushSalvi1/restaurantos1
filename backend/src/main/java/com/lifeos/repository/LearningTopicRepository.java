package com.lifeos.repository;

import com.lifeos.entity.LearningTopic;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearningTopicRepository extends JpaRepository<LearningTopic, String> {

    List<LearningTopic> findByLearningGoalIdOrderByPositionAsc(String learningGoalId);

    List<LearningTopic> findByUserIdAndLearningGoalIdOrderByPositionAsc(String userId, String learningGoalId);

    Optional<LearningTopic> findByIdAndUserId(String id, String userId);

    long countByLearningGoalIdAndCompletedTrue(String learningGoalId);

    long countByLearningGoalId(String learningGoalId);

    List<LearningTopic> findByUserIdAndCompletedFalseOrderByPositionAsc(String userId);

    void deleteByLearningGoalId(String learningGoalId);
}