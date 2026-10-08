package com.lifeos.controller;

import com.lifeos.common.PageResponse;
import com.lifeos.dto.KnowledgeDtos;
import com.lifeos.entity.enums.SavedItemType;
import com.lifeos.security.CurrentUser;
import com.lifeos.service.KnowledgeService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/** Uploaded documents, semantic search over them, and saved items. */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_TOP_K = 20;

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<KnowledgeDtos.DocumentResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String title) {
        KnowledgeDtos.DocumentResponse created = knowledgeService.upload(CurrentUser.id(), file, title);
        return ResponseEntity.created(UriComponentsBuilder.fromPath("/api/knowledge/documents/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @GetMapping("/documents")
    public KnowledgeDtos.DocumentListResponse list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "25") Integer size) {
        return knowledgeService.list(CurrentUser.id(), q, page == null ? 0 : page, size == null ? 25 : size);
    }

    @GetMapping("/documents/{documentId}")
    public KnowledgeDtos.DocumentResponse get(@PathVariable String documentId) {
        return knowledgeService.get(CurrentUser.id(), documentId);
    }

    @PostMapping("/documents/{documentId}/reprocess")
    public KnowledgeDtos.DocumentResponse reprocess(@PathVariable String documentId) {
        return knowledgeService.reprocess(CurrentUser.id(), documentId);
    }

    @DeleteMapping("/documents/{documentId}")
    public ResponseEntity<Void> delete(@PathVariable String documentId) {
        knowledgeService.delete(CurrentUser.id(), documentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    public KnowledgeDtos.SearchResponse search(
            @RequestParam String q,
            @RequestParam(required = false, defaultValue = "5") Integer topK) {
        return knowledgeService.searchResponse(CurrentUser.id(), q, clamp(topK));
    }

    /**
     * Answers strictly from the user's own uploaded documents. When nothing relevant is found the
     * answer says so rather than falling back to general knowledge. Supplying a conversation id
     * appends the exchange to that thread and returns the persisted ids.
     */
    @PostMapping("/ask")
    public KnowledgeDtos.AskResponse ask(@Valid @RequestBody KnowledgeDtos.AskRequest request) {
        return knowledgeService.ask(CurrentUser.id(), request.question(), request.conversationId(),
                clamp(request.topK()));
    }

    @PostMapping("/items")
    public KnowledgeDtos.SavedItemResponse saveItem(
            @Valid @RequestBody KnowledgeDtos.SavedItemRequest request) {
        return knowledgeService.saveItem(CurrentUser.id(), request);
    }

    @GetMapping("/items")
    public PageResponse<KnowledgeDtos.SavedItemResponse> items(
            @RequestParam(required = false) SavedItemType type,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "25") Integer size) {
        return PageResponse.of(knowledgeService.items(CurrentUser.id(), type, q,
                page == null ? 0 : page, size == null ? 25 : size));
    }

    @PutMapping("/items/{itemId}")
    public KnowledgeDtos.SavedItemResponse updateItem(
            @PathVariable String itemId, @Valid @RequestBody KnowledgeDtos.SavedItemRequest request) {
        return knowledgeService.updateItem(CurrentUser.id(), itemId, request);
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> deleteItem(@PathVariable String itemId) {
        knowledgeService.deleteItem(CurrentUser.id(), itemId);
        return ResponseEntity.noContent().build();
    }

    private int clamp(Integer topK) {
        if (topK == null) {
            return DEFAULT_TOP_K;
        }
        return Math.max(1, Math.min(MAX_TOP_K, topK));
    }
}
