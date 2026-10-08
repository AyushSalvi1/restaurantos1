package com.lifeos.repository;

import com.lifeos.entity.Habit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface HabitRepository extends JpaRepository<Habit, String> {

    Optional<Habit> findByIdAndUserId(String id, String userId);

    @Query("select h from Habit h where h.userId = :userId and h.archived = false order by h.createdAt asc")
    List<Habit> findActiveForUser(@Param("userId") String userId);

    List<Habit> findByUserIdOrderByCreatedAtAsc(String userId);

    List<Habit> findByUserIdAndArchivedFalse(String userId);

    long countByUserIdAndArchivedFalse(String userId);
}