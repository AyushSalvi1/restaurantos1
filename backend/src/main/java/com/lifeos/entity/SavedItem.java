package com.lifeos.entity;

import com.lifeos.entity.enums.SavedItemType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "saved_items")
@Getter
@Setter
public class SavedItem extends BaseEntity {

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", length = 24, nullable = false)
    private SavedItemType itemType = SavedItemType.NOTE;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "url", length = 700)
    private String url;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}