package com.lifeos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Retrieval unit of the knowledge base. The embedding is stored as a JSON array of floats. */
@Entity
@Table(name = "knowledge_chunks")
@Getter
@Setter
public class KnowledgeChunk extends CreatedEntity {

    @Column(name = "document_id", length = 36, nullable = false)
    private String documentId;

    @Column(name = "user_id", length = 36, nullable = false)
    private String userId;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "token_estimate", nullable = false)
    private int tokenEstimate;

    @Column(name = "embedding", columnDefinition = "TEXT")
    private String embedding;

    @Column(name = "embedding_model", length = 64)
    private String embeddingModel;
}