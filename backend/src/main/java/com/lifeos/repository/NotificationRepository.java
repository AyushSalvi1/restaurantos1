package com.lifeos.repository;

import com.lifeos.entity.Notification;
import com.lifeos.entity.enums.NotificationCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, String> {

    Optional<Notification> findByIdAndUserId(String id, String userId);

    Optional<Notification> findByUserIdAndDedupeKey(String userId, String dedupeKey);

    Page<Notification> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadAtIsNullOrderByCreatedAtDesc(String userId, Pageable pageable);

    Page<Notification> findByUserIdAndCategoryOrderByCreatedAtDesc(String userId,
                                                                    NotificationCategory category,
                                                                    Pageable pageable);

    long countByUserIdAndReadAtIsNull(String userId);

    @Query("select count(n) from Notification n where n.userId = :userId and n.createdAt >= :since")
    long countCreatedSince(@Param("userId") String userId, @Param("since") Instant since);

    @Query("select count(n) from Notification n where n.userId = :userId and n.category = :category and n.createdAt >= :since")
    long countCategorySince(@Param("userId") String userId,
                            @Param("category") NotificationCategory category,
                            @Param("since") Instant since);

    @Query("select n from Notification n where n.scheduledFor is not null and n.scheduledFor <= :now and n.readAt is null order by n.scheduledFor asc")
    java.util.List<Notification> findDueReminders(@Param("now") Instant now);

    @Modifying
    @Query("delete from Notification n where n.readAt is not null and n.readAt < :before")
    int deleteReadBefore(@Param("before") Instant before);
}