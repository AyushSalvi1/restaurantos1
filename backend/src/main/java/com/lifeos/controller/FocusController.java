package com.lifeos.controller;

import com.lifeos.dto.FocusDtos;
import com.lifeos.security.CurrentUser;
import com.lifeos.service.FocusService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Focus sessions and their statistics. */
@RestController
@RequestMapping("/api/focus")
public class FocusController {

    private final FocusService focusService;

    public FocusController(FocusService focusService) {
        this.focusService = focusService;
    }

    @PostMapping("/sessions")
    public ResponseEntity<FocusDtos.FocusSessionResponse> start(
            @Valid @RequestBody FocusDtos.StartSessionRequest request) {
        FocusDtos.FocusSessionResponse started = focusService.start(CurrentUser.id(), request);
        return ResponseEntity.created(UriComponentsBuilder.fromPath("/api/focus/sessions/{id}")
                        .buildAndExpand(started.id()).toUri())
                .body(started);
    }

    @PostMapping("/sessions/{sessionId}/complete")
    public FocusDtos.FocusSessionResponse complete(
            @PathVariable String sessionId,
            @Valid @RequestBody FocusDtos.CompleteSessionRequest request) {
        return focusService.complete(CurrentUser.id(), sessionId, request);
    }

    @GetMapping("/sessions/{sessionId}")
    public FocusDtos.FocusSessionResponse get(@PathVariable String sessionId) {
        return focusService.get(CurrentUser.id(), sessionId);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> delete(@PathVariable String sessionId) {
        focusService.delete(CurrentUser.id(), sessionId);
        return ResponseEntity.noContent().build();
    }

    /**
 * Session history, newest first. The range is day-granular like the calendar and analytics filters, and
 * both bounds are optional: omitting them returns the account's full history rather than nothing.
 */
@GetMapping("/sessions")
    public List<FocusDtos.FocusSessionResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "50") Integer limit) {
        return focusService.list(CurrentUser.id(), from, to, limit);
    }

    @GetMapping("/statistics")
    public FocusDtos.FocusStats statistics(@RequestParam(required = false, defaultValue = "30") Integer days) {
        return focusService.statistics(CurrentUser.id(), days == null ? 30 : days);
    }
}
