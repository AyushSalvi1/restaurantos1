package com.lifeos.controller;

import com.lifeos.dto.AdminDtos;
import com.lifeos.common.PageResponse;
import com.lifeos.security.CurrentUser;
import com.lifeos.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Administrative console.
 *
 * <p>Aggregate figures and configuration only. The class-level role requirement mirrors the rule in
 * {@code SecurityConfig} so the guarantee does not depend on a single filter chain matching.</p>
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    public AdminDtos.SystemStats stats() {
        return adminService.stats();
    }

    @GetMapping("/users")
    public PageResponse<AdminDtos.AdminUserResponse> users(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> roles,
            @RequestParam(required = false, defaultValue = "25") Integer size,
            @RequestParam(required = false, defaultValue = "0") Integer page) {
        return PageResponse.of(adminService.users(
                new AdminDtos.SearchFilter(q, size, page, roles)));
    }

    @PatchMapping("/users/{userId}/status")
    public AdminDtos.AdminUserResponse updateStatus(@PathVariable String userId,
                                                   @Valid @RequestBody AdminDtos.UpdateUserStatusRequest request) {
        return adminService.updateStatus(CurrentUser.id(), userId, request.status());
    }

    @PatchMapping("/users/{userId}/role")
    public AdminDtos.AdminUserResponse updateRole(@PathVariable String userId,
                                                 @Valid @RequestBody AdminDtos.UpdateUserRoleRequest request) {
        return adminService.updateRole(CurrentUser.id(), userId, request.role());
    }

    /** Counts and last-activity timestamps only. Entry text and document content are never returned. */
    @GetMapping("/users/{userId}/privacy")
    public AdminDtos.UserPrivacyMetadata privacyMetadata(@PathVariable String userId) {
        return adminService.privacyMetadata(userId);
    }

    @GetMapping("/audit-logs")
    public PageResponse<AdminDtos.AuditLogResponse> auditLogs(
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "50") Integer size) {
        return PageResponse.of(adminService.auditLogs(page == null ? 0 : page, size == null ? 50 : size));
    }

    @GetMapping("/error-logs")
    public PageResponse<AdminDtos.ErrorLogResponse> errorLogs(
            @RequestParam(required = false) String severity,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "50") Integer size) {
        return PageResponse.of(adminService.errorLogs(page == null ? 0 : page,
                size == null ? 50 : size, severity));
    }

    @GetMapping("/settings")
    public List<AdminDtos.SettingResponse> settings() {
        return adminService.settings();
    }

    @PostMapping("/settings")
    public AdminDtos.SettingResponse upsertSetting(@Valid @RequestBody AdminDtos.SettingRequest request) {
        return adminService.upsertSetting(CurrentUser.id(), request);
    }

    /** Reports which providers have credentials without ever echoing a credential value. */
    @GetMapping("/ai-configuration")
    public AdminDtos.AiConfigurationResponse aiConfiguration() {
        return adminService.aiConfiguration();
    }
}
