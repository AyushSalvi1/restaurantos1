package com.lifeos.entity;

import com.lifeos.entity.enums.NotificationCategory;
import com.lifeos.entity.enums.NotificationPriority;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "notifications")
@Getter
@Setter
public class Notification extends CreatedEntity {

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 24, nullable = false)
    private NotificationCategory category;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "body", length = 700)
    private String body;

    @Column(name = "link", length = 300)
    private String link;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 16, nullable = false)
    private NotificationPriority priority = NotificationPriority.NORMAL;

    @Column(name = "read_at")
    private Instant readAt;

    /** Guards against duplicate notifications for the same logical event. */
    @Column(name = "dedupe_key", length = 190)
    private String dedupeKey;

    @Column(name = "scheduled_for")
    private Instant scheduledFor;

    public boolean isRead() {
        return readAt != null;
    }
}