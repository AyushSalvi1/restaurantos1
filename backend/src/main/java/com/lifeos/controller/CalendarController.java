package com.lifeos.controller;

import com.lifeos.dto.CalendarDtos;
import com.lifeos.security.CurrentUser;
import com.lifeos.service.CalendarService;
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

/** Calendar events and the combined feed of events, task deadlines and habit schedules. */
@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    private final CalendarService calendarService;

    public CalendarController(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GetMapping
    public CalendarDtos.CalendarFeed feed(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return calendarService.feed(CurrentUser.id(), from, to);
    }

    @GetMapping("/deadlines")
    public java.util.List<CalendarDtos.EventResponse> deadlines(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return calendarService.deadlineEvents(CurrentUser.id(), from, to);
    }

    @GetMapping("/{eventId}")
    public CalendarDtos.Occurrence get(@PathVariable String eventId) {
        return calendarService.get(CurrentUser.id(), eventId);
    }

    @PostMapping
    public ResponseEntity<CalendarDtos.EventResponse> create(
            @Valid @RequestBody CalendarDtos.EventRequest request) {
        CalendarDtos.EventResponse created = calendarService.create(CurrentUser.id(), request);
        return ResponseEntity.created(UriComponentsBuilder.fromPath("/api/calendar/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PutMapping("/{eventId}")
    public CalendarDtos.EventResponse update(@PathVariable String eventId,
                                             @Valid @RequestBody CalendarDtos.EventRequest request) {
        return calendarService.update(CurrentUser.id(), eventId, request);
    }

    @PatchMapping("/{eventId}/move")
    public CalendarDtos.EventResponse move(@PathVariable String eventId,
                                           @Valid @RequestBody CalendarDtos.MoveRequest request) {
        return calendarService.move(CurrentUser.id(), eventId, request);
    }

    @DeleteMapping("/{eventId}")
    public ResponseEntity<Void> delete(@PathVariable String eventId) {
        calendarService.delete(CurrentUser.id(), eventId);
        return ResponseEntity.noContent().build();
    }
}
