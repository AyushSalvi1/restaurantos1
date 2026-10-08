package com.lifeos.repository;

import com.lifeos.entity.SavedItem;
import com.lifeos.entity.enums.SavedItemType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SavedItemRepository extends JpaRepository<SavedItem, String> {

    Optional<SavedItem> findByIdAndUserIdAndDeletedAtIsNull(String id, String userId);

    Page<SavedItem> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<SavedItem> findByUserIdAndItemTypeAndDeletedAtIsNullOrderByCreatedAtDesc(String userId,
                                                                                  SavedItemType type,
                                                                                  Pageable pageable);

    List<SavedItem> findByUserIdAndItemTypeAndDeletedAtIsNullOrderByCreatedAtDesc(String userId, SavedItemType type);

    @Query("""
            select s from SavedItem s
            where s.userId = :userId and s.deletedAt is null
              and (lower(s.title) like lower(concat('%', :query, '%'))
                   or lower(coalesce(s.content, '')) like lower(concat('%', :query, '%'))
                   or lower(coalesce(s.tags, '')) like lower(concat('%', :query, '%')))
            order by s.createdAt desc
            """)
    Page<SavedItem> search(@Param("userId") String userId, @Param("query") String query, Pageable pageable);

    long countByUserIdAndDeletedAtIsNull(String userId);
}