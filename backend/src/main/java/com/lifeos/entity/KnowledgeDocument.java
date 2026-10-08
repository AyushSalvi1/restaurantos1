package com.lifeos.entity;

import com.lifeos.entity.enums.KnowledgeStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "knowledge_documents")
@Getter
@Setter
public class KnowledgeDocument extends BaseEntity {

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "filename", length = 255, nullable = false)
    private String filename;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "extension", length = 16, nullable = false)
    private String extension;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_path", length = 500)
    private String storagePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 24, nullable = false)
    private KnowledgeStatus status = KnowledgeStatus.PENDING;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "word_count", nullable = false)
    private int wordCount;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}