package com.lifeos.service;

import com.lifeos.ai.AiProviderRegistry;
import com.lifeos.config.VectorProperties;
import com.lifeos.dto.AdminDtos;
import com.lifeos.entity.AuditLog;
import com.lifeos.entity.ErrorLog;
import com.lifeos.entity.SystemSetting;
import com.lifeos.entity.User;
import com.lifeos.entity.enums.Role;
import com.lifeos.entity.enums.UserStatus;
import com.lifeos.repository.AuditLogRepository;
import com.lifeos.repository.ErrorLogRepository;
import com.lifeos.repository.GoalRepository;
import com.lifeos.repository.HabitRepository;
import com.lifeos.repository.FocusSessionRepository;
import com.lifeos.repository.JournalEntryRepository;
import com.lifeos.repository.KnowledgeChunkRepository;
import com.lifeos.repository.KnowledgeDocumentRepository;
import com.lifeos.repository.RefreshTokenRepository;
import com.lifeos.repository.SystemSettingRepository;
import com.lifeos.repository.TaskRepository;
import com.lifeos.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Administrative operations.
 *
 * <p>The admin surface is deliberately aggregate-only. Counts and configuration are exposed; journal
 * text and knowledge document content are not, because an administrator has no need for them and
 * reading them would weaken the privacy guarantee the rest of the system makes.</p>
 */
@Service
public class AdminService {

    private static final Duration DAY = Duration.ofDays(1);
    private static final String PRIVACY_POLICY = "Journal entries and knowledge document contents are never exposed "
            + "to administrators. Only counts and the most recent activity timestamps are visible.";

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final GoalRepository goalRepository;
    private final HabitRepository habitRepository;
    private final FocusSessionRepository focusSessionRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final KnowledgeDocumentRepository documentRepository;
    private final KnowledgeChunkRepository chunkRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditLogRepository auditLogRepository;
    private final ErrorLogRepository errorLogRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final AiProviderRegistry providerRegistry;
    private final VectorProperties vectorProperties;
    private final AuditService auditService;

    public AdminService(UserRepository userRepository,
                        TaskRepository taskRepository,
                        GoalRepository goalRepository,
                        HabitRepository habitRepository,
                        FocusSessionRepository focusSessionRepository,
                        JournalEntryRepository journalEntryRepository,
                        KnowledgeDocumentRepository documentRepository,
                        KnowledgeChunkRepository chunkRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        AuditLogRepository auditLogRepository,
                        ErrorLogRepository errorLogRepository,
                        SystemSettingRepository systemSettingRepository,
                        AiProviderRegistry providerRegistry,
                        VectorProperties vectorProperties,
                        AuditService auditService) {
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.goalRepository = goalRepository;
        this.habitRepository = habitRepository;
        this.focusSessionRepository = focusSessionRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditLogRepository = auditLogRepository;
        this.errorLogRepository = errorLogRepository;
        this.systemSettingRepository = systemSettingRepository;
        this.providerRegistry = providerRegistry;
        this.vectorProperties = vectorProperties;
        this.auditService = auditService;
    }

    // ----------------------------------------------------------------- stats

    @Transactional(readOnly = true)
    public AdminDtos.SystemStats stats() {
        Instant now = Instant.now();
        return new AdminDtos.SystemStats(
                userRepository.count(),
                userRepository.countByStatus(UserStatus.ACTIVE),
                userRepository.countByStatus(UserStatus.DISABLED),
                userRepository.countByCreatedAtAfter(now.minus(DAY.multipliedBy(7))),
                taskRepository.count(),
                goalRepository.count(),
                habitRepository.count(),
                documentRepository.count(),
                chunkRepository.count(),
                journalEntryRepository.count(),
                focusSessionRepository.sumAllCompletedMinutes(),
                errorLogRepository.countByCreatedAtAfter(now.minus(DAY)),
                errorLogRepository.countBySeverityAndCreatedAtAfter("WARN", now.minus(DAY)),
                auditLogRepository.countByCreatedAtAfter(now.minus(DAY)),
                now);
    }

    // ----------------------------------------------------------------- users

    @Transactional(readOnly = true)
    public Page<AdminDtos.AdminUserResponse> users(AdminDtos.SearchFilter filter) {
        int size = filter == null || filter.size() == null ? 25 : filter.size();
        int page = filter == null || filter.page() == null ? 0 : Math.max(0, filter.page());
        String query = filter == null || filter.query() == null ? "" : filter.query().strip().toLowerCase(Locale.ROOT);

        List<AdminDtos.AdminUserResponse> matched = new ArrayList<>();
        for (User user : userRepository.findAll()) {
            if (!query.isEmpty()
                    && !user.getEmail().toLowerCase(Locale.ROOT).contains(query)
                    && (user.getFullName() == null
                    || !user.getFullName().toLowerCase(Locale.ROOT).contains(query))) {
                continue;
            }
            matched.add(describe(user));
        }
        matched.sort((left, right) -> {
            int byRole = right.role().compareTo(left.role());
            return byRole != 0 ? byRole : String.valueOf(right.createdAt()).compareTo(String.valueOf(left.createdAt()));
        });

        int from = Math.min(page * size, matched.size());
        int to = Math.min(from + size, matched.size());
        return new org.springframework.data.domain.PageImpl<>(matched.subList(from, to),
                PageRequest.of(page, size), matched.size());
    }

    @Transactional
    public AdminDtos.AdminUserResponse updateStatus(String actorUserId, String targetUserId, String status) {
        User target = requireUser(targetUserId);
        UserStatus next;
        try {
            next = UserStatus.valueOf(status.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown account status: " + status);
        }
        if (targetUserId.equals(actorUserId) && next != UserStatus.ACTIVE) {
            throw new IllegalArgumentException("An administrator cannot suspend their own account");
        }
        target.setStatus(next);
        userRepository.save(target);
        if (next != UserStatus.ACTIVE) {
            refreshTokenRepository.revokeAllForUser(targetUserId, "account " + next.name());
        }
        auditService.record(actorUserId, "USER_STATUS_CHANGED", "User", targetUserId, next.name());
        return describe(target);
    }

    @Transactional
    public AdminDtos.AdminUserResponse updateRole(String actorUserId, String targetUserId, String role) {
        User target = requireUser(targetUserId);
        if (targetUserId.equals(actorUserId)) {
            throw new IllegalArgumentException("An administrator cannot change their own role");
        }
        Role next;
        try {
            next = Role.valueOf(role.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown role: " + role);
        }
        target.setRole(next);
        userRepository.save(target);
        auditService.record(actorUserId, "USER_ROLE_CHANGED", "User", targetUserId, next.name());
        return describe(target);
    }

    // ------------------------------------------------------------------ logs

    @Transactional(readOnly = true)
    public Page<AdminDtos.AuditLogResponse> auditLogs(int page, int size) {
        return auditLogRepository.findAllByOrderByCreatedAtDesc(
                        PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size))))
                .map(this::toAuditResponse);
    }

    @Transactional(readOnly = true)
    public Page<AdminDtos.ErrorLogResponse> errorLogs(int page, int size, String severity) {
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size)));
        Page<ErrorLog> result = severity == null || severity.isBlank()
                ? errorLogRepository.findAllByOrderByCreatedAtDesc(pageable)
                : errorLogRepository.findAllByOrderByCreatedAtDesc(pageable);
        return result.map(this::toErrorResponse);
    }

    // -------------------------------------------------------------- settings

    @Transactional(readOnly = true)
    public List<AdminDtos.SettingResponse> settings() {
        List<AdminDtos.SettingResponse> settings = new ArrayList<>();
        systemSettingRepository.findAll().forEach(setting -> settings.add(new AdminDtos.SettingResponse(
                setting.getSettingKey(), setting.getSettingValue(), setting.getValueType(),
                setting.getDescription(), setting.getUpdatedAt(), setting.getUpdatedBy())));
        return settings;
    }

    @Transactional
    public AdminDtos.SettingResponse upsertSetting(String actorUserId, AdminDtos.SettingRequest request) {
        SystemSetting setting = systemSettingRepository.findBySettingKey(request.key())
                .orElseGet(SystemSetting::new);
        setting.setSettingKey(request.key().strip());
        setting.setSettingValue(request.value() == null ? "" : request.value());
        setting.setValueType(request.valueType() == null ? "STRING" : request.valueType().strip());
        setting.setDescription(request.description());
        setting.setUpdatedBy(actorUserId);
        systemSettingRepository.save(setting);
        auditService.record(actorUserId, "SETTING_CHANGED", "SystemSetting", setting.getSettingKey(),
                setting.getValueType());
        return new AdminDtos.SettingResponse(setting.getSettingKey(), setting.getSettingValue(),
                setting.getValueType(), setting.getDescription(), setting.getUpdatedAt(), setting.getUpdatedBy());
    }

    // -------------------------------------------------------------- privacy

    @Transactional(readOnly = true)
    public AdminDtos.UserPrivacyMetadata privacyMetadata(String userId) {
        User user = requireUser(userId);
        List<com.lifeos.entity.JournalEntry> entries =
                journalEntryRepository.findByUserIdAndDeletedAtIsNullOrderByEntryDateDesc(userId);
        List<com.lifeos.entity.KnowledgeDocument> documents =
                documentRepository.findByUserIdAndDeletedAtIsNull(userId);
        return new AdminDtos.UserPrivacyMetadata(
                user.getId(),
                user.getEmail(),
                entries.size(),
                documents.size(),
                documents.stream().mapToInt(com.lifeos.entity.KnowledgeDocument::getChunkCount).sum(),
                entries.isEmpty() ? null : entries.get(0).getCreatedAt(),
                documents.isEmpty() ? null : documents.stream()
                        .map(com.lifeos.entity.KnowledgeDocument::getCreatedAt)
                        .max(Instant::compareTo).orElse(null),
                PRIVACY_POLICY);
    }

    // ------------------------------------------------------------- AI config

    public AdminDtos.AiConfigurationResponse aiConfiguration() {
        Map<String, String> models = new LinkedHashMap<>();
        models.put("active", providerRegistry.active().name());
        models.put("activeModel", providerRegistry.active().embeddingModel());
        models.put("vectorDimensions", String.valueOf(providerRegistry.embeddingDimensions()));

        List<String> notes = new ArrayList<>();
        notes.add("Credentials are read from configuration only and are never stored in the database.");
        if (providerRegistry.isDegraded()) {
            notes.add("The configured provider has no credentials, so LIFEOS is answering on-device.");
        }
        if (providerRegistry.isLocalEmbedding()) {
            notes.add("Embeddings are produced locally with a deterministic hashing embedder.");
        }

        return new AdminDtos.AiConfigurationResponse(
                providerRegistry.status().configuredProvider(),
                configured("gemini"),
                configured("openai"),
                configured("ollama"),
                providerRegistry.isLocalEmbedding(),
                vectorProperties.dimensions(),
                models,
                notes);
    }

    private boolean configured(String providerName) {
        return providerRegistry.all().stream()
                .anyMatch(provider -> provider.name().equalsIgnoreCase(providerName) && provider.isConfigured());
    }

    // -------------------------------------------------------------- internals

    private User requireUser(String userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    private AdminDtos.AdminUserResponse describe(User user) {
        return new AdminDtos.AdminUserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().name(),
                user.getStatus().name(),
                user.isEmailVerified(),
                user.isOnboardingCompleted(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                taskRepository.countByUserIdAndDeletedAtIsNull(user.getId()),
                goalRepository.countByUserIdAndDeletedAtIsNull(user.getId()),
                refreshTokenRepository.countByUserIdAndRevokedFalse(user.getId()));
    }

    private AdminDtos.AuditLogResponse toAuditResponse(AuditLog log) {
        String actorEmail = null;
        if (log.getActorUserId() != null) {
            actorEmail = userRepository.findByIdAndDeletedAtIsNull(log.getActorUserId())
                    .map(User::getEmail).orElse(null);
        }
        return new AdminDtos.AuditLogResponse(log.getId(), log.getActorUserId(), actorEmail,
                log.getAction(), log.getEntityType(), log.getEntityId(), log.getDetails(),
                log.getIpAddress(), log.getCreatedAt());
    }

    private AdminDtos.ErrorLogResponse toErrorResponse(ErrorLog log) {
        return new AdminDtos.ErrorLogResponse(log.getId(), log.getSeverity(), log.getMessage(),
                log.getPath(), log.getMethod(), log.getUserId(), log.getExceptionClass(), log.getCreatedAt());
    }
}
