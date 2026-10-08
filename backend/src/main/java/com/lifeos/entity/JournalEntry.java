package com.lifeos.entity;

import com.lifeos.entity.enums.JournalMood;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Private, user-owned reflection log. Content is never returned by any
 * administrative endpoint; only non-content metadata is visible to admins.
 */
@Entity
@Table(name = "journal_entries")
@Getter
@Setter
public class JournalEntry extends BaseEntity {

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "mood", length = 24)
    private JournalMood mood;

    /** Optional 1-5 self-reported rating used for trends only. */
    @Column(name = "mood_score")
    private Integer moodScore;

    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;

    @Column(name = "ai_analyzed", nullable = false)
    private boolean aiAnalyzed;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "word_count", nullable = false)
    private int wordCount;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}