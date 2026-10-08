package com.lifeos.controller;

import com.lifeos.dto.HabitDtos;
import com.lifeos.security.CurrentUser;
import com.lifeos.service.HabitService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
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
import java.time.LocalDate;
import java.util.List;

/** Habits, their check-ins, streaks and trends. */
@RestController
@RequestMapping("/api/habits")
public class HabitController {

    private final HabitService habitService;

    public HabitController(HabitService habitService) {
        this.habitService = habitService;
    }

    @GetMapping
    public List<HabitDtos.HabitResponse> list(
            @RequestParam(required = false, defaultValue = "false") boolean includeArchived) {
        return habitService.list(CurrentUser.id(), includeArchived);
    }

    @GetMapping("/statistics")
    public List<HabitDtos.HabitStats> statistics() {
        return habitService.statistics(CurrentUser.id());
    }

    @GetMapping("/{habitId}")
    public HabitDtos.HabitResponse get(@PathVariable String habitId) {
        return habitService.get(CurrentUser.id(), habitId);
    }

    @PostMapping
    public ResponseEntity<HabitDtos.HabitResponse> create(@Valid @RequestBody HabitDtos.HabitRequest request) {
        HabitDtos.HabitResponse created = habitService.create(CurrentUser.id(), request);
        return ResponseEntity.created(UriComponentsBuilder.fromPath("/api/habits/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PutMapping("/{habitId}")
    public HabitDtos.HabitResponse update(@PathVariable String habitId,
                                          @Valid @RequestBody HabitDtos.HabitRequest request) {
        return habitService.update(CurrentUser.id(), habitId, request);
    }

    @DeleteMapping("/{habitId}")
    public ResponseEntity<Void> delete(@PathVariable String habitId) {
        habitService.delete(CurrentUser.id(), habitId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{habitId}/logs")
    public HabitDtos.HabitLogResponse log(@PathVariable String habitId,
                                          @Valid @RequestBody HabitDtos.HabitLogRequest request) {
        return habitService.record(CurrentUser.id(), habitId, request);
    }

    /** Flips the check-in for a day, which is what the dashboard button uses. */
    @PatchMapping("/{habitId}/logs/toggle")
    public HabitDtos.HabitLogResponse toggle(@PathVariable String habitId,
                                            @RequestBody(required = false) HabitDtos.HabitToggleRequest request) {
        LocalDate date = request == null || request.logDate() == null ? LocalDate.now() : request.logDate();
        return habitService.toggle(CurrentUser.id(), habitId, date);
    }

    @GetMapping("/{habitId}/logs")
    public List<HabitDtos.HabitLogResponse> logs(
            @PathVariable String habitId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return habitService.logs(CurrentUser.id(), habitId, from, to);
    }

    @GetMapping("/{habitId}/trend")
    public HabitDtos.HabitTrend trend(@PathVariable String habitId,
                                      @RequestParam(required = false, defaultValue = "30") Integer days) {
        return habitService.trend(CurrentUser.id(), habitId, days == null ? 30 : days);
    }
}
