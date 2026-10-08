package com.lifeos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "lifeos.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        int authAttemptsPerWindow,
        Duration window,
        int aiRequestsPerMinute
) {
}