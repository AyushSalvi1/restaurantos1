package com.lifeos.repository;

import com.lifeos.entity.CalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, String> {

    Optional<CalendarEvent> findByIdAndUserId(String id, String userId);

    /**
     * Events overlapping {@code [from, to)}. Recurrence rules are expanded by the service layer
     * before this query runs, so this stays a single index range scan.
     */
    @Query("""
            select e from CalendarEvent e
            where e.userId = :userId and e.startAt < :to and e.endAt > :from
            order by e.startAt asc
            """)
    List<CalendarEvent> findOverlapping(@Param("userId") String userId,
                                        @Param("from") Instant from,
                                        @Param("to") Instant to);

    @Query("""
            select e from CalendarEvent e
            where e.userId = :userId and e.startAt >= :from and e.startAt < :to
            order by e.startAt asc
            """)
    List<CalendarEvent> findStartingBetween(@Param("userId") String userId,
                                            @Param("from") Instant from,
                                            @Param("to") Instant to);

    List<CalendarEvent> findByUserIdAndStartAtBetweenOrderByStartAtAsc(String userId, Instant from, Instant to);

    @Query("select count(e) from CalendarEvent e where e.userId = :userId and e.startAt between :from and :to")
    long countBetween(@Param("userId") String userId, @Param("from") Instant from, @Param("to") Instant to);

    List<CalendarEvent> findByUserIdAndTaskId(String userId, String taskId);

    List<CalendarEvent> findByUserIdAndExternalSourceAndExternalId(String userId, String source, String externalId);
}