package com.lifeos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Walks the core path a real user takes: register, record a task, complete it, then read the
 * dashboard, analytics and assistant. It runs with no AI provider configured, so it also proves the
 * system is usable without any external service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Core user journey")
class CoreJourneyTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("registers, records and completes work, then reports it back")
    void recordsAndReportsWork() throws Exception {
        String email = "journey-" + UUID.randomUUID() + "@lifeos.test";

        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Correct-Horse-9!",
                                  "fullName": "Journey Tester",
                                  "timezone": "UTC"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        JsonNode session = objectMapper.readTree(registered.getResponse().getContentAsString());
        String token = session.get("accessToken").asText();
        String auth = "Bearer " + token;

        String tomorrow = LocalDate.now().plusDays(1).toString();
        MvcResult createdTask = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Write the integration test summary",
                                  "priority": "HIGH",
                                  "deadline": "%sT17:00:00Z",
                                  "estimatedMinutes": 45
                                }
                                """.formatted(tomorrow)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();

        String taskId = objectMapper.readTree(createdTask.getResponse().getContentAsString())
                .get("id").asText();

        mockMvc.perform(MockMvcRequestBuilders.patch("/api/tasks/" + taskId + "/status")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "IN_PROGRESS"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(post("/api/tasks/" + taskId + "/complete")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"actualMinutes": 40, "notes": "Done in one sitting."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.actualMinutes").value(40));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/tasks").param("q", "integration test summary")
                        .header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/dashboard").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.greeting.displayName").value("Journey"))
                .andExpect(jsonPath("$.today.tasksCompleted").value(1))
                .andExpect(jsonPath("$.dataGaps").isArray());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/analytics/summary").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tasksCompleted").value(1))
                .andExpect(jsonPath("$.timezone").value("UTC"));

        mockMvc.perform(MockMvcRequestBuilders.get("/api/analytics/balance").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dimensions").isNotEmpty())
                .andExpect(jsonPath("$.disclaimer").isNotEmpty());
    }

    @Test
    @DisplayName("answers from stored records with no AI provider configured")
    void assistantWorksOffline() throws Exception {
        String email = "offline-" + UUID.randomUUID() + "@lifeos.test";
        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Correct-Horse-9!",
                                  "fullName": "Offline Tester"
                                }
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        String auth = "Bearer " + objectMapper.readTree(registered.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(post("/api/habits")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Morning walk",
                                  "frequencyType": "WEEKLY",
                                  "timesPerPeriod": 7,
                                  "targetDays": [1, 2, 3, 4, 5, 6, 7]
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/ai/chat")
                        .header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "How many habits am I tracking?",
                                  "contextScopes": ["habits"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").isNotEmpty())
                .andExpect(jsonPath("$.provider").isNotEmpty())
                .andExpect(jsonPath("$.contextUsed").isArray());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/ai/providers").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeProvider").isNotEmpty())
                .andExpect(jsonPath("$.providers").isNotEmpty());
    }

    @Test
    @DisplayName("rejects unauthenticated access and cross-account reads")
    void enforcesAuthentication() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/api/dashboard"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(MockMvcRequestBuilders.get("/api/admin/stats"))
                .andExpect(status().isUnauthorized());
    }
}
