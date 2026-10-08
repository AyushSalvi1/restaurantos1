package com.lifeos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the retrieval path with no remote AI provider configured: a document is uploaded, indexed
 * with the local embedding implementation, retrieved, and used to answer a question with citations.
 * This is the guarantee that LIFEOS works fully offline and that it never fabricates a citation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Knowledge ingestion and grounded retrieval")
class KnowledgeRagTests {

    private static final String NOTE = """
            Sleep hygiene notes

            Consistent wake time matters more than total hours. Anchor the wake time first, then let
            sleep onset follow. Caffeine after midday shortens deep sleep even when total time is stable.
            A cool dark room around eighteen degrees helps most people fall asleep faster.
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String register(String prefix) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s-%s@lifeos.test",
                                  "password": "Correct-Horse-9!",
                                  "fullName": "%s Reader",
                                  "timezone": "UTC"
                                }
                                """.formatted(prefix, UUID.randomUUID(), prefix)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken")
                .asText();
    }

    private String uploadText(String auth, String filename, String title, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", filename, "text/plain", content.getBytes(StandardCharsets.UTF_8));
        MvcResult result = mockMvc.perform(multipart("/api/knowledge").file(file)
                        .header("Authorization", "Bearer " + auth)
                        .param("title", title)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.chunkCount").isNumber())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();
    }

    /**
     * Waits for the asynchronous ingestion triggered by an upload to finish.
     *
     * <p>Ingestion is dispatched as an application event after the upload transaction commits and runs on
     * the async executor, so a freshly uploaded document is legitimately PENDING with no chunks. Polling
     * observes the completed state without asserting a timing detail.</p>
     */
    private JsonNode awaitIndexed(String auth, String documentId) throws Exception {
        for (int attempt = 0; attempt < 120; attempt++) {
            MvcResult result = mockMvc.perform(get("/api/knowledge/documents/" + documentId)
                            .header("Authorization", "Bearer " + auth)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode document = objectMapper.readTree(result.getResponse().getContentAsString());
            String state = document.get("status").asText();
            if ("READY".equals(state)) {
                return document;
            }
            if ("FAILED".equals(state)) {
                fail("Ingestion failed: " + document.get("failureReason").asText());
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException("Document " + documentId + " was not indexed in time");
    }

    @Test
    @DisplayName("indexes an uploaded document and returns it in the library")
    void indexesUploadedDocument() throws Exception {
        String auth = register("knowledge");

        MvcResult created = mockMvc.perform(multipart("/api/knowledge")
                        .file(new MockMultipartFile(
                                "file", "sleep.txt", "text/plain", NOTE.getBytes(StandardCharsets.UTF_8)))
                        .header("Authorization", "Bearer " + auth)
                        .param("title", "Sleep hygiene")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.filename").value("sleep.txt"))
                .andExpect(jsonPath("$.extension").value("txt"))
                .andExpect(jsonPath("$.sizeBytes").isNumber())
                .andReturn();

        JsonNode accepted = objectMapper.readTree(created.getResponse().getContentAsString());
        String documentId = accepted.get("id").asText();
        // The response reflects the accepted upload, before the background ingestion has run.
        assertThat(accepted.get("status").asText()).isIn("PENDING", "PROCESSING", "READY");

        JsonNode indexed = awaitIndexed(auth, documentId);
        assertThat(indexed.get("chunkCount").asInt()).isPositive();
        assertThat(indexed.get("wordCount").asInt()).isPositive();
        assertThat(indexed.get("title").asText()).isEqualTo("Sleep hygiene");

        mockMvc.perform(get("/api/knowledge/documents").header("Authorization", "Bearer " + auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documents").isArray())
                .andExpect(jsonPath("$.totalDocuments").value(1))
                .andExpect(jsonPath("$.totalChunks").isNumber())
                .andExpect(jsonPath("$.documents[0].title").value("Sleep hygiene"));
    }

    @Test
    @DisplayName("rejects unsupported file types instead of storing them")
    void rejectsUnsupportedTypes() throws Exception {
        String auth = register("knowledge-bad");

        mockMvc.perform(multipart("/api/knowledge")
                        .file(new MockMultipartFile(
                                "file", "payload.exe", "application/octet-stream", new byte[] {1, 2, 3, 4}))
                        .header("Authorization", "Bearer " + auth)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/knowledge/documents").header("Authorization", "Bearer " + auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDocuments").value(0));
    }

    @Test
    @DisplayName("retrieves relevant chunks for a question and cites them")
    void retrievesAndCitesChunks() throws Exception {
        String auth = register("rag");
        String documentId = uploadText(auth, "sleep.txt", "Sleep hygiene", NOTE);
        awaitIndexed(auth, documentId);

        MvcResult search = mockMvc.perform(get("/api/knowledge/search")
                        .header("Authorization", "Bearer " + auth)
                        .param("q", "caffeine and deep sleep")
                        .param("topK", "3")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query").value("caffeine and deep sleep"))
                .andExpect(jsonPath("$.hits").isArray())
                .andExpect(jsonPath("$.totalHits").isNumber())
                .andReturn();

        var hits = objectMapper.readTree(search.getResponse().getContentAsString()).get("hits");
        assertThat(hits.isArray()).isTrue();
        assertThat(hits.size()).isPositive();
        assertThat(hits.get(0).get("snippet").asText()).isNotBlank();
        assertThat(hits.get(0).get("documentTitle").asText()).isEqualTo("Sleep hygiene");
        assertThat(hits.get(0).get("score").asDouble()).isGreaterThanOrEqualTo(0.0);

mockMvc.perform(post("/api/knowledge/ask")
                        .header("Authorization", "Bearer " + auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "What matters more, wake time or total hours?", "topK": 3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").isNotEmpty())
                .andExpect(jsonPath("$.citations").isArray())
                .andExpect(jsonPath("$.citations.length()").isNumber())
                .andExpect(jsonPath("$.provider").isNotEmpty())
                .andExpect(jsonPath("$.grounded").value(true))
                // No conversation was requested, so nothing was persisted and no id is invented.
                .andExpect(jsonPath("$.conversationId").doesNotExist())
                .andExpect(jsonPath("$.messageId").doesNotExist());
    }

    @Test
    @DisplayName("continues a conversation when the caller supplies one")
    void appendsGroundedAnswerToSuppliedConversation() throws Exception {
        String auth = register("rag-thread");
        String documentId = uploadText(auth, "sleep.txt", "Sleep hygiene", NOTE);
        awaitIndexed(auth, documentId);

        // Conversations are created by the first chat turn, which is the only entry point that opens one.
        String conversationId = objectMapper.readTree(mockMvc.perform(
                        post("/api/ai/chat")
                                .header("Authorization", "Bearer " + auth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"message": "What do my sleep notes say about wake time?"}
                                        """))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .get("conversationId").asText();

        String answer = objectMapper.readTree(mockMvc.perform(post("/api/knowledge/ask")
                        .header("Authorization", "Bearer " + auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "What matters more, wake time or total hours?",
                                 "topK": 3, "conversationId": "%s"}
                                """.formatted(conversationId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grounded").value(true))
                .andExpect(jsonPath("$.conversationId").value(conversationId))
                .andExpect(jsonPath("$.messageId").isNotEmpty())
                .andReturn().getResponse().getContentAsString())
                .get("answer").asText();

        mockMvc.perform(get("/api/ai/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messages.length()").value(4))
                .andExpect(jsonPath("$.messages[2].role").value("USER"))
                .andExpect(jsonPath("$.messages[2].content")
                        .value("What matters more, wake time or total hours?"))
                .andExpect(jsonPath("$.messages[3].role").value("ASSISTANT"))
                .andExpect(jsonPath("$.messages[3].content").value(answer))
                .andExpect(jsonPath("$.messages[3].citations").isNotEmpty());
    }

    @Test
    @DisplayName("rejects a conversation owned by another account")
    void rejectsForeignConversation() throws Exception {
        String owner = register("rag-owner");
        String intruder = register("rag-intruder");

        String conversationId = objectMapper.readTree(mockMvc.perform(
                        post("/api/ai/chat")
                                .header("Authorization", "Bearer " + owner)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"message": "What should I focus on this week?"}
                                        """))
                        .andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .get("conversationId").asText();

        mockMvc.perform(post("/api/knowledge/ask")
                        .header("Authorization", "Bearer " + intruder)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "Anything about my notes?", "conversationId": "%s"}
                                """.formatted(conversationId)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("reports insufficient grounding instead of inventing an answer")
    void refusesToAnswerWithoutEvidence() throws Exception {
        String auth = register("rag-empty");

        mockMvc.perform(post("/api/knowledge/ask")
                        .header("Authorization", "Bearer " + auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "What is the recommended tyre pressure?", "topK": 3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grounded").value(false))
                .andExpect(jsonPath("$.citations").isEmpty())
                .andExpect(jsonPath("$.answer").isNotEmpty())
                .andExpect(jsonPath("$.provider").isNotEmpty())
                .andExpect(jsonPath("$.model").isNotEmpty());
    }

    @Test
    @DisplayName("does not expose one account's documents to another account")
    void isolatesDocumentsBetweenAccounts() throws Exception {
        String owner = register("doc-owner");
        String intruder = register("doc-intruder");
        String documentId = uploadText(owner, "private.txt", "Private notes", NOTE);

        mockMvc.perform(get("/api/knowledge/documents/" + documentId)
                        .header("Authorization", "Bearer " + intruder))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/knowledge/documents").header("Authorization", "Bearer " + intruder))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDocuments").value(0));

        mockMvc.perform(delete("/api/knowledge/documents/" + documentId)
                        .header("Authorization", "Bearer " + intruder))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/knowledge/documents/" + documentId).header("Authorization", "Bearer " + owner))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("keeps saved items private and validates their urls")
    void managesSavedItems() throws Exception {
        String auth = register("saved-items");

        MvcResult created = mockMvc.perform(post("/api/knowledge/items")
                        .header("Authorization", "Bearer " + auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Primary source",
                                  "url": "https://example.org/article",
                                  "itemType": "URL",
                                  "tags": ["research"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://example.org/article"))
                .andExpect(jsonPath("$.itemType").value("URL"))
                .andReturn();
        String itemId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id")
                .asText();

        mockMvc.perform(post("/api/knowledge/items")
                        .header("Authorization", "Bearer " + auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title": "Broken", "url": "not-a-url", "itemType": "URL"}
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/knowledge/items").header("Authorization", "Bearer " + auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(delete("/api/knowledge/items/" + itemId).header("Authorization", "Bearer " + auth))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/knowledge/items").header("Authorization", "Bearer " + auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("reports provider degradation when no remote model is configured")
    void reportsProviderStatus() throws Exception {
        String auth = register("providers");

        mockMvc.perform(get("/api/ai/providers").header("Authorization", "Bearer " + auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeProvider").isNotEmpty())
                .andExpect(jsonPath("$.remoteProviderConfigured").value(false))
                .andExpect(jsonPath("$.note").isNotEmpty())
                .andExpect(jsonPath("$.providers").isNotEmpty());
    }
}
