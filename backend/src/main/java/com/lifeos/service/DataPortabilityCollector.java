package com.lifeos.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Gathers every row belonging to one account for export and for account deletion.
 *
 * <p>Both features need the same enumeration, so it lives in one place: adding a module means adding
 * it here once, and the export and the deletion stay in step. Journal entries and knowledge documents
 * are included deliberately, because they are the user's own data and a complete export requires them.</p>
 */
@Service
public class DataPortabilityCollector {

    private static final int JOURNAL_PAGE_SIZE = 200;
    private static final int EXPORT_HORIZON_DAYS = 3650;
    private static final int CALENDAR_FROM_YEAR = 1970;
    private static final int CALENDAR_TO_YEAR = 2999;

    private final GoalService goalService;
    private final HabitService habitService;
    private final CalendarService calendarService;
    private final FinanceService financeService;
    private final LearningService learningService;
    private final FocusService focusService;
    private final JournalService journalService;
    private final KnowledgeService knowledgeService;
    private final PreferenceService preferenceService;

    public DataPortabilityCollector(GoalService goalService,
                                    HabitService habitService,
                                    CalendarService calendarService,
                                    FinanceService financeService,
                                    LearningService learningService,
                                    FocusService focusService,
                                    JournalService journalService,
                                    KnowledgeService knowledgeService,
                                    PreferenceService preferenceService) {
        this.goalService = goalService;
        this.habitService = habitService;
        this.calendarService = calendarService;
        this.financeService = financeService;
        this.learningService = learningService;
        this.focusService = focusService;
        this.journalService = journalService;
        this.knowledgeService = knowledgeService;
        this.preferenceService = preferenceService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> collect(String userId) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("preferences", preferenceService.preferences(userId));
        data.put("goals", goalService.allActive(userId));
        data.put("habits", habitService.list(userId, true));
        data.put("calendar", calendarService.feed(userId,
                LocalDate.of(CALENDAR_FROM_YEAR, 1, 1),
                LocalDate.of(CALENDAR_TO_YEAR, 12, 31)));
        data.put("finance", financeService.overview(userId));
        data.put("learningGoals", learningService.goals(userId, null));
        data.put("learningResources", learningService.resources(userId, null));
        data.put("skills", learningService.skills(userId));
        data.put("focus", focusService.statistics(userId, EXPORT_HORIZON_DAYS));
        data.put("journal", journalEntries(userId));
        data.put("knowledgeDocuments", knowledgeService.list(userId, null, 0, 1000));
        return data;
    }

    private List<Object> journalEntries(String userId) {
        List<Object> entries = new ArrayList<>();
        int page = 0;
        while (true) {
            var result = journalService.page(userId, PageRequest.of(page, JOURNAL_PAGE_SIZE));
            entries.addAll(result.getContent());
            if (!result.hasNext()) {
                return entries;
            }
            page++;
        }
    }

    /** Section names an export contains, so the UI can state what will be included. */
    public List<String> sectionNames() {
        return List.of("preferences", "goals", "habits", "calendar", "finance",
                "learningGoals", "learningResources", "skills", "focus", "journal", "knowledgeDocuments");
    }
}
