package com.lifeos.repository;

import com.lifeos.entity.LearningResource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LearningResourceRepository extends JpaRepository<LearningResource, String> {

    Optional<LearningResource> findByIdAndUserId(String id, String userId);

    List<LearningResource> findByUserIdOrderByCreatedAtDesc(String userId);

    List<LearningResource> findByUserIdAndLearningGoalIdOrderByCreatedAtDesc(String userId, String learningGoalId);

    void deleteByLearningGoalId(String learningGoalId);
}