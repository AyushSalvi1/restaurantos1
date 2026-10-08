package com.lifeos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "skills")
@Getter
@Setter
public class Skill extends BaseEntity {

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "name", length = 120, nullable = false)
    private String name;

    @Column(name = "category", length = 48)
    private String category;

    /** Self-assessed proficiency on a 1-5 scale. */
    @Column(name = "proficiency", nullable = false)
    private int proficiency = 1;
}