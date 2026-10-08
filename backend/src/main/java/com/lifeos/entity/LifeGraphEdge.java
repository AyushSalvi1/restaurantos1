package com.lifeos.entity;

import com.lifeos.entity.enums.GraphNodeType;
import com.lifeos.entity.enums.GraphRelation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Weighted, directed relationship between two entities owned by the same user. */
@Entity
@Table(name = "life_graph_edges")
@Getter
@Setter
public class LifeGraphEdge extends CreatedEntity {

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 32, nullable = false)
    private GraphNodeType sourceType;

    @Column(name = "source_id", length = 36, nullable = false)
    private String sourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", length = 32, nullable = false)
    private GraphNodeType targetType;

    @Column(name = "target_id", length = 36, nullable = false)
    private String targetId;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation", length = 32, nullable = false)
    private GraphRelation relation;

    @Column(name = "weight", nullable = false)
    private double weight = 1.0d;
}