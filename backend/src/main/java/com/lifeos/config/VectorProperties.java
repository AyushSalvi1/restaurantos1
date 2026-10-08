package com.lifeos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lifeos.vector")
public record VectorProperties(int dimensions) {
}