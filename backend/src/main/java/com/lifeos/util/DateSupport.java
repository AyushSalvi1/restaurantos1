package com.lifeos.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/** Date arithmetic expressed in the user's own timezone, then persisted as UTC instants. */
public final class DateSupport {

    private DateSupport() {
    }

    public static Instant startOfDay(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toInstant();
    }

    public static Instant endOfDay(LocalDate date, ZoneId zone) {
        return date.plusDays(1).atStartOfDay(zone).toInstant();
    }

    public static LocalDate toLocalDate(Instant instant, ZoneId zone) {
        return instant.atZone(zone).toLocalDate();
    }

    public static ZonedDateTime toZoned(Instant instant, ZoneId zone) {
        return instant == null ? null : instant.atZone(zone);
    }

    public static Instant toInstant(LocalDateTimeHolder holder, ZoneId zone) {
        return holder == null ? null : holder.at(zone);
    }

    public static List<LocalDate> daysBetween(LocalDate from, LocalDate to) {
        List<LocalDate> days = new ArrayList<>();
        if (from == null || to == null || to.isBefore(from)) {
            return days;
        }
        for (LocalDate cursor = from; !cursor.isAfter(to); cursor = cursor.plusDays(1)) {
            days.add(cursor);
        }
        return days;
    }

    /** Minimal holder so the planner can express a wall-clock time without leaking zone maths. */
    public record LocalDateTimeHolder(java.time.LocalDate date, java.time.LocalTime time) {
        public Instant at(ZoneId zone) {
            return java.time.LocalDateTime.of(date, time).atZone(zone).toInstant();
        }
    }
}