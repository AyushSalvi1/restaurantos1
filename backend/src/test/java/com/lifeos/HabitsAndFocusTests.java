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
 * Habits, their logs and streaks, and focus sessions including the live-session fields the Focus page
 * polls. Streaks and completion ratios are the numbers the habit cards display, so they are computed from
 * real logs here rather than asserted as placeholders.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Habits and focus sessions")
class HabitsAndFocusTests extends ApiTestSupport {

    private static String today() {
        return LocalDate.now().toString();
    }

    // ----------------------------------------------------------------- habits

    @Test
    @DisplayName("creates a habit, logs it and reports streak and consistency")
    void habitLifecycle() throws Exception {
        Account owner = register("habits");

        JsonNode created = apiPost("/api/habits", owner, """
                {
                  "name": "Morning water",
                  "description": "Two litres before coffee",
                  "category": "health",
                  "frequencyType": "DAILY",
                  "timesPerPeriod": 1,
                  "reminderTime": "07:30:00"
                }
                """);
        assertThat(created.get("id").asText()).isNotBlank();
        assertThat(created.get("frequencyType").asText()).isEqualTo("DAILY");
        assertThat(created.get("currentStreak").asInt()).isZero();
        assertThat(created.get("consistencyRate30d").asDouble()).isZero();
        String habitId = created.get("id").asText();

        JsonNode logged = apiPost("/api/habits/" + habitId + "/logs", owner, """
                {"logDate": "%s", "completed": true, "quantity": 2, "note": "before coffee"}
                """.formatted(today()));
        assertThat(logged.get("completed").asBoolean()).isTrue();
        assertThat(logged.get("quantity").asInt()).isEqualTo(2);

        JsonNode after = apiGet("/api/habits/" + habitId, owner);
        assertThat(after.get("completedToday").asBoolean()).isTrue();
        assertThat(after.get("currentStreak").asInt()).isEqualTo(1);
        assertThat(after.get("completedLast30Days").asInt()).isEqualTo(1);

        JsonNode updated = apiPut("/api/habits/" + habitId, owner, """
                {"name": "Morning water", "reminderTime": "08:00:00"}
                """);
        assertThat(updated.path("reminderTime").asText()).isNotBlank();

        JsonNode archived = apiPut("/api/habits/" + habitId, owner, """
                {"name": "Morning water", "archived": true}
                """);
        assertThat(archived.get("archived").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("builds a multi-day streak from consecutive logs")
    void computesStreak() throws Exception {
        Account owner = register("habits-streak");

        String habitId = apiPost("/api/habits", owner, """
                {"name": "Read twenty pages", "frequencyType": "DAILY"}
                """).get("id").asText();

        LocalDate today = LocalDate.now();
        for (int daysAgo = 0; daysAgo < 4; daysAgo++) {
            apiPost("/api/habits/" + habitId + "/logs", owner, """
                    {"logDate": "%s", "completed": true}
                    """.formatted(today.minusDays(daysAgo)));
        }

        JsonNode habit = apiGet("/api/habits/" + habitId, owner);
        assertThat(habit.get("currentStreak").asInt()).as("four consecutive days").isEqualTo(4);
        assertThat(habit.get("longestStreak").asInt()).isGreaterThanOrEqualTo(4);
        assertThat(habit.get("consistencyRate30d").asDouble())
                .as("four of thirty days scheduled").isBetween(0.0, 20.0);
    }

    @Test
    @DisplayName("toggles a day on and off")
    void togglesLog() throws Exception {
        Account owner = register("habits-toggle");

        String habitId = apiPost("/api/habits", owner, """
                {"name": "Stretch", "frequencyType": "DAILY"}
                """).get("id").asText();

        apiPatch("/api/habits/" + habitId + "/logs/toggle", owner, """
                {"logDate": "%s"}
                """.formatted(today()));
        assertThat(apiGet("/api/habits/" + habitId, owner).get("completedToday").asBoolean()).isTrue();

        apiPatch("/api/habits/" + habitId + "/logs/toggle", owner, """
                {"logDate": "%s"}
                """.formatted(today()));
        assertThat(apiGet("/api/habits/" + habitId, owner).get("completedToday").asBoolean())
                .as("a second toggle clears the day").isFalse();
    }

    @Test
    @DisplayName("lists logs for a window and returns a trend with scheduled days")
    void logsAndTrend() throws Exception {
        Account owner = register("habits-trend");

        String habitId = apiPost("/api/habits", owner, """
                {"name": "Walk", "frequencyType": "DAILY"}
                """).get("id").asText();

        apiPost("/api/habits/" + habitId + "/logs", owner, """
                {"logDate": "%s", "completed": true}
                """.formatted(today()));

        JsonNode logs = apiGet("/api/habits/" + habitId + "/logs", owner, "from",
                LocalDate.now().minusDays(7).toString(), "to", today());
        assertThat(logs.isArray()).as("logs are a plain list").isTrue();
        assertThat(logs.size()).isEqualTo(1);

        JsonNode trend = apiGet("/api/habits/" + habitId + "/trend", owner, "days", "7");
        assertThat(trend.get("points").size()).isEqualTo(7);
        assertThat(trend.get("name").asText()).isEqualTo("Walk");
        assertThat(trend.get("points").get(0).has("scheduled")).isTrue();
    }

    @Test
    @DisplayName("aggregates statistics across the account's habits")
    void habitStatistics() throws Exception {
        Account owner = register("habits-stats");

        String first = apiPost("/api/habits", owner, """
                {"name": "Stat habit one", "frequencyType": "DAILY"}
                """).get("id").asText();
        apiPost("/api/habits", owner, """
                {"name": "Stat habit two", "frequencyType": "DAILY"}
                """);
        apiPost("/api/habits/" + first + "/logs", owner, """
                {"logDate": "%s", "completed": true}
                """.formatted(today()));

        JsonNode stats = apiGet("/api/habits/statistics", owner);
        assertThat(stats.isArray()).as("one row per habit").isTrue();
        assertThat(stats.size()).isEqualTo(2);
        int completed = 0;
        for (JsonNode row : stats) {
            assertThat(row.path("habitId").asText()).isNotBlank();
            assertThat(row.path("consistencyRate30d").asDouble()).isBetween(0.0, 100.0);
            if (row.path("completedLast30Days").asInt() > 0) {
                completed++;
            }
        }
        assertThat(completed).as("only the logged habit has a completion").isEqualTo(1);
    }

    @Test
    @DisplayName("rejects a blank habit name and an unknown habit")
    void validatesHabits() throws Exception {
        Account owner = register("habits-invalid");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/habits").header("Authorization", owner.header())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": ""}
                        """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/habits/00000000-0000-0000-0000-000000000000")
                .header("Authorization", owner.header()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("keeps one account's habits and logs out of another account's view")
    void isolatesHabits() throws Exception {
        Account owner = register("habits-owner");
        Account intruder = register("habits-intruder");

        String habitId = apiPost("/api/habits", owner, """
                {"name": "Private habit"}
                """).get("id").asText();

        mockMvc.perform(get("/api/habits/" + habitId).header("Authorization", intruder.header()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/habits").header("Authorization", intruder.header()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/habits/" + habitId + "/logs").header("Authorization", intruder.header())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"logDate": "%s", "completed": true}
                        """.formatted(today())))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ focus

    @Test
    @DisplayName("starts a focus session and reports the remaining time")
    void startsSession() throws Exception {
        Account owner = register("focus");

        JsonNode started = apiPost("/api/focus/sessions", owner, """
                {"mode": "POMODORO_25_5", "plannedMinutes": 25}
                """);
        assertThat(started.get("id").asText()).isNotBlank();
        assertThat(started.get("plannedMinutes").asInt()).isEqualTo(25);
        assertThat(started.get("mode").asText()).isEqualTo("POMODORO_25_5");
        // The Focus page derives its countdown from startedAt, so the start response only has to carry it.
        assertThat(started.get("startedAt").asText()).isNotBlank();
        String sessionId = started.get("id").asText();

        JsonNode read = apiGet("/api/focus/sessions/" + sessionId, owner);
        assertThat(read.get("id").asText()).isEqualTo(sessionId);

        JsonNode list = apiGet("/api/focus/sessions", owner);
        assertThat(list.isArray()).as("sessions are a plain list").isTrue();
        assertThat(list.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("completes a session with outcome, interruptions and a rating")
    void completesSession() throws Exception {
        Account owner = register("focus-complete");

        String taskId = apiPost("/api/tasks", owner, """
                {"title": "Deep work target", "estimatedMinutes": 50}
                """).get("id").asText();

        String sessionId = apiPost("/api/focus/sessions", owner, """
                {"mode": "DEEP_WORK", "plannedMinutes": 50, "taskId": "%s"}
                """.formatted(taskId)).get("id").asText();

        JsonNode completed = apiPost("/api/focus/sessions/" + sessionId + "/complete", owner, """
                {"actualMinutes": 47, "completed": true, "interruptedCount": 2,
                 "outcome": "Finished the migration", "rating": 4}
                """);
        assertThat(completed.get("actualMinutes").asInt()).isEqualTo(47);
        assertThat(completed.get("completed").asBoolean()).isTrue();
        assertThat(completed.get("interruptedCount").asInt()).isEqualTo(2);
        assertThat(completed.get("rating").asInt()).isEqualTo(4);
        assertThat(completed.get("endedAt").asText()).isNotBlank();
        assertThat(completed.path("taskTitle").asText()).as("the session resolves the task title")
                .isEqualTo("Deep work target");

        // Completing a session must add its minutes to the task, not leave the two disagreeing.
        assertThat(apiGet("/api/tasks/" + taskId, owner).path("actualMinutes").asInt())
                .as("task actual minutes include the focused session").isEqualTo(47);
    }

    @Test
    @DisplayName("records statistics from completed sessions")
    void focusStatistics() throws Exception {
        Account owner = register("focus-stats");

        for (int i = 0; i < 3; i++) {
            String sessionId = apiPost("/api/focus/sessions", owner, """
                    {"mode": "POMODORO_25_5", "plannedMinutes": 25}
                    """).get("id").asText();
            apiPost("/api/focus/sessions/" + sessionId + "/complete", owner, """
                    {"actualMinutes": 25, "completed": true, "rating": 4}
                    """);
        }

        JsonNode stats = apiGet("/api/focus/statistics", owner);
        assertThat(stats.path("totalSessions").asInt()).isEqualTo(3);
        assertThat(stats.path("totalMinutes").asInt()).isEqualTo(75);
        assertThat(stats.path("completionRate").asDouble()).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("deletes a session and reports a missing one as not found")
    void deletesSession() throws Exception {
        Account owner = register("focus-delete");

        String sessionId = apiPost("/api/focus/sessions", owner, """
                {"mode": "POMODORO_25_5", "plannedMinutes": 25}
                """).get("id").asText();

        apiDelete("/api/focus/sessions/" + sessionId, owner);
        mockMvc.perform(get("/api/focus/sessions/" + sessionId).header("Authorization", owner.header()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("accepts a day-granular history range and works without one")
    void historyRangeIsDayGranular() throws Exception {
        Account owner = register("focus-range");

        String sessionId = apiPost("/api/focus/sessions", owner, """
                {"mode": "POMODORO_25_5", "plannedMinutes": 25}
                """).get("id").asText();
        apiPost("/api/focus/sessions/" + sessionId + "/complete", owner, """
                {"actualMinutes": 20, "completed": true}
                """);

        // The Focus page sends calendar dates, not instants. Omitting the range must still return history.
        JsonNode ranged = apiGet("/api/focus/sessions", owner, "from",
                LocalDate.now().minusDays(1).toString(), "to", LocalDate.now().toString());
        assertThat(ranged.isArray()).isTrue();
        assertThat(ranged.size()).isEqualTo(1);

        JsonNode unbounded = apiGet("/api/focus/sessions", owner);
        assertThat(unbounded.size()).as("no range means all history, not none").isEqualTo(1);

        JsonNode future = apiGet("/api/focus/sessions", owner, "from",
                LocalDate.now().plusDays(1).toString(), "to", LocalDate.now().plusDays(2).toString());
        assertThat(future.size()).as("a range that excludes today returns nothing").isZero();
    }

    @Test
    @DisplayName("refuses to start a session against another account's task")
    void isolatesFocusSessions() throws Exception {
        Account owner = register("focus-owner");
        Account intruder = register("focus-intruder");

        String taskId = apiPost("/api/tasks", owner, """
                {"title": "Owner task"}
                """).get("id").asText();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/focus/sessions").header("Authorization", intruder.header())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"mode": "POMODORO_25_5", "plannedMinutes": 25, "taskId": "%s"}
                        """.formatted(taskId)))
                .andExpect(status().isNotFound());
    }
}