package com.lifeos.dto;

import com.lifeos.entity.enums.JournalMood;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Journal payloads. Content is private to the owning account and never exposed to admins. */
public final class JournalDtos {

    private JournalDtos() {
    }

    public record JournalRequest(
            @Size(max = 200) String title,
            @NotBlank @Size(max = 200000) String content,
            @NotNull LocalDate entryDate,
            JournalMood mood,
            @Min(1) @Max(5) Integer moodScore,
            List<String> tags
    ) {
    }

    public record JournalResponse(
            String id,
            String title,
            String content,
            LocalDate entryDate,
            JournalMood mood,
            Integer moodScore,
            List<String> tags,
            boolean aiAnalyzed,
            String aiSummary,
            int wordCount,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    /** Metadata-only view used by statistics so full text is never aggregated. */
    public record JournalStats(
            long totalEntries,
            long entriesThisMonth,
            long daysWithEntriesLast30,
            long wordsLast30,
            List<MoodPoint> moodDistribution,
            List<TagCount> topTags,
            String mostFrequentMood,
            String reflection
    ) {
        public record MoodPoint(String mood, long count) {
        }

        public record TagCount(String tag, long count) {
        }
    }

    /** Thematic summary produced by the optional AI analysis. Non-clinical by design. */
    public record JournalAnalysis(
            String entryId,
            List<String> recurringThemes,
            List<String> goalsMentioned,
            List<String> productivityPatterns,
            List<String> referencedTopics,
            String summary,
            boolean aiGenerated,
            String disclaimer
    ) {
        public static JournalAnalysis local(String entryId, List<String> themes, List<String> goals,
                                            List<String> patterns, List<String> topics, String summary) {
            return new JournalAnalysis(entryId, themes, goals, patterns, topics, summary, false,
                    "LIFEOS summarises your own words. It does not provide medical or psychological advice.");
        }
    }
}