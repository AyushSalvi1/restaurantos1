package com.lifeos.websocket;

import com.lifeos.service.NotificationService;
import com.lifeos.security.UserPrincipal;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.time.Instant;

/**
 * STOMP endpoint for lightweight client commands. Subscriptions to {@code /user/**} are served by
 * the broker; this only handles explicit acknowledgements so the UI can react instantly.
 */
@Controller
public class NotificationWsController {

    private final NotificationService notificationService;

    public NotificationWsController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @MessageMapping("/notifications/read")
    public void acknowledge(@Payload String notificationId, UserPrincipal user) {
        if (user == null) {
            return;
        }
        notificationService.markRead(user.id(), notificationId);
    }

    @MessageMapping("/notifications/ping")
    public Instant ping() {
        return Instant.now();
    }
}