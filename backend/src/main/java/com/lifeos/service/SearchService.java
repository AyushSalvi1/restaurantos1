package com.lifeos.service;

import com.lifeos.dto.SearchDtos;
import com.lifeos.entity.Goal;
import com.lifeos.entity.Habit;
import com.lifeos.entity.JournalEntry;
import com.lifeos.entity.LearningGoal;
import com.lifeos.entity.LearningResource;
import com.lifeos.repository.GoalRepository;
import com.lifeos.repository.HabitRepository;
import com.lifeos.repository.JournalEntryRepository;
import com.lifeos.repository.LearningGoalRepository;
import com.lifeos.repository.LearningResourceRepository;
import com.lifeos.repository.SavedItemRepository;
import com.lifeos.rag.RagService;
import com.lifeos.util.Csv;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Cross-module search.
 *
 * <p>Results are grouped by source and ranked by where the match landed: a title hit outranks a body
 * hit, and an exact prefix match outranks a match in the middle of the text. Journals are matched on
 * their title and tags rather than their body, so an inline search never surfaces a private entry the
 * user did not name.</p>
 */
@Service
public class SearchService {

    private static final int PER_GROUP = 8;
    private static final int KNOWLEDGE_TOP_K = 5;
    private static final List<String> TYPES =
            List.of("task", "goal", "habit", "learning", "resource", "journal", "knowledge", "saved");

    private final TaskService taskService;
    private final GoalRepository goalRepository;
    private final HabitRepository habitRepository;
    private final LearningGoalRepository learningGoalRepository;
    private final LearningResourceRepository resourceRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final SavedItemRepository savedItemRepository;
    private final RagService ragService;

    public SearchService(TaskService taskService,
                         GoalRepository goalRepository,
                         HabitRepository habitRepository,
                         LearningGoalRepository learningGoalRepository,
                         LearningResourceRepository resourceRepository,
                         JournalEntryRepository journalEntryRepository,
                         SavedItemRepository savedItemRepository,
                         RagService ragService) {
        this.taskService = taskService;
        this.goalRepository = goalRepository;
        this.habitRepository = habitRepository;
        this.learningGoalRepository = learningGoalRepository;
        this.resourceRepository = resourceRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.savedItemRepository = savedItemRepository;
        this.ragService = ragService;
    }

    @Transactional(readOnly = true)
    public SearchDtos.SearchResponse search(String userId, String rawQuery, String typeFilter) {
        long started = System.currentTimeMillis();
        String query = rawQuery == null ? "" : rawQuery.strip();
        if (query.isEmpty()) {
            return new SearchDtos.SearchResponse("", List.of(), 0, 0, TYPES);
        }
        String needle = query.toLowerCase(Locale.ROOT);
        List<String> wanted = typeFilter == null || typeFilter.isBlank()
                ? TYPES
                : TYPES.stream().filter(value -> value.equalsIgnoreCase(typeFilter.strip())).toList();

        List<SearchDtos.Group> groups = new ArrayList<>();
        if (wanted.contains("task")) {
            groups.add(group("task", "Tasks", taskItems(userId, needle, query)));
        }
        if (wanted.contains("goal")) {
            groups.add(group("goal", "Goals", goalItems(userId, needle, query)));
        }
        if (wanted.contains("habit")) {
            groups.add(group("habit", "Habits", habitItems(userId, needle, query)));
        }
        if (wanted.contains("learning")) {
            groups.add(group("learning", "Learning goals", learningItems(userId, needle, query)));
        }
        if (wanted.contains("resource")) {
            groups.add(group("resource", "Learning resources", resourceItems(userId, needle, query)));
        }
        if (wanted.contains("journal")) {
            groups.add(group("journal", "Journal", journalItems(userId, needle, query)));
        }
        if (wanted.contains("knowledge")) {
            groups.add(group("knowledge", "Knowledge base", knowledgeItems(userId, query)));
        }
        if (wanted.contains("saved")) {
            groups.add(group("saved", "Saved items", savedItems(userId, needle, query)));
        }

        groups.removeIf(group -> group.items().isEmpty());
        int total = groups.stream().mapToInt(group -> group.items().size()).sum();
        return new SearchDtos.SearchResponse(query, groups, total,
                System.currentTimeMillis() - started, TYPES);
    }

    // ---------------------------------------------------------------- sources

    private List<SearchDtos.ResultItem> taskItems(String userId, String needle, String query) {
        List<SearchDtos.ResultItem> items = new ArrayList<>();
        TaskQuery search = new TaskQuery(query, null, null, null, null, null, null, null, null, null,
                false, "deadline", 0, PER_GROUP * 4, null);
        taskService.search(userId, search).forEach(task -> {
            Double score = score(task.title(), task.description(), needle, query);
            if (score == null) {
                return;
            }
            items.add(new SearchDtos.ResultItem(task.id(), "task", task.title(),
                    snippet(task.description(), needle), task.status().name(), null,
                    "/tasks/" + task.id(), score));
        });
        return rank(items);
    }

    private List<SearchDtos.ResultItem> goalItems(String userId, String needle, String query) {
        List<SearchDtos.ResultItem> items = new ArrayList<>();
        for (Goal goal : goalRepository.findAllForUser(userId)) {
            Double score = score(goal.getTitle(), goal.getDescription(), needle, query);
            if (score == null) {
                continue;
            }
            items.add(new SearchDtos.ResultItem(goal.getId(), "goal", goal.getTitle(),
                    snippet(goal.getDescription(), needle), goal.getStatus().name(), null,
                    "/goals/" + goal.getId(), score));
        }
        return rank(items);
    }

    private List<SearchDtos.ResultItem> habitItems(String userId, String needle, String query) {
        List<SearchDtos.ResultItem> items = new ArrayList<>();
        for (Habit habit : habitRepository.findByUserIdOrderByCreatedAtAsc(userId)) {
            Double score = score(habit.getName(), habit.getDescription(), needle, query);
            if (score == null) {
                continue;
            }
            items.add(new SearchDtos.ResultItem(habit.getId(), "habit", habit.getName(),
                    snippet(habit.getDescription(), needle), habit.isArchived() ? "ARCHIVED" : "ACTIVE", null,
                    "/habits", score));
        }
        return rank(items);
    }

    private List<SearchDtos.ResultItem> learningItems(String userId, String needle, String query) {
        List<SearchDtos.ResultItem> items = new ArrayList<>();
        for (LearningGoal goal : learningGoalRepository.findAllForUser(userId)) {
            Double score = score(goal.getTitle(), goal.getDescription(), needle, query);
            if (score == null) {
                continue;
            }
            items.add(new SearchDtos.ResultItem(goal.getId(), "learning", goal.getTitle(),
                    snippet(goal.getDescription(), needle), goal.getStatus().name(), null,
                    "/learning/" + goal.getId(), score));
        }
        return rank(items);
    }

    private List<SearchDtos.ResultItem> resourceItems(String userId, String needle, String query) {
        List<SearchDtos.ResultItem> items = new ArrayList<>();
        for (LearningResource resource : resourceRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            Double score = score(resource.getTitle(), resource.getUrl(), needle, query);
            if (score == null) {
                continue;
            }
            items.add(new SearchDtos.ResultItem(resource.getId(), "resource", resource.getTitle(),
                    resource.getUrl(), resource.getResourceType().name(), null,
                    "/learning/resources/" + resource.getId(), score));
        }
        return rank(items);
    }

    private List<SearchDtos.ResultItem> journalItems(String userId, String needle, String query) {
        List<SearchDtos.ResultItem> items = new ArrayList<>();
        for (JournalEntry entry : journalEntryRepository.findByUserIdAndDeletedAtIsNullOrderByEntryDateDesc(userId)) {
            String tags = String.join(", ", Csv.split(entry.getTags()));
            Double score = score(entry.getTitle(), tags, needle, query);
            if (score == null) {
                continue;
            }
            items.add(new SearchDtos.ResultItem(entry.getId(), "journal",
                    entry.getTitle() == null ? "Entry of " + entry.getEntryDate() : entry.getTitle(),
                    "Entry for " + entry.getEntryDate() + " (" + entry.getWordCount() + " words)",
                    entry.getMood() == null ? null : entry.getMood().name(),
                    entry.getEntryDate(), "/journal/" + entry.getId(), score));
        }
        return rank(items);
    }

    private List<SearchDtos.ResultItem> knowledgeItems(String userId, String query) {
        return ragService.search(userId, query, KNOWLEDGE_TOP_K).stream()
                .map(hit -> new SearchDtos.ResultItem(hit.chunkId(), "knowledge", hit.documentTitle(),
                        hit.snippet(), null, null,
                        "/knowledge/" + hit.documentId() + "#chunk-" + hit.chunkIndex(),
                        Math.min(0.99, hit.score())))
                .toList();
    }

    private List<SearchDtos.ResultItem> savedItems(String userId, String needle, String query) {
        List<SearchDtos.ResultItem> items = new ArrayList<>();
        savedItemRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(
                        userId, PageRequest.of(0, 200)).forEach(saved -> {
            Double score = score(saved.getTitle(), saved.getContent(), needle, query);
            if (score == null) {
                return;
            }
            items.add(new SearchDtos.ResultItem(saved.getId(), "saved", saved.getTitle(),
                    snippet(saved.getContent(), needle), saved.getItemType().name(),
                    saved.getCreatedAt() == null ? null : saved.getCreatedAt().atZone(ZoneId.of("UTC"))
                            .toLocalDate(),
                    "/saved/" + saved.getId(), score));
        });
        return rank(items);
    }

    // --------------------------------------------------------------- ranking

    /**
     * Scores a title/body pair, or returns null when the term is absent. A title that starts with the
     * query ranks highest, then any title match, then a body match.
     */
    private Double score(String title, String body, String needle, String query) {
        String lowerTitle = title == null ? "" : title.toLowerCase(Locale.ROOT);
        String lowerBody = body == null ? "" : body.toLowerCase(Locale.ROOT);
        if (lowerTitle.startsWith(query.toLowerCase(Locale.ROOT))) {
            return 1.0;
        }
        if (lowerTitle.contains(needle)) {
            return 0.85;
        }
        if (lowerBody.contains(needle)) {
            return 0.6;
        }
        return null;
    }

    private List<SearchDtos.ResultItem> rank(List<SearchDtos.ResultItem> items) {
        return items.stream()
                .sorted(Comparator.comparingDouble(SearchDtos.ResultItem::score).reversed()
                        .thenComparing(SearchDtos.ResultItem::title))
                .limit(PER_GROUP)
                .toList();
    }

    private SearchDtos.Group group(String type, String label, List<SearchDtos.ResultItem> items) {
        return new SearchDtos.Group(type, label, items, items.size());
    }

    private String snippet(String body, String needle) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String normalised = body.replaceAll("\\s+", " ").strip();
        int index = normalised.toLowerCase(Locale.ROOT).indexOf(needle);
        if (index < 0) {
            return normalised.length() <= 160 ? normalised : normalised.substring(0, 160) + "...";
        }
        int start = Math.max(0, index - 60);
        int end = Math.min(normalised.length(), index + needle.length() + 60);
        String window = normalised.substring(start, end);
        return (start > 0 ? "..." : "") + window + (end < normalised.length() ? "..." : "");
    }
}
