package com.lifeos.websocket;

import com.lifeos.dto.InsightDtos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/** Pushes in-app notifications over STOMP so the UI updates without polling. */
@Component
public class NotificationBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(NotificationBroadcaster.class);

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void notifyUser(String userId, InsightDtos.NotificationResponse notification) {
        try {
            messagingTemplate.convertAndSendToUser(userId, "/queue/notifications", notification);
        } catch (RuntimeException ex) {
            log.debug("Realtime delivery skipped for user {}: {}", userId, ex.getMessage());
        }
    }

    public void broadcastSystemNotice(String topic, Object payload) {
        try {
            messagingTemplate.convertAndSend("/topic/" + topic, payload);
        } catch (RuntimeException ex) {
            log.debug("Broadcast skipped on {}: {}", topic, ex.getMessage());
        }
    }
}