package com.lifeos.controller;

import com.lifeos.common.PageResponse;
import com.lifeos.dto.GoalDtos;
import com.lifeos.entity.enums.GoalStatus;
import com.lifeos.entity.enums.Origin;
import com.lifeos.security.CurrentUser;
import com.lifeos.service.GoalService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

/** Goals, milestones, progress and confirmation of AI-proposed decompositions. */
@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping
    public PageResponse<GoalDtos.GoalResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) GoalStatus status,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "25") Integer size) {
        return PageResponse.of(goalService.search(CurrentUser.id(), q, status,
                page == null ? 0 : page, size == null ? 25 : size));
    }

    @GetMapping("/active")
    public List<GoalDtos.GoalSummary> active() {
        return goalService.summaries(CurrentUser.id());
    }

    @GetMapping("/{goalId}")
    public GoalDtos.GoalResponse get(@PathVariable String goalId) {
        return goalService.get(CurrentUser.id(), goalId);
    }

    @PostMapping
    public ResponseEntity<GoalDtos.GoalResponse> create(@Valid @RequestBody GoalDtos.GoalRequest request) {
        GoalDtos.GoalResponse created = goalService.create(CurrentUser.id(), request, Origin.USER, true);
        return ResponseEntity.created(UriComponentsBuilder.fromPath("/api/goals/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PutMapping("/{goalId}")
    public GoalDtos.GoalResponse update(@PathVariable String goalId,
                                        @Valid @RequestBody GoalDtos.GoalRequest request) {
        return goalService.update(CurrentUser.id(), goalId, request);
    }

    @PatchMapping("/{goalId}/progress")
    public GoalDtos.GoalResponse updateProgress(@PathVariable String goalId,
                                                @Valid @RequestBody GoalDtos.ProgressUpdateRequest request) {
        return goalService.updateProgress(CurrentUser.id(), goalId, request);
    }

    @DeleteMapping("/{goalId}")
    public ResponseEntity<Void> delete(@PathVariable String goalId) {
        goalService.delete(CurrentUser.id(), goalId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{goalId}/milestones")
    public List<GoalDtos.MilestoneResponse> milestones(@PathVariable String goalId) {
        return goalService.milestones(CurrentUser.id(), goalId);
    }

    @PostMapping("/{goalId}/milestones")
    public ResponseEntity<GoalDtos.MilestoneResponse> createMilestone(
            @PathVariable String goalId, @Valid @RequestBody GoalDtos.MilestoneRequest request) {
        GoalDtos.MilestoneResponse created = goalService.createMilestone(CurrentUser.id(), goalId, request);
        return ResponseEntity.created(UriComponentsBuilder
                        .fromPath("/api/goals/{goalId}/milestones/{id}")
                        .buildAndExpand(goalId, created.id()).toUri())
                .body(created);
    }

    @PutMapping("/{goalId}/milestones/{milestoneId}")
    public GoalDtos.MilestoneResponse updateMilestone(@PathVariable String goalId,
                                                      @PathVariable String milestoneId,
                                                      @Valid @RequestBody GoalDtos.MilestoneRequest request) {
        return goalService.updateMilestone(CurrentUser.id(), milestoneId, request);
    }

    @DeleteMapping("/{goalId}/milestones/{milestoneId}")
    public ResponseEntity<Void> deleteMilestone(@PathVariable String goalId, @PathVariable String milestoneId) {
        goalService.deleteMilestone(CurrentUser.id(), milestoneId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Persists an AI proposal only after the user confirms it. Nothing is written before this call,
     * so a proposal can always be discarded by simply not confirming it.
     */
    @PostMapping("/confirm-proposal")
    public GoalDtos.GoalResponse confirmProposal(@Valid @RequestBody GoalDtos.ConfirmProposalRequest request) {
        return goalService.confirmProposal(CurrentUser.id(), request);
    }
}
