package com.lifeos.dto;

import java.time.LocalDate;
import java.util.List;

/** Global search payloads grouped by source. */
public final class SearchDtos {

    private SearchDtos() {
    }

    public record ResultItem(
            String id,
            String type,
            String title,
            String snippet,
            String status,
            LocalDate date,
            String link,
            double score
    ) {
    }

    public record Group(String type, String label, List<ResultItem> items, long total) {
    }

    public record SearchResponse(
            String query,
            List<Group> groups,
            int totalResults,
            long tookMillis,
            List<String> availableTypes
    ) {
    }
}