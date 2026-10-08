package com.lifeos.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Knowledge base payloads: documents, semantic search results and grounded answers. */
public final class KnowledgeDtos {

    private KnowledgeDtos() {
    }

    public record DocumentResponse(
            String id,
            String title,
            String filename,
            String contentType,
            String extension,
            long sizeBytes,
            String status,
            String failureReason,
            int chunkCount,
            int wordCount,
            Instant createdAt
    ) {
    }

    public record DocumentListResponse(
            List<DocumentResponse> documents,
            long totalDocuments,
            long totalChunks,
            long totalBytes
    ) {
    }

    public record SavedItemRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 700) String url,
            @Size(max = 100000) String content,
            List<String> tags,
            com.lifeos.entity.enums.SavedItemType itemType
    ) {
    }

    public record SavedItemResponse(
            String id,
            String title,
            String url,
            String content,
            List<String> tags,
            String itemType,
            Instant createdAt
    ) {
    }

    public record SearchHit(
            String chunkId,
            String documentId,
            String documentTitle,
            String filename,
            int chunkIndex,
            String snippet,
            double score
    ) {
    }

    public record SearchResponse(
            String query,
            List<SearchHit> hits,
            int totalHits,
            boolean grounded,
            String message
    ) {
    }

    public record AskRequest(
            @NotBlank @Size(max = 2000) String question,
            @Min(1) @Max(20) Integer topK,
            String conversationId
    ) {
    }

    public record AskResponse(
            String answer,
            List<SearchHit> citations,
            boolean grounded,
            String provider,
            String model,
            String conversationId,
            String messageId
    ) {
    }
}