package com.lifeos.repository;

import com.lifeos.entity.SavingsGoal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, String> {

    Optional<SavingsGoal> findByIdAndUserId(String id, String userId);

    List<SavingsGoal> findByUserIdOrderByCreatedAtDesc(String userId);
}