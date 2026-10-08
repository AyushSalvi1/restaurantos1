package com.lifeos.entity;

import com.lifeos.entity.enums.ResourceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "learning_resources")
@Getter
@Setter
public class LearningResource extends BaseEntity {

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "learning_goal_id", length = 36)
    private String learningGoalId;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "url", length = 500)
    private String url;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", length = 32, nullable = false)
    private ResourceType resourceType = ResourceType.ARTICLE;

    @Column(name = "completed", nullable = false)
    private boolean completed;
}