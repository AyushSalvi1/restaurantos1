package com.lifeos.service;

import com.lifeos.dto.InsightDtos;
import com.lifeos.entity.Notification;
import com.lifeos.entity.NotificationPreference;
import com.lifeos.entity.enums.NotificationCategory;
import com.lifeos.entity.enums.NotificationPriority;
import com.lifeos.exception.AppException;
import com.lifeos.repository.NotificationPreferenceRepository;
import com.lifeos.repository.NotificationRepository;
import com.lifeos.websocket.NotificationBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Central notification engine. Every producer funnels through {@link #notify} so preferences,
 * deduplication and quiet hours are honoured in exactly one place.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final EmailSenderBridge emailBridge;
    private final NotificationBroadcaster broadcaster;
    private final UserZoneService userZoneService;

    public NotificationService(NotificationRepository notificationRepository,
                               NotificationPreferenceRepository preferenceRepository,
                               EmailSenderBridge emailBridge,
                               NotificationBroadcaster broadcaster,
                               UserZoneService userZoneService) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.emailBridge = emailBridge;
        this.broadcaster = broadcaster;
        this.userZoneService = userZoneService;
    }

    /**
     * @param dedupeKey optional stable key; when supplied a second call with the same key is a no-op
     * @return the created notification, or empty when suppressed by preferences or quiet hours
     */
    @Transactional
    public Optional<Notification> notify(String userId,
                                         NotificationCategory category,
                                         NotificationPriority priority,
                                         String title,
                                         String body,
                                         String link,
                                         String dedupeKey) {
        NotificationPreference preference = preferenceRepository.findByUserId(userId).orElse(null);
        if (preference != null && !enabledFor(preference, category)) {
            return Optional.empty();
        }
        if (dedupeKey != null && notificationRepository.findByUserIdAndDedupeKey(userId, dedupeKey).isPresent()) {
            return Optional.empty();
        }
        if (isQuietNow(preference, userZoneService.zoneOf(userId))) {
            return Optional.empty();
        }

        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setCategory(category);
        notification.setPriority(priority == null ? NotificationPriority.NORMAL : priority);
        notification.setTitle(truncate(title, 200));
        notification.setBody(truncate(body, 700));
        notification.setLink(link);
        notification.setDedupeKey(dedupeKey == null ? null : truncate(dedupeKey, 190));
        notificationRepository.save(notification);

        broadcaster.notifyUser(userId, toResponse(notification));
        if (preference != null && preference.isEmailEnabled()) {
            emailBridge.send(userId, category, title, body, link);
        }
        return Optional.of(notification);
    }

    @Transactional
    public Optional<Notification> schedule(String userId, NotificationCategory category, String title, String body,
                                           String link, Instant deliverAt, String dedupeKey) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setCategory(category);
        notification.setPriority(NotificationPriority.NORMAL);
        notification.setTitle(truncate(title, 200));
        notification.setBody(truncate(body, 700));
        notification.setLink(link);
        notification.setScheduledFor(deliverAt);
        notification.setDedupeKey(dedupeKey == null ? null : truncate(dedupeKey, 190));
        notificationRepository.save(notification);
        return Optional.of(notification);
    }

    @Transactional(readOnly = true)
    public InsightDtos.NotificationPage list(String userId, String category, Integer page, Integer size) {
        PageRequest pageable = PageRequest.of(
                Math.max(0, page == null ? 0 : page),
                Math.min(Math.max(size == null ? 20 : size, 1), 100),
                Sort.by(Sort.Order.desc("createdAt")));
        Page<Notification> result = category == null
                ? notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : notificationRepository.findByUserIdAndCategoryOrderByCreatedAtDesc(userId,
                NotificationCategory.valueOf(category.toUpperCase(java.util.Locale.ROOT)), pageable);

        return new InsightDtos.NotificationPage(
                result.stream().map(this::toResponse).toList(),
                notificationRepository.countByUserIdAndReadAtIsNull(userId),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public long unreadCount(String userId) {
        return notificationRepository.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public InsightDtos.NotificationResponse markRead(String userId, String notificationId) {
        Notification notification = require(userId, notificationId);
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
            notificationRepository.save(notification);
        }
        return toResponse(notification);
    }

    @Transactional
    public long markAllRead(String userId, String category) {
        PageRequest pageable = PageRequest.of(0, 500, Sort.by(Sort.Order.desc("createdAt")));
        Page<Notification> page = category == null
                ? notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                : notificationRepository.findByUserIdAndCategoryOrderByCreatedAtDesc(userId,
                NotificationCategory.valueOf(category.toUpperCase(java.util.Locale.ROOT)), pageable);
        Instant now = Instant.now();
        List<Notification> unread = page.getContent().stream()
                .filter(notification -> notification.getReadAt() == null)
                .toList();
        unread.forEach(notification -> notification.setReadAt(now));
        notificationRepository.saveAll(unread);
        return unread.size();
    }

    @Transactional
    public void delete(String userId, String notificationId) {
        notificationRepository.delete(require(userId, notificationId));
    }

    @Transactional
    public void deleteAll(String userId) {
        notificationRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(0, 1000, Sort.by(Sort.Order.desc("createdAt"))))
                .forEach(notificationRepository::delete);
    }

    /** Promotes scheduled reminders whose time has arrived. Invoked by the reminder scheduler. */
    @Transactional
    public int deliverDueReminders(Instant now) {
        List<Notification> due = notificationRepository.findDueReminders(now);
        for (Notification notification : due) {
            notification.setScheduledFor(null);
            broadcaster.notifyUser(notification.getUserId(), toResponse(notification));
        }
        notificationRepository.saveAll(due);
        return due.size();
    }

    /** Removes read notifications older than the cutoff so the inbox table stays bounded. */
    @Transactional
    public int expireStaleReminders(Instant readBefore) {
        return notificationRepository.deleteReadBefore(readBefore);
    }

    @Transactional(readOnly = true)
    public boolean isQuietNow(String userId, Instant now) {
        return preferenceRepository.findByUserId(userId)
                .map(preference -> isQuietNow(preference, userZoneService.zoneOf(userId)))
                .orElse(false);
    }

    public InsightDtos.NotificationResponse toResponse(Notification notification) {
        return new InsightDtos.NotificationResponse(
                notification.getId(),
                notification.getCategory().name(),
                notification.getTitle(),
                notification.getBody(),
                notification.getLink(),
                notification.getPriority().name(),
                notification.isRead(),
                notification.getCreatedAt(),
                notification.getReadAt());
    }

    private Notification require(String userId, String notificationId) {
        return notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> AppException.notFound("Notification not found"));
    }

    private boolean enabledFor(NotificationPreference preference, NotificationCategory category) {
        if (!preference.isInAppEnabled()) {
            return false;
        }
        return switch (category) {
            case TASK -> preference.isTaskEnabled();
            case GOAL -> preference.isGoalEnabled();
            case HABIT -> preference.isHabitEnabled();
            case CALENDAR -> preference.isCalendarEnabled();
            case FINANCE -> preference.isFinanceEnabled();
            case LEARNING -> preference.isLearningEnabled();
            case AI_INSIGHT -> preference.isAiInsightEnabled();
            case SYSTEM -> true;
        };
    }

    private boolean isQuietNow(NotificationPreference preference, ZoneId zone) {
        LocalTime start = preference.getQuietHoursStart();
        LocalTime end = preference.getQuietHoursEnd();
        if (start == null || end == null) {
            return false;
        }
        LocalTime now = LocalTime.now(zone);
        return start.equals(end)
                ? false
                : start.isBefore(end)
                ? !now.isBefore(start) && now.isBefore(end)
                : !now.isBefore(start) || now.isBefore(end);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** Narrow wrapper over the email sender that also records which user is being emailed. */
    @org.springframework.stereotype.Component
    public static class EmailSenderBridge {
        private final com.lifeos.notification.EmailSender emailSender;
        private final com.lifeos.repository.UserRepository userRepository;

        public EmailSenderBridge(com.lifeos.notification.EmailSender emailSender,
                                 com.lifeos.repository.UserRepository userRepository) {
            this.emailSender = emailSender;
            this.userRepository = userRepository;
        }

        public void send(String userId, NotificationCategory category, String title, String body, String link) {
            userRepository.findByIdAndDeletedAtIsNull(userId).ifPresent(user ->
                    emailSender.send(user.getEmail(), "[LIFEOS] " + title, compose(category, body, link)));
        }

        private String compose(NotificationCategory category, String body, String link) {
            StringBuilder text = new StringBuilder(body == null ? "" : body);
            if (link != null && !link.isBlank()) {
                text.append(System.lineSeparator()).append(link);
            }
            text.append(System.lineSeparator())
                    .append("You are receiving this because notification preferences are enabled in LIFEOS.");
            return text.toString();
        }
    }
}