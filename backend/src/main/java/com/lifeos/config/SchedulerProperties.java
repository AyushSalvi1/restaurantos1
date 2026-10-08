package com.lifeos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lifeos.scheduler")
public record SchedulerProperties(boolean enabled) {
}