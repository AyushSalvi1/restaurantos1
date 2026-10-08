package com.lifeos.repository;

import com.lifeos.entity.KnowledgeChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, String> {

    List<KnowledgeChunk> findByUserIdOrderByIdAsc(String userId);

    List<KnowledgeChunk> findByDocumentIdOrderByChunkIndexAsc(String documentId);

    void deleteByDocumentId(String documentId);

    long countByDocumentId(String documentId);

    @Query("select c from KnowledgeChunk c where c.userId = :userId and c.embedding is not null and c.documentId in :documentIds")
    List<KnowledgeChunk> findEmbedded(@Param("userId") String userId,
                                      @Param("documentIds") Collection<String> documentIds);
}