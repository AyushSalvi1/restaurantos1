package com.lifeos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lifeos.app")
public record AppProperties(
        String baseUrl,
        String corsAllowedOrigins,
        boolean seedDataEnabled,
        boolean demoMode,
        /** Account the demo seeder creates. Never used unless seeding is switched on. */
        String seedEmail,
        String seedPassword
) {

    public AppProperties {
        if (seedEmail == null || seedEmail.isBlank()) {
            seedEmail = "demo@lifeos.app";
        }
        if (seedPassword == null || seedPassword.isBlank()) {
            seedPassword = "Demo-LifeOS-2024";
        }
    }
}