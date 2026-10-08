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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin operations, the assistant's day planner, and the analytics endpoints the Analytics page calls.
 * Admin coverage matters here because it is the one area where authorisation is the feature: a normal
 * account must get nothing from it, and an admin must be able to do the documented jobs.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Admin, planning and analytics")
class AdminAndAnalyticsTests extends ApiTestSupport {

    /** Promotes an account to admin through the database, since there is no self-service route. */
    private void makeAdmin(Account account) {
        jdbc("update users set role_code = 'ADMIN' where id = '" + account.userId() + "'");
    }

    @org.springframework.beans.factory.annotation.Autowired
    private javax.sql.DataSource dataSource;

    private void jdbc(String sql) {
        try (java.sql.Connection connection = dataSource.getConnection();
             java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        } catch (java.sql.SQLException ex) {
            throw new IllegalStateException(ex);
        }
    }

    // ------------------------------------------------------------------ admin

    @Test
    @DisplayName("keeps every admin route closed to a normal account")
    void adminRoutesRejectNormalUsers() throws Exception {
        Account user = register("admin-user");
        String token = user.header();

        for (String path : new String[] {"/api/admin/stats", "/api/admin/users", "/api/admin/audit-logs",
                "/api/admin/error-logs", "/api/admin/settings", "/api/admin/ai-configuration"}) {
            mockMvc.perform(get(path).header("Authorization", token))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("lets an admin read platform stats and the user list")
    void adminReadsPlatformState() throws Exception {
        Account admin = register("admin-reads");
        makeAdmin(admin);

        JsonNode stats = apiGet("/api/admin/stats", admin);
        assertThat(stats.path("totalUsers").asLong()).isGreaterThanOrEqualTo(1);
        assertThat(stats.path("activeUsers").asLong()).isGreaterThanOrEqualTo(1);

        JsonNode users = apiGet("/api/admin/users", admin, "page", "0", "size", "10");
        assertThat(users.path("content").isArray()).isTrue();

        JsonNode audit = apiGet("/api/admin/audit-logs", admin);
        assertThat(audit.path("content").isArray()).isTrue();

        JsonNode errors = apiGet("/api/admin/error-logs", admin);
        assertThat(errors.path("content").isArray()).isTrue();

        JsonNode aiConfiguration = apiGet("/api/admin/ai-configuration", admin);
        assertThat(aiConfiguration.isObject()).isTrue();
    }

    @Test
    @DisplayName("lets an admin change another account's status")
    void adminSuspendsAccount() throws Exception {
        Account admin = register("admin-actions");
        makeAdmin(admin);
        Account target = register("admin-target");

        JsonNode suspended = read(mockMvc.perform(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/admin/users/" + target.userId() + "/status")
                        .header("Authorization", admin.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "DISABLED"}
                                """))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(suspended.get("status").asText()).isEqualTo("DISABLED");

        // A disabled account must stop working. Its token is refused at the filter, which the client reads as
// "signed out" rather than "forbidden", so a suspended user is bounced to the login screen.
        mockMvc.perform(get("/api/auth/me").header("Authorization", target.header()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/admin/users/" + target.userId() + "/status")
                .header("Authorization", admin.header())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "ACTIVE"}
                        """))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").header("Authorization", target.header()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("reads a user's privacy metadata")
    void adminReadsPrivacyMetadata() throws Exception {
        Account admin = register("admin-privacy");
        makeAdmin(admin);
        Account target = register("privacy-target");

        JsonNode metadata = apiGet("/api/admin/users/" + target.userId() + "/privacy", admin);
        assertThat(metadata.isObject()).isTrue();
        assertThat(metadata.toString()).doesNotContain(target.token());
    }

    @Test
    @DisplayName("stores and reads a platform setting")
    void adminManagesSettings() throws Exception {
        Account admin = register("admin-settings");
        makeAdmin(admin);

        mockMvc.perform(post("/api/admin/settings")
                        .header("Authorization", admin.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"key": "maintenance.notice", "value": "Back at 09:00", "valueType": "STRING"}
                                """))
                .andExpect(status().isOk());

        JsonNode settings = apiGet("/api/admin/settings", admin);
        assertThat(settings.toString()).contains("maintenance.notice");
    }

    // -------------------------------------------------------------- AI and day

    @Test
    @DisplayName("reports which provider is active and that offline output is a computation")
    void reportsProviderHonesty() throws Exception {
        Account owner = register("ai-providers");

        JsonNode providers = apiGet("/api/ai/providers", owner);
        assertThat(providers.path("activeProvider").asText()).isNotBlank();
        assertThat(providers.path("remoteProviderConfigured").isBoolean())
                .as("the client can tell no hosted model is in play").isTrue();
        assertThat(providers.path("note").asText())
                .as("the offline provider states what it actually is").isNotBlank();
        assertThat(providers.toString().toLowerCase())
                .as("the UI must be able to say this is not a hosted model")
                .contains("heuristic");
    }

    @Test
    @DisplayName("answers a question about the caller's own records and names the scopes used")
    void answersFromOwnRecords() throws Exception {
        Account owner = register("ai-chat");

        apiPost("/api/tasks", owner, """
                {"title": "Ship the release", "priority": "HIGH"}
                """);
        apiPost("/api/tasks", owner, """
                {"title": "Ship the docs", "priority": "LOW"}
                """);

        JsonNode chat = apiPost("/api/ai/chat", owner, """
                {"message": "How many open tasks do I have?"}
                """);
        assertThat(chat.get("reply").asText()).as("an answer, not an error").isNotBlank();
        assertThat(chat.get("conversationId").asText()).isNotBlank();
        assertThat(chat.get("messageId").asText()).isNotBlank();
        assertThat(chat.path("contextUsed").isArray()).isTrue();
        assertThat(chat.get("grounded").asBoolean())
                .as("the answer came from the caller's own records").isTrue();

        JsonNode conversations = apiGet("/api/ai/conversations", owner);
        assertThat(conversations.path("content").size()).isEqualTo(1);

        JsonNode scopes = apiGet("/api/ai/scopes", owner);
        assertThat(scopes.isArray()).isTrue();
    }

    @Test
    @DisplayName("renames and deletes a conversation")
    void conversationLifecycle() throws Exception {
        Account owner = register("ai-conversations");

        String conversationId = apiPost("/api/ai/chat", owner, """
                {"message": "What should I focus on?"}
                """).get("conversationId").asText();

        JsonNode renamed = apiPut("/api/ai/conversations/" + conversationId, owner, """
                {"title": "Focus review"}
                """);
        assertThat(renamed.path("summary").path("title").asText()).isEqualTo("Focus review");

        apiDelete("/api/ai/conversations/" + conversationId, owner);
        mockMvc.perform(get("/api/ai/conversations/" + conversationId)
                .header("Authorization", owner.header()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("plans a day from real records and applies the plan to the calendar")
    void plansAndAppliesDay() throws Exception {
        Account owner = register("ai-plan");

        apiPost("/api/tasks", owner, """
                {"title": "Write the report", "priority": "HIGH", "estimatedMinutes": 60}
                """);

        JsonNode plan = apiPost("/api/ai/plan-day", owner, """
                {"date": "%s", "availableMinutes": 240, "focusBlockMinutes": 50,
                 "breakMinutes": 10, "energyLevel": 3, "includeHabits": true}
                """.formatted(LocalDate.now()));
        assertThat(plan.path("blocks").isArray()).isTrue();
        assertThat(plan.path("blocks").size()).as("an available day produces blocks").isPositive();
        assertThat(plan.path("scheduledMinutes").asInt()).isPositive();
        assertThat(plan.get("utilisationPercent").asDouble()).isGreaterThan(0.0);

        int focusMinutes = 0;
        for (JsonNode block : plan.path("blocks")) {
            focusMinutes += block.path("durationMinutes").asInt();
        }
        assertThat(focusMinutes).as("the plan fits the time the user has").isLessThanOrEqualTo(240);

        // Applying an edit rebuilds the plan with that block moved; the plan itself stays derived rather
        // than becoming calendar rows, which is what the client is told when it applies an edit.
        JsonNode block = plan.path("blocks").get(0);
        java.time.Instant start = java.time.Instant.parse(block.get("startAt").asText()).plusSeconds(3_600);
        java.time.Instant end = java.time.Instant.parse(block.get("endAt").asText()).plusSeconds(3_600);
        JsonNode applied = apiPost("/api/ai/plan-day/apply", owner, """
                {"blocks": [{"blockId": "%s", "startAt": "%s", "endAt": "%s", "locked": true}]}
                """.formatted(block.get("id").asText(), start, end));
        assertThat(applied.path("blocks").isArray()).isTrue();
        assertThat(applied.get("date").asText()).isNotBlank();

        // An empty edit set is rejected rather than silently rebuilding nothing.
        mockMvc.perform(post("/api/ai/plan-day/apply")
                        .header("Authorization", owner.header())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"blocks": []}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("returns recommendations the client can act on")
    void returnsRecommendations() throws Exception {
        Account owner = register("ai-recommendations");

        apiPost("/api/tasks", owner, """
                {"title": "Overdue item", "deadline": "%sT09:00:00Z"}
                """.formatted(java.time.Instant.now().minusSeconds(86_400)));

        JsonNode recommendations = apiGet("/api/ai/recommendations", owner);
        assertThat(recommendations.path("recommendations").isArray()).isTrue();
        assertThat(recommendations.get("generatedBy").asText()).isNotBlank();
        for (JsonNode recommendation : recommendations.path("recommendations")) {
            assertThat(recommendation.get("id").asText()).isNotBlank();
            assertThat(recommendation.get("title").asText()).isNotBlank();
            assertThat(recommendation.path("score").asInt()).isPositive();
        }
    }

    @Test
    @DisplayName("does not show one account's conversations to another")
    void isolatesConversations() throws Exception {
        Account owner = register("ai-owner");
        Account intruder = register("ai-intruder");

        String conversationId = apiPost("/api/ai/chat", owner, """
                {"message": "Private planning thoughts"}
                """).get("conversationId").asText();

        mockMvc.perform(get("/api/ai/conversations/" + conversationId)
                .header("Authorization", intruder.header()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/ai/conversations").header("Authorization", intruder.header()))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    // -------------------------------------------------------------- analytics

    @Test
    @DisplayName("computes balance dimensions from the caller's records")
    void balanceScore() throws Exception {
        Account owner = register("analytics-balance");

        apiPost("/api/goals", owner, """
                {"title": "Analytics goal", "progress": 40}
                """);
        apiPost("/api/habits", owner, """
                {"name": "Analytics habit", "frequencyType": "DAILY"}
                """);

        JsonNode balance = apiGet("/api/analytics/balance", owner);
        assertThat(balance.path("dimensions").isArray()).isTrue();
        assertThat(balance.get("overallScore").asInt()).isBetween(0, 100);
        for (JsonNode dimension : balance.path("dimensions")) {
            assertThat(dimension.get("key").asText()).isNotBlank();
            assertThat(dimension.get("score").asInt()).isBetween(0, 100);
        }

        JsonNode weighted = apiPut("/api/analytics/balance/weights", owner, """
                {"weights": {"health": 40, "career": 30, "finance": 20, "relationships": 10}}
                """);
        assertThat(weighted.get("overallScore").asInt()).isBetween(0, 100);

        // The chosen weights must come back on the next read, not just in the response.
        boolean healthWeighted = false;
        for (JsonNode dimension : apiGet("/api/analytics/balance", owner).path("dimensions")) {
            if ("health".equals(dimension.get("key").asText())) {
                healthWeighted = dimension.get("weight").asDouble() == 40.0;
            }
        }
        assertThat(healthWeighted).as("the weights the user chose must persist").isTrue();
    }

    @Test
    @DisplayName("summarises a date range and reports data gaps rather than guessing")
    void analyticsSummary() throws Exception {
        Account owner = register("analytics-summary");

        String from = LocalDate.now().minusDays(6).toString();
        String to = LocalDate.now().toString();
        JsonNode summary = apiGet("/api/analytics/summary", owner, "from", from, "to", to);
        assertThat(summary.isObject()).isTrue();

        JsonNode full = apiGet("/api/analytics", owner, "from", from, "to", to);
        assertThat(full.path("dataGaps").isArray()).as("gaps are reported, not filled in").isTrue();
        assertThat(full.path("productivity").isObject() || full.has("insights")).isTrue();
    }

    @Test
    @DisplayName("generates, dismisses and keeps insights and predictions")
    void insightsAndPredictions() throws Exception {
        Account owner = register("analytics-insights");

        apiPost("/api/tasks", owner, """
                {"title": "Insight task one", "status": "COMPLETED"}
                """);
        apiPost("/api/tasks", owner, """
                {"title": "Insight task two"}
                """);
        apiPost("/api/habits", owner, """
                {"name": "Insight habit", "frequencyType": "DAILY"}
                """);

        JsonNode generated = apiPost("/api/analytics/insights/generate", owner, "{}");
        assertThat(generated.has("created")).isTrue();
        assertThat(generated.path("evaluated").asInt()).isPositive();
        assertThat(generated.path("skippedReasons").isArray()).isTrue();

        JsonNode insights = apiGet("/api/analytics/insights", owner);
        for (JsonNode insight : insights.path("content")) {
            assertThat(insight.get("id").asText()).isNotBlank();
            assertThat(insight.get("title").asText()).isNotBlank();
            assertThat(insight.path("factors").isArray())
                    .as("every insight explains itself").isTrue();
        }

        if (insights.path("content").size() > 0) {
            String insightId = insights.path("content").get(0).get("id").asText();
            apiPatch("/api/analytics/insights/" + insightId, owner, """
                    {"dismissed": true}
                    """);
            assertThat(apiGet("/api/analytics/insights", owner, "includeDismissed", "false")
                    .path("content").toString()).doesNotContain(insightId);
        }

        JsonNode regenerated = apiPost("/api/analytics/predictions/regenerate", owner, "{}");
        assertThat(regenerated.has("created")).isTrue();

        JsonNode predictions = apiGet("/api/analytics/predictions", owner);
        assertThat(predictions.isArray()).isTrue();
        for (JsonNode prediction : predictions) {
            assertThat(prediction.path("probability").asInt()).isBetween(0, 100);
            assertThat(prediction.path("factors").isArray())
                    .as("a prediction states what drove it").isTrue();
        }
    }

    @Test
    @DisplayName("never shows one account's analytics to another")
    void isolatesAnalytics() throws Exception {
        Account owner = register("analytics-owner");
        Account intruder = register("analytics-intruder");

        apiPost("/api/tasks", owner, """
                {"title": "Owner task for analytics"}
                """);
        apiPost("/api/analytics/insights/generate", owner, "{}");

        assertThat(apiGet("/api/analytics/insights", intruder).path("totalElements").asLong()).isZero();
        assertThat(apiGet("/api/analytics/predictions", intruder).size()).isZero();
    }
}