package com.lifeos.repository;

import com.lifeos.entity.JournalEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, String> {

    Optional<JournalEntry> findByIdAndUserIdAndDeletedAtIsNull(String id, String userId);

    Page<JournalEntry> findByUserIdAndDeletedAtIsNullOrderByEntryDateDesc(String userId, Pageable pageable);

    List<JournalEntry> findByUserIdAndDeletedAtIsNullOrderByEntryDateDesc(String userId);

    List<JournalEntry> findByUserIdAndDeletedAtIsNullAndEntryDateBetweenOrderByEntryDateDesc(String userId,
                                                                                            LocalDate from,
                                                                                            LocalDate to);

    Optional<JournalEntry> findByUserIdAndEntryDateAndDeletedAtIsNull(String userId, LocalDate entryDate);

    @Query("""
            select j from JournalEntry j
            where j.userId = :userId and j.deletedAt is null
              and (lower(coalesce(j.title, '')) like lower(concat('%', :query, '%'))
                   or lower(j.content) like lower(concat('%', :query, '%'))
                   or lower(coalesce(j.tags, '')) like lower(concat('%', :query, '%')))
            order by j.entryDate desc
            """)
    Page<JournalEntry> search(@Param("userId") String userId, @Param("query") String query, Pageable pageable);

    long countByUserIdAndDeletedAtIsNull(String userId);

    long countByUserIdAndDeletedAtIsNullAndEntryDateBetween(String userId, LocalDate from, LocalDate to);

    @Query("select count(distinct j.entryDate) from JournalEntry j where j.userId = :userId and j.deletedAt is null and j.entryDate between :from and :to")
    long countDaysWithEntries(@Param("userId") String userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select coalesce(sum(j.wordCount), 0) from JournalEntry j where j.userId = :userId and j.deletedAt is null and j.entryDate between :from and :to")
    long sumWordsBetween(@Param("userId") String userId, @Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Privacy-safe metadata view used by the AI reflection feature (own entries only). */
    @Query("""
            select j from JournalEntry j
            where j.userId = :userId and j.deletedAt is null and j.entryDate between :from and :to
            order by j.entryDate asc
            """)
    List<JournalEntry> findOwnRange(@Param("userId") String userId,
                                    @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);

    @Query("select count(j) from JournalEntry j where j.userId = :userId and j.deletedAt is null and j.createdAt >= :since")
    long countCreatedSince(@Param("userId") String userId, @Param("since") Instant since);
}