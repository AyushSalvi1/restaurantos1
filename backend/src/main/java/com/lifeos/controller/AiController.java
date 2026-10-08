package com.lifeos.controller;

import com.lifeos.ai.AiProviderRegistry;
import com.lifeos.common.PageResponse;
import com.lifeos.dto.AiDtos;
import com.lifeos.security.CurrentUser;
import com.lifeos.service.AiAssistantService;
import com.lifeos.service.DayPlanService;
import com.lifeos.service.RecommendationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Assistant chat, day planning, recommendations and provider status.
 *
 * <p>Provider status is exposed so the UI can state which engine answered. When credentials are
 * missing LIFEOS still replies, using the on-device composer, and says so.</p>
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiAssistantService assistantService;
    private final DayPlanService dayPlanService;
    private final RecommendationService recommendationService;
    private final AiProviderRegistry providerRegistry;

    public AiController(AiAssistantService assistantService,
                        DayPlanService dayPlanService,
                        RecommendationService recommendationService,
                        AiProviderRegistry providerRegistry) {
        this.assistantService = assistantService;
        this.dayPlanService = dayPlanService;
        this.recommendationService = recommendationService;
        this.providerRegistry = providerRegistry;
    }

    @PostMapping("/chat")
    public AiDtos.ChatResponse chat(@Valid @RequestBody AiDtos.ChatRequest request) {
        return assistantService.chat(CurrentUser.id(), request);
    }

    @GetMapping("/scopes")
    public List<String> scopes() {
        return assistantService.availableScopes();
    }

    @GetMapping("/conversations")
    public PageResponse<AiDtos.ConversationSummary> conversations(
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        return PageResponse.of(assistantService.conversations(CurrentUser.id(),
                page == null ? 0 : page, size == null ? 20 : size));
    }

    @GetMapping("/conversations/{conversationId}")
    public AiDtos.ConversationDetail conversation(@PathVariable String conversationId) {
        return assistantService.conversation(CurrentUser.id(), conversationId);
    }

    @PutMapping("/conversations/{conversationId}")
    public AiDtos.ConversationDetail rename(@PathVariable String conversationId,
                                            @RequestBody RenameRequest request) {
        return assistantService.rename(CurrentUser.id(), conversationId, request.title());
    }

    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Void> deleteConversation(@PathVariable String conversationId) {
        assistantService.deleteConversation(CurrentUser.id(), conversationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/plan-day")
    public AiDtos.PlanDayResponse planDay(@Valid @RequestBody AiDtos.PlanDayRequest request) {
        return dayPlanService.plan(CurrentUser.id(), request);
    }

    @PostMapping("/plan-day/apply")
    public AiDtos.PlanDayResponse applyPlan(@RequestBody AiDtos.PlanUpdateRequest request) {
        return dayPlanService.applyEdits(CurrentUser.id(), request);
    }

    @GetMapping("/recommendations")
    public AiDtos.RecommendationsResponse recommendations() {
        return recommendationService.recommend(CurrentUser.id());
    }

    @GetMapping("/providers")
    public AiDtos.ProviderStatus providers() {
        return providerRegistry.status();
    }

    public record RenameRequest(String title) {
    }
}
