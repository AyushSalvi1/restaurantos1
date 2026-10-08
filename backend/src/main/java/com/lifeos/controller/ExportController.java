package com.lifeos.controller;

import com.lifeos.dto.ExportDtos;
import com.lifeos.security.CurrentUser;
import com.lifeos.service.ExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Data portability and account deletion.
 *
 * <p>A generated export is only readable by the account that created it: the path is rebuilt from the
 * authenticated user id rather than taken from the request.</p>
 */
@RestController
@RequestMapping("/api/account")
public class ExportController {

    private final ExportService exportService;

    public ExportController(ExportService exportService) {
        this.exportService = exportService;
    }

    @PostMapping("/export")
    public ExportDtos.ExportResponse export() {
        return exportService.export(CurrentUser.id());
    }

    @GetMapping("/export/sections")
    public List<String> sections() {
        return exportService.supportedSections();
    }

    @GetMapping("/export/{exportId}")
    public ResponseEntity<byte[]> download(@PathVariable String exportId) {
        byte[] payload = exportService.read(CurrentUser.id(), exportId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"lifeos-export-" + exportId + ".json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload);
    }
}
