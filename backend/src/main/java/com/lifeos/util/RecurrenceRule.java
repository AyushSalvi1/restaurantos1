package com.lifeos.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * A deliberately small recurrence dialect shared by tasks and calendar events:
 *
 * <pre>
 *   FREQ=DAILY;INTERVAL=1
 *   FREQ=WEEKLY;BYDAY=MO,WE,FR
 *   FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=15
 * </pre>
 *
 * Parsing is lenient about whitespace and case. Anything unrecognised yields {@link #none()},
 * which keeps a malformed rule from silently producing thousands of occurrences.
 */
public final class RecurrenceRule {

    private final String frequency;
    private final int interval;
    private final java.util.List<Integer> byDays;
    private final Integer byMonthDay;

    private RecurrenceRule(String frequency, int interval, java.util.List<Integer> byDays, Integer byMonthDay) {
        this.frequency = frequency;
        this.interval = Math.max(1, interval);
        this.byDays = byDays;
        this.byMonthDay = byMonthDay;
    }

    public static RecurrenceRule none() {
        return new RecurrenceRule(null, 1, java.util.List.of(), null);
    }

    public static boolean isRecurring(String rule) {
        return parse(rule).frequency != null;
    }

    public static RecurrenceRule parse(String rule) {
        if (rule == null || rule.isBlank()) {
            return none();
        }
        String frequency = null;
        int interval = 1;
        java.util.List<Integer> byDays = new java.util.ArrayList<>();
        Integer byMonthDay = null;

        for (String part : rule.split(";")) {
            String[] keyValue = part.split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            String key = keyValue[0].trim().toUpperCase(java.util.Locale.ROOT);
            String value = keyValue[1].trim();
            switch (key) {
                case "FREQ" -> frequency = value.toUpperCase(java.util.Locale.ROOT);
                case "INTERVAL" -> {
                    try {
                        interval = Integer.parseInt(value);
                    } catch (NumberFormatException ignored) {
                        interval = 1;
                    }
                }
                case "BYDAY" -> {
                    for (String day : value.split(",")) {
                        int iso = isoDayOfWeek(day.trim());
                        if (iso > 0) {
                            byDays.add(iso);
                        }
                    }
                }
                case "BYMONTHDAY" -> {
                    try {
                        byMonthDay = Integer.parseInt(value);
                    } catch (NumberFormatException ignored) {
                        byMonthDay = null;
                    }
                }
                default -> {
                    // Unknown parts are ignored rather than failing the whole rule.
                }
            }
        }

        if (frequency == null) {
            return none();
        }
        return new RecurrenceRule(frequency, interval, java.util.List.copyOf(byDays), byMonthDay);
    }

    public boolean isNone() {
        return frequency == null;
    }

    public String frequency() {
        return frequency;
    }

    /**
     * Expands occurrences inside {@code [windowStart, windowEnd)} capped at {@code limit} to protect
     * against pathological rules such as {@code FREQ=MINUTELY}.
     */
    public java.util.List<Instant> expand(Instant origin, Instant windowStart, Instant windowEnd,
                                         ZoneId zone, Instant limitEnd, int limit) {
        java.util.List<Instant> occurrences = new java.util.ArrayList<>();
        if (isNone() || origin == null || windowEnd == null || windowStart == null) {
            return occurrences;
        }
        if (windowEnd.isBefore(windowStart)) {
            return occurrences;
        }
        Instant hardEnd = limitEnd != null && limitEnd.isBefore(windowEnd) ? limitEnd : windowEnd;
        int guard = 0;

        switch (frequency) {
            case "DAILY" -> {
                long step = ChronoUnit.DAYS.getDuration().toMillis() * interval;
                for (long t = origin.toEpochMilli(); t <= hardEnd.toEpochMilli() && guard++ < limit; t += step) {
                    Instant candidate = Instant.ofEpochMilli(t);
                    if (!candidate.isBefore(windowStart) && candidate.isBefore(windowEnd)) {
                        occurrences.add(candidate);
                    }
                }
            }
            case "WEEKLY" -> {
                java.util.List<Integer> days = byDays.isEmpty()
                        ? java.util.List.of(origin.atZone(zone).getDayOfWeek().getValue())
                        : byDays;
                LocalDate startDate = origin.atZone(zone).toLocalDate();
                LocalDate endDate = hardEnd.atZone(zone).toLocalDate();
                LocalDate weekStart = startDate.minusDays(startDate.getDayOfWeek().getValue() - 1L);
                for (LocalDate date = weekStart; !date.isAfter(endDate); date = date.plusDays(7L * interval)) {
                    guard++;
                    if (guard > limit) {
                        break;
                    }
                    for (int iso : days) {
                        LocalDate occurrenceDate = date.plusDays(iso - 1L);
                        if (occurrenceDate.isBefore(startDate) || occurrenceDate.isAfter(endDate)) {
                            continue;
                        }
                        Instant candidate = occurrenceDate.atTime(origin.atZone(zone).toLocalTime())
                                .atZone(zone).toInstant();
                        if (candidate.isAfter(origin) && !candidate.isBefore(windowStart) && candidate.isBefore(windowEnd)) {
                            occurrences.add(candidate);
                        }
                    }
                }
            }
            case "MONTHLY" -> {
                LocalDate startDate = origin.atZone(zone).toLocalDate();
                LocalDate endDate = hardEnd.atZone(zone).toLocalDate();
                int requestedDay = byMonthDay != null ? byMonthDay : startDate.getDayOfMonth();
                LocalDate cursor = startDate.withDayOfMonth(1);
                while (!cursor.isAfter(endDate) && guard++ < limit) {
                    LocalDate target = cursor.withDayOfMonth(Math.min(requestedDay, cursor.lengthOfMonth()));
                    if (!target.isBefore(startDate)) {
                        Instant candidate = target.atTime(origin.atZone(zone).toLocalTime()).atZone(zone).toInstant();
                        if (!candidate.isBefore(windowStart) && candidate.isBefore(windowEnd)) {
                            occurrences.add(candidate);
                        }
                    }
                    cursor = cursor.plusMonths(interval);
                }
            }
            default -> {
                return occurrences;
            }
        }
        return occurrences;
    }

    /** Compact weekly form used when generating habit reminders. */
    public static String weekly(java.util.List<Integer> isoDays) {
        if (isoDays == null || isoDays.isEmpty()) {
            return null;
        }
        String days = isoDays.stream()
                .sorted()
                .map(RecurrenceRule::abbreviation)
                .collect(java.util.stream.Collectors.joining(","));
        return "FREQ=WEEKLY;BYDAY=" + days;
    }

    public static String daily(int interval) {
        return "FREQ=DAILY;INTERVAL=" + Math.max(1, interval);
    }

    public static String monthly(int dayOfMonth) {
        return "FREQ=MONTHLY;INTERVAL=1;BYMONTHDAY=" + Math.max(1, Math.min(28, dayOfMonth));
    }

    private static String abbreviation(int isoDay) {
        return switch (isoDay) {
            case 1 -> "MO";
            case 2 -> "TU";
            case 3 -> "WE";
            case 4 -> "TH";
            case 5 -> "FR";
            case 6 -> "SA";
            case 7 -> "SU";
            default -> "MO";
        };
    }

    private static int isoDayOfWeek(String token) {
        return switch (token.toUpperCase(java.util.Locale.ROOT)) {
            case "MO", "MON", "1" -> 1;
            case "TU", "TUE", "2" -> 2;
            case "WE", "WED", "3" -> 3;
            case "TH", "THU", "4" -> 4;
            case "FR", "FRI", "5" -> 5;
            case "SA", "SAT", "6" -> 6;
            case "SU", "SUN", "7" -> 7;
            default -> -1;
        };
    }
}