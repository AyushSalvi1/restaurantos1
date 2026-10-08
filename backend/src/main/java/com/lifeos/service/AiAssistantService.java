package com.lifeos.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeos.ai.AIProvider;
import com.lifeos.ai.AiProviderRegistry;
import com.lifeos.ai.AiRequest;
import com.lifeos.ai.AiResponse;
import com.lifeos.ai.Prompts;
import com.lifeos.dto.AiDtos;
import com.lifeos.dto.KnowledgeDtos;
import com.lifeos.entity.AiConversation;
import com.lifeos.entity.AiMessage;
import com.lifeos.entity.enums.MessageRole;
import com.lifeos.exception.AppException;
import com.lifeos.rag.RagService;
import com.lifeos.repository.AiConversationRepository;
import com.lifeos.repository.AiMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * The LIFEOS assistant.
 *
 * <p>Two grounding paths exist and the response states which one was used. When the question is about
 * uploaded documents the answer comes from {@link RagService} and carries citations; otherwise it is
 * answered from a live context snapshot of the user's own records, and the scopes that contributed
 * are returned in {@code contextUsed}. If nothing relevant has been recorded the assistant says so
 * instead of filling the gap with general model knowledge.</p>
 */
@Service
public class AiAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantService.class);
    private static final int DEFAULT_HISTORY = 12;
    private static final int MAX_HISTORY = 40;
    private static final String KNOWLEDGE_SCOPE = "knowledge";
    private static final int RAG_TOP_K = 5;

    private final AiConversationRepository conversationRepository;
    private final AiMessageRepository messageRepository;
    private final AiProviderRegistry providerRegistry;
    private final RagService ragService;
    private final AiContextService contextService;
    private final ObjectMapper objectMapper;

    public AiAssistantService(AiConversationRepository conversationRepository,
                              AiMessageRepository messageRepository,
                              AiProviderRegistry providerRegistry,
                              RagService ragService,
                              AiContextService contextService,
                              ObjectMapper objectMapper) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.providerRegistry = providerRegistry;
        this.ragService = ragService;
        this.contextService = contextService;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------- chat

    @Transactional
    public AiDtos.ChatResponse chat(String userId, AiDtos.ChatRequest request) {
        AiConversation conversation = resolveConversation(userId, request.conversationId());

        List<String> scopes = request.contextScopes() == null || request.contextScopes().isEmpty()
                ? availableScopes()
                : request.contextScopes().stream().map(String::trim).map(String::toLowerCase).toList();

        persistMessage(userId, conversation, MessageRole.USER, request.message(), null, null, List.of());

        if (scopes.contains(KNOWLEDGE_SCOPE) && contextService.looksLikeDocumentQuestion(request.message())) {
            RagService.GroundedAnswer grounded = ragService.answer(userId, request.message(), RAG_TOP_K);
            persistMessage(userId, conversation, MessageRole.ASSISTANT, grounded.text(),
                    grounded.provider(), grounded.model(), grounded.citations());
            conversationRepository.save(bump(conversation));
            return new AiDtos.ChatResponse(conversation.getId(), lastMessageId(conversation.getId()),
                    grounded.text(), List.of(KNOWLEDGE_SCOPE), grounded.provider(), grounded.model(),
                    grounded.grounded(), grounded.citations(), Instant.now());
        }

        AiContextService.ContextSnapshot snapshot = contextService.build(userId, scopes);
        String context = Prompts.contextBlock("YOUR RECORDED DATA", snapshot.lines());
        List<com.lifeos.ai.AiMessage> history = historyFor(conversation.getId(),
                request.historyLimit() == null ? DEFAULT_HISTORY : request.historyLimit());
        history.add(com.lifeos.ai.AiMessage.user(context + "\nQUESTION: " + request.message()));

        AIProvider provider = providerRegistry.active();
        AiResponse response = provider.complete(AiRequest.of(withSystem(history)));
        String reply = response.text() == null || response.text().isBlank()
                ? "Not enough data yet. Record a task, habit or goal and then ask again."
                : response.text();

        persistMessage(userId, conversation, MessageRole.ASSISTANT, reply, response.provider(), response.model(), List.of());
        conversationRepository.save(bump(conversation));
        return new AiDtos.ChatResponse(conversation.getId(), lastMessageId(conversation.getId()), reply,
                snapshot.scopes(), response.provider(), response.model(), snapshot.nonEmpty(),
                List.of(), Instant.now());
    }

    // --------------------------------------------------------- conversations

    /**
     * Answers strictly from the user's uploaded documents, bypassing the scope heuristic used by
     * {@link #chat} so the caller gets document-grounded text or an explicit refusal.
     *
     * <p>Passing a conversation id appends the exchange to that thread and returns the persisted ids.
     * Omitting it keeps the call a one-shot lookup: nothing is written, so the returned ids are null
     * rather than pointing at a conversation that does not exist.</p>
     */
    @Transactional
    public KnowledgeDtos.AskResponse askGrounded(String userId, String question, String conversationId, int topK) {
        RagService.GroundedAnswer grounded = ragService.answer(userId, question, topK);
        if (conversationId == null || conversationId.isBlank()) {
            return new KnowledgeDtos.AskResponse(grounded.text(), grounded.citations(), grounded.grounded(),
                    grounded.provider(), grounded.model(), null, null);
        }
        AiConversation conversation = resolveConversation(userId, conversationId);
        persistMessage(userId, conversation, MessageRole.USER, question, null, null, List.of());
        persistMessage(userId, conversation, MessageRole.ASSISTANT, grounded.text(), grounded.provider(),
                grounded.model(), grounded.citations());
        conversationRepository.save(bump(conversation));
        return new KnowledgeDtos.AskResponse(grounded.text(), grounded.citations(), grounded.grounded(),
                grounded.provider(), grounded.model(), conversation.getId(), lastMessageId(conversation.getId()));
    }

    @Transactional(readOnly = true)
    public Page<AiDtos.ConversationSummary> conversations(String userId, int page, int size) {
        return conversationRepository.findByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(
                        userId, PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))))
                .map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public AiDtos.ConversationDetail conversation(String userId, String conversationId) {
        AiConversation conversation = conversationRepository
                .findByIdAndUserIdAndDeletedAtIsNull(conversationId, userId)
                .orElseThrow(() -> AppException.notFound("Conversation not found"));
        return new AiDtos.ConversationDetail(toSummary(conversation), messagesOf(conversationId));
    }

    @Transactional
    public void deleteConversation(String userId, String conversationId) {
        AiConversation conversation = conversationRepository
                .findByIdAndUserIdAndDeletedAtIsNull(conversationId, userId)
                .orElseThrow(() -> AppException.notFound("Conversation not found"));
        messageRepository.deleteByConversationId(conversationId);
        conversation.setDeletedAt(Instant.now());
        conversationRepository.save(conversation);
    }

    @Transactional
    public AiDtos.ConversationDetail rename(String userId, String conversationId, String title) {
        if (title == null || title.isBlank()) {
            throw AppException.badRequest("A title is required");
        }
        AiConversation conversation = conversationRepository
                .findByIdAndUserIdAndDeletedAtIsNull(conversationId, userId)
                .orElseThrow(() -> AppException.notFound("Conversation not found"));
        String cleaned = title.strip();
        conversation.setTitle(cleaned.substring(0, Math.min(200, cleaned.length())));
        return new AiDtos.ConversationDetail(toSummary(conversationRepository.save(conversation)),
                messagesOf(conversationId));
    }

    /** Scopes the assistant can ground an answer in, so the UI can offer them explicitly. */
    public List<String> availableScopes() {
        return List.of(KNOWLEDGE_SCOPE, "tasks", "goals", "habits", "focus", "learning",
                "finance", "journal", "calendar");
    }

    // -------------------------------------------------------------- internals

    private AiConversation resolveConversation(String userId, String conversationId) {
        if (conversationId != null && !conversationId.isBlank()) {
            return conversationRepository.findByIdAndUserIdAndDeletedAtIsNull(conversationId, userId)
                    .orElseThrow(() -> AppException.notFound("Conversation not found"));
        }
        AiConversation conversation = new AiConversation();
        conversation.setUserId(userId);
        conversation.setTitle("New conversation");
        conversation.setMessageCount(0);
        return conversationRepository.save(conversation);
    }

    private AiConversation bump(AiConversation conversation) {
        conversation.setMessageCount(conversation.getMessageCount() + 2);
        conversation.setLastMessageAt(Instant.now());
        return conversation;
    }

    /** Renames an untitled conversation from its first user message so the list is readable. */
    private void titleFromFirstMessage(AiConversation conversation, String message) {
        if (!"New conversation".equals(conversation.getTitle()) || message == null) {
            return;
        }
        String cleaned = message.strip().replaceAll("\\s+", " ");
        conversation.setTitle(cleaned.length() <= 60 ? cleaned : cleaned.substring(0, 60) + "...");
    }

    private List<AiDtos.MessageResponse> messagesOf(String conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId)
                .stream().map(this::toMessage).toList();
    }

    private void persistMessage(String userId, AiConversation conversation, MessageRole role, String content,
                                String provider, String model, List<KnowledgeDtos.SearchHit> citations) {
        titleFromFirstMessage(conversation, role == MessageRole.USER ? content : null);
        AiMessage message = new AiMessage();
        message.setConversationId(conversation.getId());
        message.setUserId(userId);
        message.setRole(role);
        message.setContent(content);
        message.setProvider(provider);
        message.setModel(model);
        message.setPromptTokens(0);
        message.setCompletionTokens(content == null ? 0 : (int) Math.ceil(content.length() / 4.0));
        message.setCitations(citations.isEmpty() ? null : writeCitations(citations));
        messageRepository.save(message);
    }

    private String lastMessageId(String conversationId) {
        List<AiMessage> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        return messages.isEmpty() ? null : messages.get(messages.size() - 1).getId();
    }

    private List<com.lifeos.ai.AiMessage> historyFor(String conversationId, int limit) {
        List<AiMessage> stored = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        int capped = Math.min(MAX_HISTORY, Math.max(0, limit));
        List<AiMessage> window = stored.size() > capped && capped > 0
                ? stored.subList(stored.size() - capped, stored.size())
                : stored;
        List<com.lifeos.ai.AiMessage> history = new ArrayList<>(window.size());
        for (AiMessage message : window) {
            history.add(message.getRole() == MessageRole.ASSISTANT
                    ? com.lifeos.ai.AiMessage.assistant(message.getContent())
                    : com.lifeos.ai.AiMessage.user(message.getContent()));
        }
        return history;
    }

    private List<com.lifeos.ai.AiMessage> withSystem(List<com.lifeos.ai.AiMessage> history) {
        List<com.lifeos.ai.AiMessage> all = new ArrayList<>(history.size() + 1);
        all.add(com.lifeos.ai.AiMessage.system(Prompts.ASSISTANT_SYSTEM));
        all.addAll(history);
        return all;
    }

    private String writeCitations(List<KnowledgeDtos.SearchHit> citations) {
        try {
            return objectMapper.writeValueAsString(citations);
        } catch (Exception ex) {
            log.warn("Could not store citations for a message");
            return null;
        }
    }

    private List<KnowledgeDtos.SearchHit> readCitations(String citations) {
        if (citations == null || citations.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(citations, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    public AiDtos.ConversationSummary toSummary(AiConversation conversation) {
        return new AiDtos.ConversationSummary(conversation.getId(), conversation.getTitle(),
                conversation.getMessageCount(), conversation.getLastMessageAt(), conversation.getCreatedAt());
    }

    public AiDtos.MessageResponse toMessage(AiMessage message) {
        return new AiDtos.MessageResponse(
                message.getId(),
                message.getRole().name(),
                message.getContent(),
                message.getProvider(),
                message.getModel(),
                readCitations(message.getCitations()),
                message.getCreatedAt());
    }
}