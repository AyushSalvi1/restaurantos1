package com.lifeos.controller;

import com.lifeos.dto.InsightDtos;
import com.lifeos.entity.enums.NotificationCategory;
import com.lifeos.security.CurrentUser;
import com.lifeos.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** In-app notification inbox. Real-time delivery happens over the authenticated WebSocket. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public InsightDtos.NotificationPage list(@RequestParam(required = false) NotificationCategory category,
                                             @RequestParam(required = false, defaultValue = "0") Integer page,
                                             @RequestParam(required = false, defaultValue = "20") Integer size) {
        return notificationService.list(CurrentUser.id(), category == null ? null : category.name(),
                page == null ? 0 : page, size == null ? 20 : size);
    }

    @GetMapping("/unread-count")
    public UnreadCount unreadCount() {
        return new UnreadCount(notificationService.unreadCount(CurrentUser.id()));
    }

    @PostMapping("/{notificationId}/read")
    public InsightDtos.NotificationResponse markRead(@PathVariable String notificationId) {
        return notificationService.markRead(CurrentUser.id(), notificationId);
    }

    @PostMapping("/read-all")
    public MarkAllReadResult markAllRead(@RequestBody(required = false) InsightDtos.MarkAllReadRequest request) {
        String category = request == null ? null : request.category();
        return new MarkAllReadResult(notificationService.markAllRead(CurrentUser.id(), category));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<Void> delete(@PathVariable String notificationId) {
        notificationService.delete(CurrentUser.id(), notificationId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAll() {
        notificationService.deleteAll(CurrentUser.id());
        return ResponseEntity.noContent().build();
    }

    public record UnreadCount(long unread) {
    }

    /** Reports how many rows changed so a caller can tell a no-op from a bulk clear. */
    public record MarkAllReadResult(long marked) {
    }
}
