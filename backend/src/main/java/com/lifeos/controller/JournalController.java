package com.lifeos.controller;

import com.lifeos.common.PageResponse;
import com.lifeos.dto.JournalDtos;
import com.lifeos.security.CurrentUser;
import com.lifeos.service.JournalService;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDate;

/** Journal entries, their statistics and optional thematic analysis. */
@RestController
@RequestMapping("/api/journal")
public class JournalController {

    private final JournalService journalService;

    public JournalController(JournalService journalService) {
        this.journalService = journalService;
    }

    @GetMapping
    public PageResponse<JournalDtos.JournalResponse> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "25") Integer size) {
        Sort sort = Sort.by(Sort.Order.desc("entryDate"), Sort.Order.desc("createdAt"));
        return PageResponse.of(journalService.page(CurrentUser.id(),
                PageRequest.of(page == null ? 0 : page, size == null ? 25 : size, sort)));
    }

    @GetMapping("/statistics")
    public JournalDtos.JournalStats statistics() {
        return journalService.statistics(CurrentUser.id());
    }

    @GetMapping("/{entryId}")
    public JournalDtos.JournalResponse get(@PathVariable String entryId) {
        return journalService.get(CurrentUser.id(), entryId);
    }

    @PostMapping
    public ResponseEntity<JournalDtos.JournalResponse> create(
            @Valid @RequestBody JournalDtos.JournalRequest request) {
        JournalDtos.JournalResponse created = journalService.create(CurrentUser.id(), request);
        return ResponseEntity.created(UriComponentsBuilder.fromPath("/api/journal/{id}")
                        .buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @PutMapping("/{entryId}")
    public JournalDtos.JournalResponse update(@PathVariable String entryId,
                                              @Valid @RequestBody JournalDtos.JournalRequest request) {
        return journalService.update(CurrentUser.id(), entryId, request);
    }

    @DeleteMapping("/{entryId}")
    public ResponseEntity<Void> delete(@PathVariable String entryId) {
        journalService.delete(CurrentUser.id(), entryId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Summarises the entry's own themes. The response states plainly that this is not clinical
     * advice, and it works with no AI provider configured.
     */
    @PostMapping("/{entryId}/analysis")
    public JournalDtos.JournalResponse analyse(@PathVariable String entryId) {
        JournalDtos.JournalAnalysis analysis = journalService.analyse(CurrentUser.id(), entryId);
        return journalService.setAnalysis(CurrentUser.id(), entryId, analysis);
    }
}
