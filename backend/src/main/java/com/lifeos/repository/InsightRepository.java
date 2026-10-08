package com.lifeos.repository;

import com.lifeos.entity.Insight;
import com.lifeos.entity.enums.InsightType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InsightRepository extends JpaRepository<Insight, String> {

    Page<Insight> findByUserIdAndDismissedFalseOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<Insight> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<Insight> findByUserIdAndInsightTypeOrderByCreatedAtDesc(String userId, InsightType type, Pageable pageable);

    List<Insight> findTop5ByUserIdAndDismissedFalseOrderByCreatedAtDesc(String userId);

    List<Insight> findByUserIdAndInsightTypeAndCreatedAtAfterOrderByCreatedAtDesc(String userId, InsightType type, Instant after);

    Optional<Insight> findByIdAndUserId(String id, String userId);

    Optional<Insight> findByUserIdAndDedupeKey(String userId, String dedupeKey);

    long countByUserIdAndCreatedAtAfter(String userId, Instant after);
}