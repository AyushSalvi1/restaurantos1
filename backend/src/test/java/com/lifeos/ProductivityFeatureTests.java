package com.lifeos;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full task lifecycle and the goal graph around it: board grouping, quick capture, dependencies,
 * recurrence, bulk actions, reordering, milestones and progress. These are the endpoints the Tasks,
 * Today and Goals pages depend on, exercised end to end through the API.
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Tasks, goals and milestones")
class ProductivityFeatureTests extends ApiTestSupport {

    // ------------------------------------------------------------------- tasks

    @Test
    @DisplayName("creates, completes and deletes a task")
    void taskLifecycle() throws Exception {
        Account owner = register("tasks");

        JsonNode created = apiPost("/api/tasks", owner, """
                {
                  "title": "Write the migration",
                  "description": "Add the missing audit column",
                  "priority": "HIGH",
                  "category": "engineering",
                  "estimatedMinutes": 90,
                  "difficulty": "MEDIUM",
                  "energyRequirement": "HIGH",
                  "tags": ["backend", "schema"]
                }
                """);
        assertThat(created.get("id").asText()).isNotBlank();
        assertThat(created.get("title").asText()).isEqualTo("Write the migration");
        assertThat(created.get("status").asText()).isEqualTo("TODO");
        assertThat(created.get("priority").asText()).isEqualTo("HIGH");
        assertThat(created.get("tags")).hasSize(2);
        String taskId = created.get("id").asText();

        JsonNode fetched = apiGet("/api/tasks/" + taskId, owner);
        assertThat(fetched.get("id").asText()).isEqualTo(taskId);
        assertThat(fetched.get("estimatedMinutes").asInt()).isEqualTo(90);

        JsonNode updated = apiPut("/api/tasks/" + taskId, owner, """
                {"title": "Write the audit migration", "notes": "needs a default for existing rows"}
                """);
        assertThat(updated.get("title").asText()).isEqualTo("Write the audit migration");
        assertThat(updated.get("notes").asText()).isNotBlank();

        JsonNode completed = apiPost("/api/tasks/" + taskId + "/complete", owner, """
                {"actualMinutes": 105, "notes": "done and reviewed"}
                """);
        assertThat(completed.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(completed.get("actualMinutes").asInt()).isEqualTo(105);
        assertThat(completed.get("completedAt").asText()).isNotBlank();

        JsonNode statusChange = apiPatch("/api/tasks/" + taskId + "/status", owner, """
                {"status": "IN_PROGRESS"}
                """);
        assertThat(statusChange.get("status").asText()).isEqualTo("IN_PROGRESS");

        assertThat(apiDelete("/api/tasks/" + taskId, owner).isEmpty()).isTrue();
        assertThat(apiGet("/api/tasks/" + taskId, owner).isMissingNode()
                || "NOT_FOUND".equals(apiGet("/api/tasks/" + taskId, owner).path("code").asText())).isTrue();
    }

    @Test
    @DisplayName("rejects a blank title and an unknown status instead of storing them")
    void validatesTaskInput() throws Exception {
        Account owner = register("tasks-invalid");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/tasks")
                .header("Authorization", owner.header())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "   "}
                        """))
                .andExpect(status().isBadRequest());

        String taskId = apiPost("/api/tasks", owner, """
                {"title": "Valid task"}
                """).get("id").asText();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/tasks/" + taskId + "/status")
                .header("Authorization", owner.header())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""
                        {"status": "NOT_A_STATUS"}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("quick capture creates a task with only a title")
    void quickCreate() throws Exception {
        Account owner = register("tasks-quick");

        JsonNode quick = apiPost("/api/tasks/quick", owner, """
                {"title": "Call the dentist", "priority": "LOW"}
                """);
        assertThat(quick.get("id").asText()).isNotBlank();
        assertThat(quick.get("title").asText()).isEqualTo("Call the dentist");
        assertThat(quick.get("priority").asText()).isEqualTo("LOW");
    }

    @Test
    @DisplayName("groups the board by status so the board page has columns to render")
    void boardGroupsByStatus() throws Exception {
        Account owner = register("tasks-board");

        apiPost("/api/tasks", owner, """
                {"title": "Backlog item", "status": "TODO"}
                """);
        apiPost("/api/tasks", owner, """
                {"title": "Doing item", "status": "IN_PROGRESS"}
                """);
        apiPost("/api/tasks", owner, """
                {"title": "Done item", "status": "COMPLETED"}
                """);

JsonNode board = apiGet("/api/tasks/board", owner);
        assertThat(board.isArray()).as("board returns one column per status").isTrue();
        assertThat(board.findValuesAsText("status")).contains("TODO", "IN_PROGRESS", "COMPLETED");
        int todoCount = -1;
        int doneCount = -1;
        for (JsonNode column : board) {
            assertThat(column.path("label").asText())
                    .as("column %s needs a label to render", column.path("status")).isNotBlank();
            if ("TODO".equals(column.path("status").asText())) {
                todoCount = column.path("tasks").size();
            }
            if ("COMPLETED".equals(column.path("status").asText())) {
                doneCount = column.path("tasks").size();
            }
        }
        assertThat(todoCount).isEqualTo(1);
        assertThat(doneCount).isEqualTo(1);
    }

    @Test
    @DisplayName("filters and paginates the task list")
    void filtersAndPaginates() throws Exception {
        Account owner = register("tasks-filter");

        for (int i = 1; i <= 5; i++) {
            apiPost("/api/tasks", owner, """
                    {"title": "Filterable task %d", "priority": "%s", "status": "%s"}
                    """.formatted(i, i % 2 == 0 ? "HIGH" : "LOW", i % 3 == 0 ? "COMPLETED" : "TODO"));
        }

        JsonNode high = apiGet("/api/tasks", owner, "priority", "HIGH");
        assertThat(high.get("totalElements").asInt()).isEqualTo(2);
        assertThat(high.get("content").size()).isEqualTo(2);

        JsonNode completed = apiGet("/api/tasks", owner, "status", "COMPLETED");
        assertThat(completed.get("totalElements").asInt()).isEqualTo(1);

        JsonNode paged = apiGet("/api/tasks", owner, "page", "0", "size", "2");
        assertThat(paged.get("content").size()).isEqualTo(2);
        assertThat(paged.get("totalElements").asInt()).isEqualTo(5);
        assertThat(paged.get("totalPages").asInt()).isEqualTo(3);
        assertThat(paged.get("page").asInt()).isZero();
    }

    @Test
    @DisplayName("applies bulk actions and reports which ids failed")
    void bulkActions() throws Exception {
        Account owner = register("tasks-bulk");

        String first = apiPost("/api/tasks", owner, """
                {"title": "Bulk one"}
                """).get("id").asText();
        String second = apiPost("/api/tasks", owner, """
                {"title": "Bulk two"}
                """).get("id").asText();

        JsonNode done = apiPost("/api/tasks/bulk", owner, """
                {"taskIds": ["%s", "%s"], "action": "COMPLETE"}
                """.formatted(first, second));
        assertThat(done.get("affected").asInt()).isEqualTo(2);
        assertThat(done.get("message").asText()).isNotBlank();

        String third = apiPost("/api/tasks", owner, """
                {"title": "Bulk three"}
                """).get("id").asText();
        JsonNode prioritised = apiPost("/api/tasks/bulk", owner, """
                {"taskIds": ["%s"], "action": "SET_PRIORITY", "priority": "HIGH"}
                """.formatted(third));
        assertThat(prioritised.get("affected").asInt()).isEqualTo(1);
        assertThat(apiGet("/api/tasks/" + third, owner).get("priority").asText()).isEqualTo("HIGH");

        JsonNode failed = apiPost("/api/tasks/bulk", owner, """
                {"taskIds": ["%s", "00000000-0000-0000-0000-000000000000"], "action": "DELETE"}
                """.formatted(third));
        assertThat(failed.get("affected").asInt()).isEqualTo(1);
        assertThat(failed.get("failedIds").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("reorders tasks and persists the new positions")
    void reordersTasks() throws Exception {
        Account owner = register("tasks-reorder");

        String a = apiPost("/api/tasks", owner, """
                {"title": "Order A"}
                """).get("id").asText();
        String b = apiPost("/api/tasks", owner, """
                {"title": "Order B"}
                """).get("id").asText();

JsonNode result = apiPost("/api/tasks/reorder", owner, """
                {"tasks": [{"id": "%s", "position": 1}, {"id": "%s", "position": 0}]}
                """.formatted(a, b));
        assertThat(result.isArray()).as("reorder returns the reordered tasks").isTrue();
        assertThat(result.size()).isEqualTo(2);
        assertThat(result.get(0).path("id").asText()).isEqualTo(b);
        assertThat(result.get(0).path("position").asInt()).isZero();

        // The new positions must be persisted, not just echoed back in the response.
        assertThat(apiGet("/api/tasks/" + b, owner).path("position").asInt())
                .as("task b is first after the reorder").isZero();
        assertThat(apiGet("/api/tasks/" + a, owner).path("position").asInt())
                .as("task a is second after the reorder").isEqualTo(1);
    }

    @Test
    @DisplayName("tracks task dependencies in both directions")
    void taskDependencies() throws Exception {
        Account owner = register("tasks-deps");

        String blocker = apiPost("/api/tasks", owner, """
                {"title": "Write the spec"}
                """).get("id").asText();
        String blocked = apiPost("/api/tasks", owner, """
                {"title": "Implement from the spec"}
                """).get("id").asText();

        apiPost("/api/tasks/" + blocked + "/dependencies", owner, """
                {"taskId": "%s"}
                """.formatted(blocker));

JsonNode forward = apiGet("/api/tasks/" + blocked + "/dependencies", owner);
        assertThat(forward.isArray()).as("dependencies are a plain list of task ids").isTrue();
        assertThat(forward.toString()).contains(blocker);

        // The endpoint reports what a task depends on, so the blocker is not itself blocked.
        JsonNode reverse = apiGet("/api/tasks/" + blocker + "/dependencies", owner);
        assertThat(reverse.size()).as("the blocker has no dependencies of its own").isZero();

        // Removing must clear the link from either side of the relationship.
        apiDelete("/api/tasks/" + blocked + "/dependencies/" + blocker, owner);
        assertThat(apiGet("/api/tasks/" + blocked + "/dependencies", owner).toString())
                .doesNotContain(blocker);
    }

    @Test
    @DisplayName("refuses a dependency on a task that does not exist")
    void rejectsUnknownDependency() throws Exception {
        Account owner = register("tasks-deps-bad");

        String task = apiPost("/api/tasks", owner, """
                {"title": "Has a dependency"}
                """).get("id").asText();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/tasks/" + task + "/dependencies")
                .header("Authorization", owner.header())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""
                        {"taskId": "00000000-0000-0000-0000-000000000000"}
                        """))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("materialises a recurring task into dated instances")
    void materialisesRecurrence() throws Exception {
        Account owner = register("tasks-recurrence");

        String template = apiPost("/api/tasks", owner, """
                {
                  "title": "Weekly review",
                  "recurrenceRule": "FREQ=WEEKLY;BYDAY=MO",
                  "deadline": "2026-10-05T09:00:00Z",
                  "estimatedMinutes": 30
                }
                """).get("id").asText();

        JsonNode created = apiPost("/api/tasks/" + template + "/recurrence/materialise", owner, "{}");
        assertThat(created.path("created").asInt()).as("instances created on demand").isPositive();

        JsonNode list = apiGet("/api/tasks", owner);
        assertThat(list.get("totalElements").asInt()).as("template plus its instances").isGreaterThan(1);
    }

    @Test
    @DisplayName("keeps one account's tasks invisible and immutable to another")
    void isolatesTasksBetweenAccounts() throws Exception {
        Account owner = register("tasks-owner");
        Account intruder = register("tasks-intruder");

        String taskId = apiPost("/api/tasks", owner, """
                {"title": "Private task"}
                """).get("id").asText();

        mockMvc.perform(MockMvcRequestBuilders.get("/api/tasks/" + taskId).header("Authorization", intruder.header()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tasks").header("Authorization", intruder.header()))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/tasks/" + taskId).header("Authorization", intruder.header()))
                .andExpect(status().isNotFound());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/api/tasks/" + taskId).header("Authorization", intruder.header())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "Hijacked"}
                        """))
                .andExpect(status().isNotFound());

        assertThat(apiGet("/api/tasks/" + taskId, owner).get("title").asText()).isEqualTo("Private task");
    }

    // ------------------------------------------------------------------ goals

    @Test
    @DisplayName("creates a goal with nested milestones")
    void goalWithMilestones() throws Exception {
        Account owner = register("goals");

        JsonNode created = apiPost("/api/goals", owner, """
                {
                  "title": "Run a half marathon",
                  "description": "Train consistently through the winter",
                  "category": "health",
                  "goalType": "LONG_TERM",
                  "targetDate": "2027-03-01T00:00:00Z",
                  "priority": "HIGH",
                  "milestones": [
                    {"title": "Run 10k without stopping", "progress": 100, "completed": true},
                    {"title": "Run 15k without stopping", "progress": 40}
                  ]
                }
                """);
        assertThat(created.get("id").asText()).isNotBlank();
        assertThat(created.get("progress").asInt()).isZero();
        assertThat(created.get("status").asText()).isEqualTo("ACTIVE");
        String goalId = created.get("id").asText();

        JsonNode milestones = apiGet("/api/goals/" + goalId + "/milestones", owner);
        assertThat(milestones.isArray()).as("milestones are a plain list").isTrue();
        assertThat(milestones.size()).isEqualTo(2);

JsonNode active = apiGet("/api/goals/active", owner);
        assertThat(active.isArray()).as("active goals are a plain list").isTrue();
        assertThat(active.size()).isEqualTo(1);

        JsonNode progressed = apiPatch("/api/goals/" + goalId + "/progress", owner, """
                {"progress": 55}
                """);
        assertThat(progressed.get("progress").asInt()).isEqualTo(55);

        JsonNode archived = apiPatch("/api/goals/" + goalId + "/progress", owner, """
                {"status": "ACHIEVED"}
                """);
        assertThat(archived.get("status").asText()).isEqualTo("ACHIEVED");

        assertThat(apiGet("/api/goals/active", owner).size())
                .as("an achieved goal leaves the active list").isZero();
    }

    @Test
    @DisplayName("adds, updates and deletes a milestone independently")
    void milestoneLifecycle() throws Exception {
        Account owner = register("goals-milestones");

        String goalId = apiPost("/api/goals", owner, """
                {"title": "Learn to sail"}
                """).get("id").asText();

        String milestoneId = apiPost("/api/goals/" + goalId + "/milestones", owner, """
                {"title": "Complete the beginner course"}
                """).get("id").asText();

        JsonNode updated = apiPut("/api/goals/" + goalId + "/milestones/" + milestoneId, owner, """
                {"title": "Complete the beginner course", "progress": 70}
                """);
        assertThat(updated.get("progress").asInt()).isEqualTo(70);

        apiDelete("/api/goals/" + goalId + "/milestones/" + milestoneId, owner);
        assertThat(apiGet("/api/goals/" + goalId + "/milestones", owner).size()).isZero();
    }

    @Test
    @DisplayName("confirms an assistant-proposed goal")
    void confirmsProposedGoal() throws Exception {
        Account owner = register("goals-proposal");

        JsonNode confirmed = apiPost("/api/goals/confirm-proposal", owner, """
                {
                  "goal": {"title": "Proposed by the assistant", "category": "career"},
                  "milestones": [{"title": "First step"}]
                }
                """);
        assertThat(confirmed.get("id").asText()).isNotBlank();
        assertThat(apiGet("/api/goals/" + confirmed.get("id").asText() + "/milestones", owner).size())
                .isEqualTo(1);
    }

    @Test
    @DisplayName("links a task to a goal and refuses a goal from another account")
    void linksTasksToOwnedGoals() throws Exception {
        Account owner = register("goals-link");
        Account intruder = register("goals-link-intruder");

        String goalId = apiPost("/api/goals", owner, """
                {"title": "Owner goal"}
                """).get("id").asText();

        String intruderGoal = apiPost("/api/goals", intruder, """
                {"title": "Intruder goal"}
                """).get("id").asText();

        String taskId = apiPost("/api/tasks", owner, """
                {"title": "Linked task", "goalId": "%s"}
                """.formatted(goalId)).get("id").asText();
        assertThat(apiGet("/api/tasks/" + taskId, owner).get("goalId").asText()).isEqualTo(goalId);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/tasks")
                .header("Authorization", owner.header())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("""
                        {"title": "Should not attach", "goalId": "%s"}
                        """.formatted(intruderGoal)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("deletes a goal and hides it from later reads")
    void deletesGoal() throws Exception {
        Account owner = register("goals-delete");

        String goalId = apiPost("/api/goals", owner, """
                {"title": "Temporary goal"}
                """).get("id").asText();

        apiDelete("/api/goals/" + goalId, owner);
        mockMvc.perform(MockMvcRequestBuilders.get("/api/goals/" + goalId).header("Authorization", owner.header()))
                .andExpect(status().isNotFound());
    }
}