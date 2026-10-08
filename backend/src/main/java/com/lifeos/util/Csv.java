package com.lifeos.util;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** CSV helpers used for the compact tag/target-day columns. */
public final class Csv {

    private Csv() {
    }

    public static String join(Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        String joined = values.stream()
                .filter(v -> v != null && !v.isBlank())
                .map(v -> v.trim())
                .collect(Collectors.joining(","));
        return joined.isEmpty() ? null : joined;
    }

    public static Set<String> split(String csv) {
        if (csv == null || csv.isBlank()) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static List<String> splitToList(String csv) {
        return List.copyOf(split(csv));
    }

    /** Escapes a value for embedding inside a generated prompt so it cannot break the template. */
    public static String sanitizeForPrompt(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.strip();
        return trimmed.length() > 400 ? trimmed.substring(0, 400) + "…" : trimmed;
    }
}