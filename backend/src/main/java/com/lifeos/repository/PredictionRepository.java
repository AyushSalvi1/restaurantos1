package com.lifeos.repository;

import com.lifeos.entity.Prediction;
import com.lifeos.entity.enums.PredictionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PredictionRepository extends JpaRepository<Prediction, String> {

    Optional<Prediction> findByIdAndUserId(String id, String userId);

    @Query("""
            select p from Prediction p
            where p.userId = :userId and p.expiresAt > :now
            order by p.probability desc
            """)
    List<Prediction> findActive(@Param("userId") String userId, @Param("now") Instant now);

    List<Prediction> findByUserIdAndPredictionTypeAndExpiresAtAfterOrderByProbabilityDesc(String userId,
                                                                                       PredictionType type,
                                                                                       Instant now);

    Optional<Prediction> findByUserIdAndPredictionTypeAndSubjectTypeAndSubjectId(String userId,
                                                                                 PredictionType type,
                                                                                 String subjectType,
                                                                                 String subjectId);

    void deleteByUserIdAndSubjectId(String userId, String subjectId);

    @Query("delete from Prediction p where p.userId = :userId and p.subjectId in :subjectIds")
    int deleteBySubjectIds(@Param("userId") String userId, @Param("subjectIds") List<String> subjectIds);
}