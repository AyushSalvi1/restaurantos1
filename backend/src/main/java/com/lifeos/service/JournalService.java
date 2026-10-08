package com.lifeos.service;

import com.lifeos.ai.AIProvider;
import com.lifeos.ai.AiMessage;
import com.lifeos.ai.AiProviderRegistry;
import com.lifeos.ai.AiRequest;
import com.lifeos.ai.Prompts;
import com.lifeos.dto.JournalDtos;
import com.lifeos.entity.JournalEntry;
import com.lifeos.entity.enums.JournalMood;
import com.lifeos.exception.AppException;
import com.lifeos.repository.JournalEntryRepository;
import com.lifeos.util.Csv;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Journal storage, statistics and optional thematic analysis.
 *
 * <p>Statistics are computed from metadata only, so aggregate figures can never leak entry text. The
 * optional analysis summarises the user's own words and always carries a disclaimer: LIFEOS does not
 * diagnose moods or offer psychological advice.</p>
 */
@Service
public class JournalService {

    private static final int ANALYSED_WORD_LIMIT = 2500;

    private final JournalEntryRepository journalEntryRepository;
    private final AiProviderRegistry providerRegistry;
    private final UserZoneService userZoneService;

    public JournalService(JournalEntryRepository journalEntryRepository,
                          AiProviderRegistry providerRegistry,
                          UserZoneService userZoneService) {
        this.journalEntryRepository = journalEntryRepository;
        this.providerRegistry = providerRegistry;
        this.userZoneService = userZoneService;
    }

    // ------------------------------------------------------------------ CRUD

    @Transactional(readOnly = true)
    public Page<JournalDtos.JournalResponse> page(String userId, Pageable pageable) {
        return journalEntryRepository.findByUserIdAndDeletedAtIsNullOrderByEntryDateDesc(userId, pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public JournalDtos.JournalResponse get(String userId, String entryId) {
        return toResponse(requireOwned(userId, entryId));
    }

    @Transactional
    public JournalDtos.JournalResponse create(String userId, JournalDtos.JournalRequest request) {
        journalEntryRepository.findByUserIdAndEntryDateAndDeletedAtIsNull(userId, request.entryDate())
                .ifPresent(existing -> {
                    existing.setDeletedAt(java.time.Instant.now());
                    journalEntryRepository.save(existing);
                });

        JournalEntry entry = new JournalEntry();
        entry.setUserId(userId);
        apply(entry, request);
        return toResponse(journalEntryRepository.save(entry));
    }

    @Transactional
    public JournalDtos.JournalResponse update(String userId, String entryId, JournalDtos.JournalRequest request) {
        JournalEntry entry = requireOwned(userId, entryId);
        apply(entry, request);
        return toResponse(journalEntryRepository.save(entry));
    }

    @Transactional
    public void delete(String userId, String entryId) {
        JournalEntry entry = requireOwned(userId, entryId);
        entry.setDeletedAt(java.time.Instant.now());
        journalEntryRepository.save(entry);
    }

    @Transactional
    public JournalDtos.JournalResponse setAnalysis(String userId, String entryId, JournalDtos.JournalAnalysis analysis) {
        JournalEntry entry = requireOwned(userId, entryId);
        entry.setAiAnalyzed(true);
        entry.setAiSummary(analysis.summary());
        return toResponse(journalEntryRepository.save(entry));
    }

    @Transactional(readOnly = true)
    public JournalEntry requireOwned(String userId, String entryId) {
        return journalEntryRepository.findByIdAndUserIdAndDeletedAtIsNull(entryId, userId)
                .orElseThrow(() -> AppException.notFound("Journal entry not found"));
    }

    // ------------------------------------------------------------ statistics

    @Transactional(readOnly = true)
    public JournalDtos.JournalStats statistics(String userId) {
        ZoneId zone = userZoneService.zoneOf(userId);
        LocalDate today = LocalDate.now(zone);
        LocalDate last30 = today.minusDays(29);
        YearMonth month = YearMonth.from(today);

        long total = journalEntryRepository.countByUserIdAndDeletedAtIsNull(userId);
        long thisMonth = journalEntryRepository.countByUserIdAndDeletedAtIsNullAndEntryDateBetween(
                userId, month.atDay(1), today);
        long daysWithEntries = journalEntryRepository.countDaysWithEntries(userId, last30, today);
        long words = journalEntryRepository.sumWordsBetween(userId, last30, today);

        Map<JournalMood, Long> moodCounts = new TreeMap<>();
        Map<String, Long> tagCounts = new TreeMap<>();
        for (JournalEntry entry : journalEntryRepository
                .findByUserIdAndDeletedAtIsNullAndEntryDateBetweenOrderByEntryDateDesc(userId, last30, today)) {
            if (entry.getMood() != null) {
                moodCounts.merge(entry.getMood(), 1L, Long::sum);
            }
            Csv.split(entry.getTags()).forEach(tag -> tagCounts.merge(tag, 1L, Long::sum));
        }

        List<JournalDtos.JournalStats.MoodPoint> moods = moodCounts.entrySet().stream()
                .map(entry -> new JournalDtos.JournalStats.MoodPoint(entry.getKey().name(), entry.getValue()))
                .toList();
        List<JournalDtos.JournalStats.TagCount> tags = tagCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(10)
                .map(entry -> new JournalDtos.JournalStats.TagCount(entry.getKey(), entry.getValue()))
                .toList();

        String mostFrequentMood = moodCounts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> entry.getKey().name())
                .orElse(null);

        String reflection = buildReflection(total, daysWithEntries, words, tags, mostFrequentMood);

        return new JournalDtos.JournalStats(total, thisMonth, daysWithEntries, words, moods, tags,
                mostFrequentMood, reflection);
    }

    private String buildReflection(long total, long daysWithEntries, long words,
                                   List<JournalDtos.JournalStats.TagCount> tags, String mostFrequentMood) {
        if (total == 0) {
            return "No journal entries yet, so there is nothing to reflect on.";
        }
        StringBuilder text = new StringBuilder();
        text.append("You have written ").append(daysWithEntries).append(" of the last 30 days (")
                .append(Math.round(daysWithEntries * 100.0 / 30)).append("% of days) and ")
                .append(words).append(" words in that window.");
        if (!tags.isEmpty()) {
            text.append(" Your most-used tags are ")
                    .append(String.join(", ", tags.stream().limit(3).map(JournalDtos.JournalStats.TagCount::tag).toList()))
                    .append('.');
        }
        if (mostFrequentMood != null) {
            text.append(" The most frequent mood you logged was ").append(mostFrequentMood)
                    .append(". This is a description of your entries, not an assessment of how you are doing.");
        }
        return text.toString();
    }

    // -------------------------------------------------------------- analysis

    /**
     * Summarises an entry's themes. The on-device path extracts structure from the text itself, so
     * this works with no API key configured; a remote provider refines the same bullet structure.
     */
    @Transactional(readOnly = true)
    public JournalDtos.JournalAnalysis analyse(String userId, String entryId) {
        JournalEntry entry = requireOwned(userId, entryId);
        String text = entry.getContent() == null ? "" : entry.getContent();
        List<String> themes = topWords(text, 6);
        List<String> goals = sentencesContaining(text, List.of("goal", "aim", "want to", "plan to", "decide"), 4);
        List<String> patterns = sentencesContaining(text, List.of("because", "so that", "which means", "turns out"), 4);
        List<String> topics = topWords(text, 4);
        String summary = summarise(entry, themes, goals);

        AIProvider provider = providerRegistry.active();
        String refined = providerRegistry.status().remoteProviderConfigured()
                ? refineWithModel(text, themes, goals, patterns)
                : summary;
        return new JournalDtos.JournalAnalysis(entryId, themes, goals, patterns, topics,
                refined == null || refined.isBlank() ? summary : refined,
                providerRegistry.status().remoteProviderConfigured(),
                "LIFEOS summarises your own words. It does not provide medical or psychological advice.");
    }

    private String refineWithModel(String text, List<String> themes, List<String> goals, List<String> patterns) {
        String body = text.length() > ANALYSED_WORD_LIMIT ? text.substring(0, ANALYSED_WORD_LIMIT) + "..." : text;
        AIProvider provider = providerRegistry.active();
        return provider.complete(AiRequest.of(List.of(
                AiMessage.system("Summarise the user's journal entry in at most three sentences. Describe themes "
                        + "only. Do not diagnose, advise on health, or comment on how the person is doing."),
                AiMessage.user(Prompts.contextBlock("ENTRY", List.of(body))
                        + "\nThemes already detected: " + String.join(", ", themes)
                        + "\nIntent statements already detected: " + String.join(", ", goals)
                        + "\nCausal statements already detected: " + String.join(", ", patterns))))).text();
    }

    private String summarise(JournalEntry entry, List<String> themes, List<String> goals) {
        StringBuilder summary = new StringBuilder();
        summary.append(entry.getWordCount()).append(" words");
        if (entry.getMood() != null) {
            summary.append(", mood logged as ").append(entry.getMood());
        }
        summary.append('.');
        if (!themes.isEmpty()) {
            summary.append(" Most frequent topics: ").append(String.join(", ", themes.stream().limit(4).toList()))
                    .append('.');
        }
        if (!goals.isEmpty()) {
            summary.append(" You stated an intention ").append(goals.size()).append(" time(s).");
        }
        return summary.toString();
    }

    /** Frequency-ranked words after stop-word removal. Deterministic, so repeated calls agree. */
    private List<String> topWords(String text, int limit) {
        Map<String, Integer> counts = new TreeMap<>();
        for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}']+")) {
            if (word.length() >= 4 && !STOP_WORDS.contains(word)) {
                counts.merge(word, 1, Integer::sum);
            }
        }
        List<Map.Entry<String, Integer>> ranked = counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .toList();
        return ranked.stream().map(Map.Entry::getKey).toList();
    }

    private List<String> sentencesContaining(String text, List<String> markers, int limit) {
        List<String> sentences = new ArrayList<>();
        for (String sentence : text.split("(?<=[.!?])\\s+")) {
            String trimmed = sentence.strip();
            if (trimmed.isEmpty()) {
                continue;
            }
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (markers.stream().anyMatch(lower::contains)) {
                sentences.add(trimmed.length() > 200 ? trimmed.substring(0, 200) + "..." : trimmed);
                if (sentences.size() == limit) {
                    break;
                }
            }
        }
        return sentences;
    }

    // -------------------------------------------------------------- internals

    private void apply(JournalEntry entry, JournalDtos.JournalRequest request) {
        entry.setTitle(request.title() == null || request.title().isBlank() ? null : request.title().strip());
        entry.setContent(request.content().strip());
        entry.setEntryDate(request.entryDate());
        entry.setMood(request.mood());
        entry.setMoodScore(request.moodScore());
        entry.setTags(Csv.join(request.tags()));
        entry.setWordCount(countWords(entry.getContent()));
        entry.setAiAnalyzed(false);
        entry.setAiSummary(null);
    }

    private int countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return (int) text.strip().split("\\s+").length;
    }

    public JournalDtos.JournalResponse toResponse(JournalEntry entry) {
        return new JournalDtos.JournalResponse(
                entry.getId(),
                entry.getTitle(),
                entry.getContent(),
                entry.getEntryDate(),
                entry.getMood(),
                entry.getMoodScore(),
                Csv.splitToList(entry.getTags()),
                entry.isAiAnalyzed(),
                entry.getAiSummary(),
                entry.getWordCount(),
                entry.getCreatedAt(),
                entry.getUpdatedAt());
    }

    /** Words too common in reflective writing to carry a theme. */
    private static final java.util.Set<String> STOP_WORDS = java.util.Set.of(
            "that", "this", "with", "from", "they", "then", "than", "there", "these", "those",
            "have", "been", "were", "will", "would", "could", "should", "about", "into", "just", "like",
            "really", "today", "yesterday", "tomorrow", "because", "thing", "things", "much", "some",
            "when", "what", "your", "their", "doing", "make", "made", "want", "know",
            "feel", "felt", "going", "also", "even", "still", "more", "less", "very");
}
