package com.lifeos.controller;

import com.lifeos.dto.LearningDtos;
import com.lifeos.entity.enums.LearningStatus;
import com.lifeos.security.CurrentUser;
import com.lifeos.service.LearningService;
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

/** Learning goals, topics, study sessions, resources and skills. */
@RestController
@RequestMapping("/api/learning")
public class LearningController {

    private final LearningService learningService;

    public LearningController(LearningService learningService) {
        this.learningService = learningService;
    }

    @GetMapping("/goals")
    public List<LearningDtos.LearningGoalResponse> goals(@RequestParam(required = false) LearningStatus status) {
        return learningService.goals(CurrentUser.id(), status);
    }

    @GetMapping("/goals/{goalId}")
    public LearningDtos.LearningGoalResponse get(@PathVariable String goalId) {
        return learningService.get(CurrentUser.id(), goalId);
    }

    @PostMapping("/goals")
    public ResponseEntity<LearningDtos.LearningGoalResponse> createGoal(
            @Valid @RequestBody LearningDtos.LearningGoalRequest request) {
        LearningDtos.LearningGoalResponse created = learningService.create(CurrentUser.id(), request);
        return ResponseEntity.created(UriComponentsBuilder.fromPath("/api/learning/goals/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PutMapping("/goals/{goalId}")
    public LearningDtos.LearningGoalResponse updateGoal(@PathVariable String goalId,
                                                        @Valid @RequestBody LearningDtos.LearningGoalRequest request) {
        return learningService.update(CurrentUser.id(), goalId, request);
    }

    @DeleteMapping("/goals/{goalId}")
    public ResponseEntity<Void> deleteGoal(@PathVariable String goalId) {
        learningService.deleteGoal(CurrentUser.id(), goalId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/goals/{goalId}/topics")
    public ResponseEntity<LearningDtos.TopicResponse> addTopic(
            @PathVariable String goalId, @Valid @RequestBody LearningDtos.TopicRequest request) {
        LearningDtos.TopicResponse created = learningService.addTopic(CurrentUser.id(), goalId, request);
        return ResponseEntity.created(UriComponentsBuilder.fromPath("/api/learning/topics/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PatchMapping("/topics/{topicId}/complete")
    public LearningDtos.TopicResponse completeTopic(@PathVariable String topicId,
                                                   @RequestParam(defaultValue = "true") boolean completed) {
        return learningService.completeTopic(CurrentUser.id(), topicId, completed);
    }

    @DeleteMapping("/topics/{topicId}")
    public ResponseEntity<Void> deleteTopic(@PathVariable String topicId) {
        learningService.deleteTopic(CurrentUser.id(), topicId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/sessions")
    public LearningDtos.SessionResponse recordSession(@Valid @RequestBody LearningDtos.SessionRequest request) {
        return learningService.recordSession(CurrentUser.id(), request);
    }

    @GetMapping("/sessions")
    public List<LearningDtos.SessionResponse> sessions(
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        return learningService.sessions(CurrentUser.id(), limit);
    }

    @GetMapping("/study")
    public List<LearningDtos.DailyStudyPoint> dailyStudy(
            @RequestParam(required = false, defaultValue = "30") Integer days) {
        return learningService.dailyStudy(CurrentUser.id(), days == null ? 30 : days);
    }

    @PostMapping("/resources")
    public ResponseEntity<LearningDtos.ResourceResponse> addResource(
            @Valid @RequestBody LearningDtos.ResourceRequest request) {
        LearningDtos.ResourceResponse created = learningService.addResource(CurrentUser.id(), request);
        return ResponseEntity.created(UriComponentsBuilder.fromPath("/api/learning/resources/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @GetMapping("/resources")
    public List<LearningDtos.ResourceResponse> resources(@RequestParam(required = false) String goalId) {
        return learningService.resources(CurrentUser.id(), goalId);
    }

    @DeleteMapping("/resources/{resourceId}")
    public ResponseEntity<Void> deleteResource(@PathVariable String resourceId) {
        learningService.deleteResource(CurrentUser.id(), resourceId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/skills")
    public ResponseEntity<LearningDtos.SkillResponse> addSkill(@Valid @RequestBody LearningDtos.SkillRequest request) {
        LearningDtos.SkillResponse created = learningService.addSkill(CurrentUser.id(), request);
        return ResponseEntity.created(UriComponentsBuilder.fromPath("/api/learning/skills/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @GetMapping("/skills")
    public List<LearningDtos.SkillResponse> skills() {
        return learningService.skills(CurrentUser.id());
    }

    @DeleteMapping("/skills/{skillId}")
    public ResponseEntity<Void> deleteSkill(@PathVariable String skillId) {
        learningService.deleteSkill(CurrentUser.id(), skillId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/statistics")
    public LearningDtos.LearningStats statistics() {
        return learningService.statistics(CurrentUser.id());
    }

    /** Persists an AI roadmap only once the user confirms it. */
    @PostMapping("/roadmaps/confirm")
    public LearningDtos.LearningGoalResponse confirmRoadmap(
            @Valid @RequestBody LearningDtos.ConfirmRoadmapRequest request) {
        return learningService.confirmRoadmap(CurrentUser.id(), request);
    }
}
