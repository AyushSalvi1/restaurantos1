package com.lifeos.repository;

import com.lifeos.entity.KnowledgeDocument;
import com.lifeos.entity.enums.KnowledgeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, String> {

    Optional<KnowledgeDocument> findByIdAndUserIdAndDeletedAtIsNull(String id, String userId);

    Page<KnowledgeDocument> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(String userId, Pageable pageable);

    @Query("""
            select d from KnowledgeDocument d
            where d.userId = :userId and d.deletedAt is null
              and (lower(d.title) like lower(concat('%', :query, '%')) or lower(d.filename) like lower(concat('%', :query, '%')))
            order by d.createdAt desc
            """)
    Page<KnowledgeDocument> search(@Param("userId") String userId, @Param("query") String query, Pageable pageable);

    List<KnowledgeDocument> findByUserIdAndDeletedAtIsNullAndStatus(String userId, KnowledgeStatus status);

    List<KnowledgeDocument> findByUserIdAndDeletedAtIsNull(String userId);

    long countByUserIdAndDeletedAtIsNull(String userId);

    @Query("select coalesce(sum(d.chunkCount), 0) from KnowledgeDocument d where d.userId = :userId and d.deletedAt is null")
    long totalChunks(@Param("userId") String userId);
}