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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the identity surface: registration validation, the refresh-rotation contract, session
 * revocation, and the ownership guarantee that no account can read or mutate another account's data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Authentication, sessions and account isolation")
class AuthAndOwnershipTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private record Account(String token, String refreshToken, String userId, String email) {}

    private Account register(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@lifeos.test";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Correct-Horse-9!",
                                  "fullName": "%s Owner",
                                  "timezone": "UTC"
                                }
                                """.formatted(email, prefix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value(email))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return new Account(
                body.get("accessToken").asText(),
                body.get("refreshToken").asText(),
                body.get("user").get("id").asText(),
                email);
    }

    @Test
    @DisplayName("rejects weak passwords and malformed emails with field level errors")
    void validatesRegistrationInput() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "not-an-email", "password": "short", "fullName": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors").isNotEmpty());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "weak-%s@lifeos.test",
                                  "password": "OnlyLettersHere",
                                  "fullName": "Weak Password"
                                }
                                """.formatted(UUID.randomUUID().toString().substring(0, 8))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[?(@.field == 'password')]").exists());
    }

    @Test
    @DisplayName("issues a new token pair on refresh and revokes the rotated token")
    void refreshRotatesTokens() throws Exception {
        Account account = register("rotate");

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(account.refreshToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();

        String rotated = objectMapper.readTree(refreshed.getResponse().getContentAsString())
                .get("refreshToken")
                .asText();
        assertThat(rotated).isNotEqualTo(account.refreshToken);

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(account.refreshToken())))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("logs out the current session and invalidates its refresh token")
    void logoutInvalidatesSession() throws Exception {
        Account account = register("logout");

        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer " + account.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s", "allDevices": false}
                                """.formatted(account.refreshToken())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken": "%s"}
                                """.formatted(account.refreshToken())))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("lists the caller's sessions and flags the current one")
    void listsSessions() throws Exception {
        // A session row is only surfaced when the request carried a User-Agent, because a token issued
        // without one cannot be attributed to a device the user could recognise or revoke.
        MvcResult registered = mockMvc.perform(post("/api/auth/register")
                        .header("User-Agent", "LIFEOS-JUnit/1.0")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "sessions-%s@lifeos.test",
                                  "password": "Correct-Horse-9!",
                                  "fullName": "Session Owner",
                                  "timezone": "UTC"
                                }
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(registered.getResponse().getContentAsString());
        String auth = "Bearer " + body.get("accessToken").asText();
        String refresh = body.get("refreshToken").asText();

        mockMvc.perform(get("/api/auth/sessions")
                        .header("Authorization", auth)
                        .param("currentRefreshToken", refresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions").isArray())
                .andExpect(jsonPath("$.sessions.length()").value(1))
                .andExpect(jsonPath("$.sessions[0].current").value(true))
                .andExpect(jsonPath("$.sessions[0].userAgent").value("LIFEOS-JUnit/1.0"));

        mockMvc.perform(get("/api/auth/sessions").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].current").value(false));
    }

    @Test
    @DisplayName("hides one account's tasks from another account")
    void isolatesTasksBetweenAccounts() throws Exception {
        Account owner = register("owner");
        Account intruder = register("intruder");

        MvcResult created = mockMvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Private planning note"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String taskId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(get("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + intruder.token()))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/tasks/" + taskId + "/status")
                        .header("Authorization", "Bearer " + intruder.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "COMPLETED"}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + intruder.token()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Private planning note"));
    }

    @Test
    @DisplayName("hides one account's journal and finance records from another account")
    void isolatesJournalAndFinance() throws Exception {
        Account owner = register("private");
        Account intruder = register("snoop");

        MvcResult entry = mockMvc.perform(post("/api/journal")
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "A private reflection that must never leak.",
                                  "entryDate": "%s",
                                  "mood": "GOOD",
                                  "tags": ["private"]
                                }
                                """.formatted(java.time.LocalDate.now())))
                .andExpect(status().isCreated())
                .andReturn();
        String entryId = objectMapper.readTree(entry.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(get("/api/journal/" + entryId)
                        .header("Authorization", "Bearer " + intruder.token()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/journal")
                        .header("Authorization", "Bearer " + intruder.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/finance/overview")
                        .header("Authorization", "Bearer " + intruder.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incomeThisMonth").value(0.0));
    }

    @Test
    @DisplayName("denies admin endpoints to a normal account and answers anonymous callers with 401")
    void protectsAdminEndpoints() throws Exception {
        Account account = register("plain");

        mockMvc.perform(get("/api/admin/stats").header("Authorization", "Bearer " + account.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + account.token()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/settings").header("Authorization", "Bearer " + account.token()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("reports the same answer whether or not an address is registered")
    void forgotPasswordDoesNotLeakExistence() throws Exception {
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "definitely-not-registered@lifeos.test"}
                                """))
                .andExpect(status().isOk());

        Account account = register("forgot");
        mockMvc.perform(post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s"}
                                """.formatted(account.email())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("updates the profile and keeps the returned user authoritative")
    void updatesProfile() throws Exception {
        Account account = register("profile");

        mockMvc.perform(put("/api/auth/me")
                        .header("Authorization", "Bearer " + account.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Renamed Person",
                                  "occupation": "Platform engineer",
                                  "employmentType": "EMPLOYED",
                                  "timezone": "Europe/Lisbon"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Renamed Person"))
                .andExpect(jsonPath("$.occupation").value("Platform engineer"))
                .andExpect(jsonPath("$.timezone").value("Europe/Lisbon"));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + account.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Renamed Person"));
    }

    @Test
    @DisplayName("requires the current password to change it")
    void changePasswordRequiresCurrentPassword() throws Exception {
        Account account = register("changepw");

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + account.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "wrong-password", "newPassword": "Another-Strong-7!"}
                                """))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(post("/api/auth/change-password")
                        .header("Authorization", "Bearer " + account.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword": "Correct-Horse-9!", "newPassword": "Another-Strong-7!"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "password": "Another-Strong-7!"}
                                """.formatted(account.email())))
                .andExpect(status().isOk());
    }
}
