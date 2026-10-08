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
 * The Finance page and the Learning page. Amounts and progress are asserted numerically because those are
 * exactly the fields that render as a wall of dashes when a projection silently returns nothing.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Finance and learning")
class FinanceAndLearningTests extends ApiTestSupport {

    private String monthStart() {
        LocalDate today = LocalDate.now();
        return today.withDayOfMonth(1).toString();
    }

    // ---------------------------------------------------------------- finance

    @Test
    @DisplayName("records income and expenses and builds the monthly summary")
    void transactionsAndSummary() throws Exception {
        Account owner = register("finance");

        apiPost("/api/finance/transactions", owner, """
                {
                  "transactionType": "INCOME",
                  "amount": 4200.00,
                  "category": "salary",
                  "description": "Monthly salary",
                  "occurredOn": "%s"
                }
                """.formatted(monthStart()));
        apiPost("/api/finance/transactions", owner, """
                {
                  "transactionType": "EXPENSE",
                  "amount": 1450.00,
                  "category": "housing",
                  "occurredOn": "%s"
                }
                """.formatted(monthStart()));
        apiPost("/api/finance/transactions", owner, """
                {
                  "transactionType": "EXPENSE",
                  "amount": 520.00,
                  "category": "food",
                  "occurredOn": "%s"
                }
                """.formatted(monthStart()));

        JsonNode summary = apiGet("/api/finance/summary", owner);
        assertThat(summary.get("income").decimalValue()).isEqualByComparingTo("4200.00");
        assertThat(summary.get("expenses").decimalValue()).isEqualByComparingTo("1970.00");
        assertThat(summary.get("savings").decimalValue()).isEqualByComparingTo("2230.00");
        assertThat(summary.get("expenseByCategory").size()).isEqualTo(2);

        JsonNode trend = apiGet("/api/finance/trend", owner, "months", "6");
        assertThat(trend.isArray()).as("trend is a plain list of months").isTrue();
        assertThat(trend.size()).isGreaterThanOrEqualTo(1);

        JsonNode patterns = apiGet("/api/finance/spending-patterns", owner);
        assertThat(patterns.isArray()).isTrue();
    }

    @Test
    @DisplayName("filters transactions by type, category and date range")
    void filtersTransactions() throws Exception {
        Account owner = register("finance-filter");

        apiPost("/api/finance/transactions", owner, """
                {"transactionType": "EXPENSE", "amount": 100.00, "category": "food", "occurredOn": "%s"}
                """.formatted(monthStart()));
        apiPost("/api/finance/transactions", owner, """
                {"transactionType": "EXPENSE", "amount": 250.00, "category": "transport", "occurredOn": "%s"}
                """.formatted(monthStart()));
        apiPost("/api/finance/transactions", owner, """
                {"transactionType": "INCOME", "amount": 900.00, "category": "salary", "occurredOn": "%s"}
                """.formatted(monthStart()));

        JsonNode all = apiGet("/api/finance/transactions", owner);
        assertThat(all.path("totalElements").asInt()).isEqualTo(3);

        JsonNode expenses = apiGet("/api/finance/transactions", owner, "type", "EXPENSE");
        assertThat(expenses.path("totalElements").asInt()).isEqualTo(2);

        JsonNode food = apiGet("/api/finance/transactions", owner, "category", "food");
        assertThat(food.path("totalElements").asInt()).isEqualTo(1);

        JsonNode categories = apiGet("/api/finance/categories", owner);
        assertThat(categories.toString()).contains("food").contains("transport");
    }

    @Test
    @DisplayName("updates and deletes a transaction")
    void updatesAndDeletesTransaction() throws Exception {
        Account owner = register("finance-edit");

        String id = apiPost("/api/finance/transactions", owner, """
                {"transactionType": "EXPENSE", "amount": 60.00, "category": "food", "occurredOn": "%s"}
                """.formatted(monthStart())).get("id").asText();

        JsonNode updated = apiPut("/api/finance/transactions/" + id, owner, """
                {"transactionType": "EXPENSE", "amount": 75.50, "category": "food", "occurredOn": "%s"}
                """.formatted(monthStart()));
        assertThat(updated.get("amount").decimalValue()).isEqualByComparingTo("75.50");

        apiDelete("/api/finance/transactions/" + id, owner);
        assertThat(apiGet("/api/finance/transactions", owner).path("totalElements").asInt()).isZero();
    }

    @Test
    @DisplayName("reports budget spend against the limit")
    void budgetUtilisation() throws Exception {
        Account owner = register("finance-budget");

        apiPost("/api/finance/transactions", owner, """
                {"transactionType": "EXPENSE", "amount": 250.00, "category": "food", "occurredOn": "%s"}
                """.formatted(monthStart()));

        String budgetId = apiPost("/api/finance/budgets", owner, """
                {
                  "category": "food",
                  "period": "MONTHLY",
                  "amount": 200.00,
                  "startDate": "%s",
                  "endDate": "%s"
                }
                """.formatted(monthStart(), LocalDate.now().withDayOfMonth(1).plusMonths(1)
                .minusDays(1).toString())).get("id").asText();

        JsonNode budget = apiGet("/api/finance/budgets", owner).get(0);
        assertThat(budget.get("spent").decimalValue()).isEqualByComparingTo("250.00");
        assertThat(budget.get("exceeded").asBoolean()).as("spending past the limit is flagged").isTrue();
        assertThat(budget.get("utilisationPercent").asDouble()).isGreaterThan(100.0);

        apiDelete("/api/finance/budgets/" + budgetId, owner);
        assertThat(apiGet("/api/finance/budgets", owner).size()).isZero();
    }

    @Test
    @DisplayName("tracks progress towards a savings goal")
    void savingsGoalProgress() throws Exception {
        Account owner = register("finance-savings");

        String id = apiPost("/api/finance/savings-goals", owner, """
                {
                  "name": "Emergency fund",
                  "targetAmount": 12000.00,
                  "savedAmount": 3000.00,
                  "targetDate": "%s"
                }
                """.formatted(LocalDate.now().plusDays(180).toString())).get("id").asText();

        JsonNode goal = apiGet("/api/finance/savings-goals", owner).get(0);
        assertThat(goal.get("progressPercent").asDouble()).isEqualTo(25.0);

        JsonNode updated = apiPut("/api/finance/savings-goals/" + id, owner, """
                {"name": "Emergency fund", "targetAmount": 12000.00, "savedAmount": 6000.00}
                """);
        assertThat(updated.get("progressPercent").asDouble()).isEqualTo(50.0);

        apiDelete("/api/finance/savings-goals/" + id, owner);
        assertThat(apiGet("/api/finance/savings-goals", owner).size()).isZero();
    }

    @Test
    @DisplayName("builds an overview the dashboard can render")
    void overviewIncludesEverySection() throws Exception {
        Account owner = register("finance-overview");

        apiPost("/api/finance/transactions", owner, """
                {"transactionType": "INCOME", "amount": 3000.00, "category": "salary", "occurredOn": "%s"}
                """.formatted(monthStart()));
        apiPost("/api/finance/budgets", owner, """
                {"period": "MONTHLY", "amount": 1000.00, "startDate": "%s", "endDate": "%s"}
                """.formatted(monthStart(), LocalDate.now().withDayOfMonth(1).plusMonths(1)
                .minusDays(1).toString()));
        apiPost("/api/finance/savings-goals", owner, """
                {"name": "New laptop", "targetAmount": 2000.00, "savedAmount": 500.00}
                """);

        JsonNode overview = apiGet("/api/finance/overview", owner);
        assertThat(overview.get("incomeThisMonth").decimalValue()).isEqualByComparingTo("3000.00");
        assertThat(overview.get("budgets").size()).isEqualTo(1);
        assertThat(overview.get("savingsGoals").size()).isEqualTo(1);
        assertThat(overview.get("monthlyTrend").isArray()).isTrue();
        assertThat(overview.get("currency").asText()).isNotBlank();
    }

    @Test
    @DisplayName("rejects a negative amount and a missing category")
    void validatesTransactions() throws Exception {
        Account owner = register("finance-invalid");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/finance/transactions").header("Authorization", owner.header())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"transactionType": "EXPENSE", "amount": -50, "category": "food",
                         "occurredOn": "%s"}
                        """.formatted(monthStart())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/finance/transactions").header("Authorization", owner.header())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"transactionType": "EXPENSE", "amount": 10.00, "occurredOn": "%s"}
                        """.formatted(monthStart())))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("keeps one account's money out of another's view")
    void isolatesFinance() throws Exception {
        Account owner = register("finance-owner");
        Account intruder = register("finance-intruder");

        String id = apiPost("/api/finance/transactions", owner, """
                {"transactionType": "EXPENSE", "amount": 40.00, "category": "food", "occurredOn": "%s"}
                """.formatted(monthStart())).get("id").asText();

        assertThat(apiGet("/api/finance/transactions", intruder).path("totalElements").asInt()).isZero();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/finance/transactions/" + id).header("Authorization", intruder.header()))
                .andExpect(status().isNotFound());
        assertThat(apiGet("/api/finance/transactions", owner).path("totalElements").asInt()).isEqualTo(1);
    }

    // --------------------------------------------------------------- learning

    @Test
    @DisplayName("creates a learning goal with topics and completes one")
    void learningGoalWithTopics() throws Exception {
        Account owner = register("learning");

        JsonNode goal = apiPost("/api/learning/goals", owner, """
                {
                  "title": "Learn query tuning",
                  "description": "Understand the planner",
                  "category": "data",
                  "topics": [
                    {"title": "Reading explain plans", "estimatedMinutes": 45},
                    {"title": "Index selection", "estimatedMinutes": 60}
                  ]
                }
                """);
        assertThat(goal.get("id").asText()).isNotBlank();
        assertThat(goal.get("topicCount").asLong()).isEqualTo(2);
        assertThat(goal.get("progress").asInt()).isZero();
        String goalId = goal.get("id").asText();

        String topicId = goal.get("topics").get(0).get("id").asText();
        JsonNode completed = apiPatch("/api/learning/topics/" + topicId + "/complete", owner, "{}");
        assertThat(completed.get("completed").asBoolean()).isTrue();

        JsonNode after = apiGet("/api/learning/goals/" + goalId, owner);
        assertThat(after.get("completedTopicCount").asLong()).isEqualTo(1);
        assertThat(after.get("progress").asInt()).as("one of two topics is half done").isEqualTo(50);
    }

    @Test
    @DisplayName("adds and removes a topic independently")
    void addsAndDeletesTopic() throws Exception {
        Account owner = register("learning-topics");

        String goalId = apiPost("/api/learning/goals", owner, """
                {"title": "Study statistics"}
                """).get("id").asText();

        String topicId = apiPost("/api/learning/goals/" + goalId + "/topics", owner, """
                {"title": "Bayesian refresher", "estimatedMinutes": 30}
                """).get("id").asText();

        assertThat(apiGet("/api/learning/goals/" + goalId, owner).get("topics").size()).isEqualTo(1);

        apiDelete("/api/learning/topics/" + topicId, owner);
        assertThat(apiGet("/api/learning/goals/" + goalId, owner).get("topics").size()).isZero();
    }

    @Test
    @DisplayName("records study sessions and rolls them into the statistics")
    void studySessions() throws Exception {
        Account owner = register("learning-sessions");

        String goalId = apiPost("/api/learning/goals", owner, """
                {"title": "Learn Rust"}
                """).get("id").asText();

        apiPost("/api/learning/sessions", owner, """
                {"learningGoalId": "%s", "startedAt": "%sT09:00:00Z",
                 "endedAt": "%sT10:30:00Z", "minutes": 90, "quizScore": 8.5}
                """.formatted(goalId, LocalDate.now(), LocalDate.now()));

        JsonNode sessions = apiGet("/api/learning/sessions", owner);
        assertThat(sessions.isArray()).as("sessions are a plain list").isTrue();
        assertThat(sessions.size()).isEqualTo(1);
        assertThat(sessions.get(0).path("minutes").asInt()).isEqualTo(90);

        JsonNode study = apiGet("/api/learning/study", owner, "days", "7");
        assertThat(study.isArray()).as("one point per day").isTrue();
        assertThat(study.size()).isEqualTo(7);
        assertThat(study.get(0).path("minutes").asInt()).isGreaterThanOrEqualTo(0);

        JsonNode stats = apiGet("/api/learning/statistics", owner);
        assertThat(stats.get("activeGoals").asLong()).isEqualTo(1);
        assertThat(stats.get("minutesThisWeek").asInt()).isGreaterThanOrEqualTo(90);
        assertThat(stats.get("averageQuizScore").asDouble()).isEqualTo(8.5);
    }

    @Test
    @DisplayName("manages resources and skills")
    void resourcesAndSkills() throws Exception {
        Account owner = register("learning-resources");

        String resourceId = apiPost("/api/learning/resources", owner, """
                {
                  "title": "Postgres docs",
                  "url": "https://www.postgresql.org/docs/",
                  "resourceType": "DOCUMENTATION"
                }
                """).get("id").asText();
        assertThat(apiGet("/api/learning/resources", owner).size()).isEqualTo(1);
        apiDelete("/api/learning/resources/" + resourceId, owner);
        assertThat(apiGet("/api/learning/resources", owner).size()).isZero();
        
        String skillId = apiPost("/api/learning/skills", owner, """
                {"name": "SQL", "category": "data", "proficiency": 3}
                """).get("id").asText();
        assertThat(apiGet("/api/learning/skills", owner).size()).isEqualTo(1);

        // A learning goal may point at one of the account's own skills.
        String goalId = apiPost("/api/learning/goals", owner, """
                {"title": "Deepen SQL", "skillId": "%s"}
                """.formatted(skillId)).get("id").asText();
        assertThat(apiGet("/api/learning/goals/" + goalId, owner).get("skillId").asText())
                .isEqualTo(skillId);

        apiDelete("/api/learning/skills/" + skillId, owner);
        assertThat(apiGet("/api/learning/skills", owner).size()).isZero();
    }

    @Test
    @DisplayName("confirms an assistant-proposed roadmap")
    void confirmsRoadmap() throws Exception {
        Account owner = register("learning-roadmap");

        JsonNode confirmed = apiPost("/api/learning/roadmaps/confirm", owner, """
                {
                  "title": "Become a data engineer",
                  "category": "data",
                  "stages": [
                    {"title": "Foundations", "topics": ["SQL", "Modelling"]},
                    {"title": "Pipelines", "topics": ["Batch"]}
                  ]
                }
                """);
        assertThat(confirmed.get("id").asText()).isNotBlank();
        assertThat(confirmed.get("topicCount").asLong()).as("two topics in the first stage and one in the second").isEqualTo(3);
    }

    @Test
    @DisplayName("rejects a learning goal that points at another account's skill")
    void isolatesLearning() throws Exception {
        Account owner = register("learning-owner");
        Account intruder = register("learning-intruder");

        String skillId = apiPost("/api/learning/skills", owner, """
                {"name": "Owner skill"}
                """).get("id").asText();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/learning/goals").header("Authorization", intruder.header())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "Should not attach", "skillId": "%s"}
                        """.formatted(skillId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/learning/skills").header("Authorization", intruder.header()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("deletes a learning goal and hides it")
    void deletesLearningGoal() throws Exception {
        Account owner = register("learning-delete");

        String goalId = apiPost("/api/learning/goals", owner, """
                {"title": "Temporary course"}
                """).get("id").asText();

        apiDelete("/api/learning/goals/" + goalId, owner);
        mockMvc.perform(get("/api/learning/goals/" + goalId).header("Authorization", owner.header()))
                .andExpect(status().isNotFound());
    }
}