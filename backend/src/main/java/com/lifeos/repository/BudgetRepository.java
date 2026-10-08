package com.lifeos.repository;

import com.lifeos.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, String> {

    Optional<Budget> findByIdAndUserId(String id, String userId);

    @Query("""
            select b from Budget b
            where b.userId = :userId and b.startDate <= :date and b.endDate >= :date
            order by b.category asc
            """)
    List<Budget> findActiveOn(@Param("userId") String userId, @Param("date") LocalDate date);

    List<Budget> findByUserIdOrderByStartDateDesc(String userId);

    void deleteByUserId(String userId);
}