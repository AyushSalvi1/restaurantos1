package com.lifeos.repository;

import com.lifeos.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkillRepository extends JpaRepository<Skill, String> {
    Optional<Skill> findByIdAndUserId(String id, String userId);

    List<Skill> findByUserIdOrderByNameAsc(String userId);
}