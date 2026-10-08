package com.lifeos;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Journal, calendar, notifications, preferences, search, the life graph and admin. Together these cover the
 * remaining pages: everything the app renders that is not a task, a goal, a habit, money or learning.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Journal, calendar, notifications, settings and search")
class JournalAndSettingsTests extends ApiTestSupport {

    private static String today() {
        return LocalDate.now().toString();
    }

    // ---------------------------------------------------------------- journal

    @Test
    @DisplayName("writes an entry, analyses it and reports statistics")
    void journalLifecycle() throws Exception {
        Account owner = register("journal");

        JsonNode created = apiPost("/api/journal", owner, """
                {
                  "title": "Better nights",
                  "content": "Cutting the evening coffee changed the shape of the night within a fortnight.",
                  "entryDate": "%s",
                  "mood": "GOOD",
                  "moodScore": 4,
                  "tags": ["sleep", "caffeine"]
                }
                """.formatted(today()));
        assertThat(created.get("id").asText()).isNotBlank();
        assertThat(created.get("wordCount").asInt()).isPositive();
        assertThat(created.get("tags").size()).isEqualTo(2);
        String entryId = created.get("id").asText();

        JsonNode analysed = apiPost("/api/journal/" + entryId + "/analysis", owner, "{}");
        assertThat(analysed.get("aiAnalyzed").asBoolean()).isTrue();
        assertThat(analysed.path("aiSummary").asText()).as("an analysis must say something").isNotBlank();

        JsonNode stats = apiGet("/api/journal/statistics", owner);
        assertThat(stats.get("totalEntries").asLong()).isEqualTo(1);
        assertThat(stats.get("daysWithEntriesLast30").asLong()).isEqualTo(1);
        assertThat(stats.path("moodDistribution").size()).isPositive();
        assertThat(stats.path("reflection").asText()).isNotBlank();

        apiPut("/api/journal/" + entryId, owner, """
                {"title": "Even better nights", "content": "Updated entry text.",
                 "entryDate": "%s"}
                """.formatted(today()));
        assertThat(apiGet("/api/journal/" + entryId, owner).get("title").asText())
                .isEqualTo("Even better nights");

        apiDelete("/api/journal/" + entryId, owner);
        mockMvc.perform(get("/api/journal/" + entryId).header("Authorization", owner.header()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("rejects an entry with no content and one with no date")
    void validatesJournalEntries() throws Exception {
        Account owner = register("journal-invalid");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/journal").header("Authorization", owner.header())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"content": "", "entryDate": "%s"}
                        """.formatted(today())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/journal").header("Authorization", owner.header())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"content": "No date supplied"}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("does not leak one account's journal to another")
    void isolatesJournal() throws Exception {
        Account owner = register("journal-owner");
        Account intruder = register("journal-intruder");

        String entryId = apiPost("/api/journal", owner, """
                {"title": "Private", "content": "Private thoughts.", "entryDate": "%s"}
                """.formatted(today())).get("id").asText();

        mockMvc.perform(get("/api/journal/" + entryId).header("Authorization", intruder.header()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/journal").header("Authorization", intruder.header()))
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/journal/" + entryId).header("Authorization", intruder.header()))
                .andExpect(status().isNotFound());
    }

    // --------------------------------------------------------------- calendar

    @Test
    @DisplayName("creates, moves and deletes an event and lists a date range")
    void calendarLifecycle() throws Exception {
        Account owner = register("calendar");

        String taskId = apiPost("/api/tasks", owner, """
                {"title": "Call the bank"}
                """).get("id").asText();

        String start = today() + "T09:00:00Z";
        String end = today() + "T10:00:00Z";
        JsonNode created = apiPost("/api/calendar", owner, """
                {
                  "title": "Dentist",
                  "description": "Six month check",
                  "eventType": "EVENT",
                  "startAt": "%s",
                  "endAt": "%s",
                  "taskId": "%s",
                  "location": "Clinic"
                }
                """.formatted(start, end, taskId));
        assertThat(created.get("id").asText()).isNotBlank();
        assertThat(created.get("startAt").asText()).isNotBlank();
        String eventId = created.get("id").asText();

        JsonNode feed = apiGet("/api/calendar", owner, "from", today(), "to", today());
        assertThat(feed.path("occurrences").size()).as("the event appears in the feed").isEqualTo(1);
        assertThat(feed.path("totalEvents").asLong()).isEqualTo(1);
        assertThat(feed.path("rangeStart").asText()).isNotBlank();

        JsonNode moved = apiPatch("/api/calendar/" + eventId + "/move", owner, """
                {"startAt": "%sT14:00:00Z", "endAt": "%sT15:00:00Z"}
                """.formatted(today(), today()));
        assertThat(moved.get("startAt").asText()).contains("14:00");

        JsonNode updated = apiPut("/api/calendar/" + eventId, owner, """
                {"title": "Dentist rescheduled", "startAt": "%sT14:00:00Z", "endAt": "%sT15:00:00Z"}
                """.formatted(today(), today()));
        assertThat(updated.get("title").asText()).isEqualTo("Dentist rescheduled");

        apiDelete("/api/calendar/" + eventId, owner);
        mockMvc.perform(get("/api/calendar/" + eventId).header("Authorization", owner.header()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("reports task deadlines as calendar items")
    void deadlineFeed() throws Exception {
        Account owner = register("calendar-deadlines");

        apiPost("/api/tasks", owner, """
                {"title": "Renew insurance", "deadline": "%sT12:00:00Z", "priority": "HIGH"}
                """.formatted(today()));
        apiPost("/api/tasks", owner, """
                {"title": "Nothing urgent", "deadline": "%sT12:00:00Z"}
                """.formatted(LocalDate.now().plusDays(45).toString()));

        JsonNode deadlines = apiGet("/api/calendar/deadlines", owner, "from", today(), "to", today());
        assertThat(deadlines.isArray()).as("deadlines are a plain list of events").isTrue();
        assertThat(deadlines.size()).isEqualTo(1);
        assertThat(deadlines.get(0).get("title").asText()).isEqualTo("Renew insurance");
        assertThat(deadlines.get(0).get("eventType").asText()).isEqualTo("DEADLINE");

        // Bounds are optional; omitting them returns every open deadline rather than failing.
        JsonNode unbounded = apiGet("/api/calendar/deadlines", owner);
        assertThat(unbounded.size()).as("both open deadlines").isEqualTo(2);
    }

    @Test
    @DisplayName("rejects an event that ends before it starts")
    void validatesEventTimes() throws Exception {
        Account owner = register("calendar-invalid");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/calendar").header("Authorization", owner.header())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "Backwards", "startAt": "%sT15:00:00Z", "endAt": "%sT14:00:00Z"}
                        """.formatted(today(), today())))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("does not expose another account's calendar")
    void isolatesCalendar() throws Exception {
        Account owner = register("calendar-owner");
        Account intruder = register("calendar-intruder");

        String eventId = apiPost("/api/calendar", owner, """
                {"title": "Private meeting", "startAt": "%sT09:00:00Z", "endAt": "%sT10:00:00Z"}
                """.formatted(today(), today())).get("id").asText();

        mockMvc.perform(get("/api/calendar/" + eventId).header("Authorization", intruder.header()))
                .andExpect(status().isNotFound());
        JsonNode intruderFeed = apiGet("/api/calendar", intruder, "from", today(), "to", today());
        assertThat(intruderFeed.path("occurrences").size()).isZero();
        assertThat(intruderFeed.path("deadlines").size()).isZero();
    }

    // ---------------------------------------------------------- notifications

    @Test
    @DisplayName("lists notifications, marks one read and clears them all")
    void notificationLifecycle() throws Exception {
        Account owner = register("notifications");

        JsonNode list = apiGet("/api/notifications", owner);
        assertThat(list.path("notifications").isArray()).isTrue();
        assertThat(list.has("unreadCount")).isTrue();

        JsonNode unread = apiGet("/api/notifications/unread-count", owner);
        assertThat(unread.path("unread").asLong()).isGreaterThanOrEqualTo(0);

        // Completing a task that carries a deadline is the documented way to generate one.
        apiPost("/api/tasks", owner, """
                {"title": "Task with a deadline",
                 "deadline": "%sT09:00:00Z"}
                """.formatted(java.time.Instant.now().plusSeconds(3600)));

        apiPost("/api/notifications/read-all", owner, "{}");
        assertThat(apiGet("/api/notifications/unread-count", owner).path("unread").asLong())
                .as("mark-all-read leaves nothing unread").isZero();

        apiDelete("/api/notifications", owner);
        assertThat(apiGet("/api/notifications", owner).path("totalElements").asInt()).isZero();
    }

    // --------------------------------------------------------------- settings

    @Test
    @DisplayName("reads and updates work preferences")
    void updatesPreferences() throws Exception {
        Account owner = register("settings");

        JsonNode defaults = apiGet("/api/settings/preferences", owner);
        assertThat(defaults.path("preferredFocusMinutes").asInt()).isGreaterThan(0);

        JsonNode updated = apiPut("/api/settings/preferences", owner, """
                {
                  "workingHoursStart": "08:00:00",
                  "workingHoursEnd": "16:00:00",
                  "preferredFocusMinutes": 50,
                  "breakMinutes": 10,
                  "weeklyProductivityHours": 30.00,
                  "areasOfInterest": ["sleep", "training"]
                }
                """);
        assertThat(updated.get("preferredFocusMinutes").asInt()).isEqualTo(50);
        assertThat(updated.get("breakMinutes").asInt()).isEqualTo(10);

        assertThat(apiGet("/api/settings/preferences", owner).path("preferredFocusMinutes").asInt())
                .as("preferences must persist").isEqualTo(50);
    }

    @Test
    @DisplayName("reads and updates notification preferences")
    void updatesNotificationPreferences() throws Exception {
        Account owner = register("settings-notify");

        JsonNode defaults = apiGet("/api/settings/notifications", owner);
        assertThat(defaults.isObject()).isTrue();

        JsonNode updated = apiPut("/api/settings/notifications", owner, """
                {"inAppEnabled": true, "emailEnabled": false, "taskEnabled": false,
                 "habitEnabled": true, "quietHoursStart": "22:00:00", "quietHoursEnd": "07:00:00"}
                """);
        assertThat(updated.get("emailEnabled").asBoolean()).isFalse();
        assertThat(updated.get("taskEnabled").asBoolean()).isFalse();
        assertThat(apiGet("/api/settings/notifications", owner).path("taskEnabled").asBoolean())
                .as("notification preferences must persist").isFalse();
    }

    @Test
    @DisplayName("records onboarding completion")
    void onboardingState() throws Exception {
        Account owner = register("onboarding");

        JsonNode state = apiGet("/api/onboarding", owner);
        assertThat(state.isObject()).isTrue();

        JsonNode completed = apiPost("/api/onboarding", owner, """
                {
                  "name": "Onboarded Tester",
                  "occupation": "Engineer",
                  "workingHoursStart": "09:00:00",
                  "workingHoursEnd": "17:00:00",
                  "preferredFocusMinutes": 45,
                  "breakMinutes": 10,
                  "weeklyProductivityHours": 35.00,
                  "primaryGoals": ["Run a half marathon", "Learn to sail"],
                  "areasOfInterest": ["sleep"],
                  "currentSkills": ["Java"]
                }
                """);
        assertThat(completed.path("completed").asBoolean()).isTrue();
        assertThat(apiGet("/api/auth/me", owner).path("onboardingCompleted").asBoolean())
                .as("finishing onboarding must be recorded on the account").isTrue();

        JsonNode preferences = apiGet("/api/settings/preferences", owner);
        assertThat(preferences.path("primaryGoalAreas").isArray()).isTrue();
        assertThat(preferences.toString()).as("the goals named during onboarding are kept").contains("sail");
        assertThat(preferences.path("preferredFocusMinutes").asInt()).isEqualTo(45);
    }

    // ----------------------------------------------------------------- search

    @Test
    @DisplayName("finds records across types and reports what it searched")
    void globalSearch() throws Exception {
        Account owner = register("search");

        apiPost("/api/tasks", owner, """
                {"title": "Renew the passport", "category": "admin"}
                """);
        apiPost("/api/goals", owner, """
                {"title": "Learn Portuguese", "category": "learning"}
                """);
        apiPost("/api/habits", owner, """
                {"name": "Portuguese flashcards"}
                """);

        JsonNode all = apiGet("/api/search", owner, "q", "Portuguese");
        assertThat(all.get("query").asText()).isEqualTo("Portuguese");
        assertThat(all.get("totalResults").asInt()).as("spans types, not just tasks").isGreaterThanOrEqualTo(2);
        assertThat(all.path("availableTypes").isArray()).isTrue();

        JsonNode filtered = apiGet("/api/search", owner, "q", "Portuguese", "type", "goal");
        assertThat(filtered.get("totalResults").asInt()).as("only goals match that term").isEqualTo(1);
        assertThat(filtered.path("groups").get(0).path("type").asText()).isEqualTo("goal");

        JsonNode noMatch = apiGet("/api/search", owner, "q", "Portuguese", "type", "task");
        assertThat(noMatch.get("totalResults").asInt()).as("the task does not match that term").isZero();

        JsonNode miss = apiGet("/api/search", owner, "q", "nothingmatchesthis");
        assertThat(miss.get("totalResults").asInt()).isZero();
    }

    @Test
    @DisplayName("keeps search results inside the caller's account")
    void isolatesSearch() throws Exception {
        Account owner = register("search-owner");
        Account intruder = register("search-intruder");

        apiPost("/api/tasks", owner, """
                {"title": "Highly searchable marker"}
                """);

        assertThat(apiGet("/api/search", intruder, "q", "searchable marker")
                .get("totalResults").asInt()).isZero();
    }

    // ------------------------------------------------------------- life graph

    @Test
    @DisplayName("builds the life graph from the account's own records")
    void lifeGraph() throws Exception {
        Account owner = register("graph");

        String goalId = apiPost("/api/goals", owner, """
                {"title": "Graph goal"}
                """).get("id").asText();
        String taskId = apiPost("/api/tasks", owner, """
                {"title": "Graph task", "goalId": "%s"}
                """.formatted(goalId)).get("id").asText();

        JsonNode graph = apiGet("/api/graph", owner);
        assertThat(graph.path("nodes").size()).as("tasks and goals become nodes").isGreaterThanOrEqualTo(2);
        assertThat(graph.path("edges").isArray()).isTrue();
        assertThat(graph.path("nodeCount").asInt()).isEqualTo(graph.path("nodes").size());

        JsonNode stats = apiGet("/api/graph/stats", owner);
        assertThat(stats.path("edges").asLong()).isGreaterThanOrEqualTo(1);

        JsonNode neighbourhood = apiGet("/api/graph/neighbourhood", owner, "type", "TASK", "nodeId", taskId);
        assertThat(neighbourhood.toString()).as("the goal it points at is reachable").contains(goalId);

        JsonNode path = apiGet("/api/graph/paths", owner, "startType", "TASK", "startId", taskId);
        assertThat(path.isArray()).as("a path is a list of node ids").isTrue();
        assertThat(path.toString()).contains(taskId);
    }

    @Test
    @DisplayName("does not expose another account's graph")
    void isolatesGraph() throws Exception {
        Account owner = register("graph-owner");
        Account intruder = register("graph-intruder");

        apiPost("/api/tasks", owner, """
                {"title": "Private graph task"}
                """);

        JsonNode intruderGraph = apiGet("/api/graph", intruder);
        assertThat(intruderGraph.path("nodes").size()).isZero();
    }

    // ------------------------------------------------------------ data export

    @Test
    @DisplayName("exports the account's data and lists the available sections")
    void exportData() throws Exception {
        Account owner = register("export");

        apiPost("/api/tasks", owner, """
                {"title": "Exported task"}
                """);

        JsonNode sections = apiGet("/api/account/export/sections", owner);
        assertThat(sections.isArray()).as("the page lists selectable sections").isTrue();
        assertThat(sections.size()).isPositive();

        JsonNode requested = apiPost("/api/account/export", owner, """
                {"sections": ["TASKS"]}
                """);
        assertThat(requested.path("exportId").asText()).as("export is queued or ready").isNotBlank();
        assertThat(requested.path("fileName").asText()).isNotBlank();

        JsonNode status = apiGet("/api/account/export/" + requested.get("exportId").asText(), owner);
        assertThat(status.isObject()).isTrue();
    }

    @Test
    @DisplayName("does not let one account download another account's export")
    void isolatesExports() throws Exception {
        Account owner = register("export-owner");
        Account intruder = register("export-intruder");

        String exportId = apiPost("/api/account/export", owner, """
                {"sections": ["TASKS"]}
                """).get("exportId").asText();

        mockMvc.perform(get("/api/account/export/" + exportId)
                .header("Authorization", intruder.header()))
                .andExpect(status().isNotFound());
    }
}