package com.lifeos.repository;

import com.lifeos.entity.LifeGraphEdge;
import com.lifeos.entity.enums.GraphNodeType;
import com.lifeos.entity.enums.GraphRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LifeGraphEdgeRepository extends JpaRepository<LifeGraphEdge, String> {

    Optional<LifeGraphEdge> findByUserIdAndSourceTypeAndSourceIdAndTargetTypeAndTargetIdAndRelation(
            String userId, GraphNodeType sourceType, String sourceId,
            GraphNodeType targetType, String targetId, GraphRelation relation);

    @Query("""
            select e from LifeGraphEdge e
            where e.userId = :userId
              and ((e.sourceType = :type and e.sourceId = :nodeId) or (e.targetType = :type and e.targetId = :nodeId))
            """)
    List<LifeGraphEdge> findAdjacent(@Param("userId") String userId,
                                     @Param("type") GraphNodeType type,
                                     @Param("nodeId") String nodeId);

    List<LifeGraphEdge> findByUserIdAndSourceTypeAndSourceId(String userId, GraphNodeType sourceType, String sourceId);

    void deleteBySourceTypeAndSourceId(GraphNodeType type, String sourceId);

    void deleteByTargetTypeAndTargetId(GraphNodeType type, String targetId);

    @Query("select e from LifeGraphEdge e where e.userId = :userId and e.sourceType in :types and e.sourceId in :ids")
    List<LifeGraphEdge> findBySources(@Param("userId") String userId,
                                      @Param("types") Collection<GraphNodeType> types,
                                      @Param("ids") Collection<String> ids);

    List<LifeGraphEdge> findByUserIdAndSourceTypeIn(String userId, Collection<GraphNodeType> types);

    long countByUserId(String userId);
}