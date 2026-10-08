package com.lifeos.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Email delivery abstraction. Falls back to structured logging when no SMTP host is
 * configured, which keeps local development and CI fully functional offline.
 */
@Component
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private final JavaMailSender mailSender;
    private final boolean enabled;

    public EmailSender(JavaMailSender mailSender,
                       org.springframework.core.env.Environment environment) {
        this.mailSender = mailSender;
        String host = environment.getProperty("spring.mail.host");
        this.enabled = host != null && !host.isBlank() && !"localhost".equalsIgnoreCase(host);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void send(String to, String subject, String body) {
        if (!enabled) {
            log.info("[email:dev] to={} subject={} body={}", to, subject, body.replaceAll("\\s+", " ").trim());
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
        } catch (RuntimeException ex) {
            log.error("Failed to send email to {}: {}", to, ex.getMessage());
        }
    }
}