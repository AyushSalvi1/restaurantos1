package com.lifeos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Shared request helpers for the functional suites.
 *
 * <p>Every suite registers its own accounts so tests never observe each other's records, and every call
 * goes through the real filter chain, so ownership, validation and error mapping are exercised rather than
 * bypassed. The helpers return the parsed body only; tests that care about the status code assert on it
 * through {@code mockMvc} directly.</p>
 */
@AutoConfigureMockMvc
public abstract class ApiTestSupport {

    protected static final String PASSWORD = "Correct-Horse-9!";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /** A registered account and its bearer token. */
    protected record Account(String userId, String token) {

        String header() {
            return "Bearer " + token;
        }
    }

    protected Account register(String prefix) throws Exception {
        String email = prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@lifeos.test";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "%s",
                                  "fullName": "%s Tester",
                                  "timezone": "UTC"
                                }
                                """.formatted(email, PASSWORD, prefix)))
                .andReturn();
        JsonNode body = read(result);
        return new Account(body.path("user").path("id").asText(), body.path("accessToken").asText());
    }

    protected JsonNode read(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return body == null || body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    protected JsonNode apiGet(String path, Account account, String... queryParams) throws Exception {
        var request = MockMvcRequestBuilders.get(path).header(HttpHeaders.AUTHORIZATION, account.header());
        for (int i = 0; i + 1 < queryParams.length; i += 2) {
            request = request.param(queryParams[i], queryParams[i + 1]);
        }
        return read(mockMvc.perform(request).andReturn());
    }

    protected JsonNode apiPost(String path, Account account, String json) throws Exception {
        return read(mockMvc.perform(post(path)
                .header(HttpHeaders.AUTHORIZATION, account.header())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)).andReturn());
    }

    protected JsonNode apiPut(String path, Account account, String json) throws Exception {
        return read(mockMvc.perform(MockMvcRequestBuilders.put(path)
                .header(HttpHeaders.AUTHORIZATION, account.header())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)).andReturn());
    }

    protected JsonNode apiPatch(String path, Account account, String json) throws Exception {
        return read(mockMvc.perform(MockMvcRequestBuilders.patch(path)
                .header(HttpHeaders.AUTHORIZATION, account.header())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json)).andReturn());
    }

    protected JsonNode apiDelete(String path, Account account) throws Exception {
        return read(mockMvc.perform(MockMvcRequestBuilders.delete(path)
                .header(HttpHeaders.AUTHORIZATION, account.header())).andReturn());
    }
}