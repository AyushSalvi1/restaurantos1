package com.lifeos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.List;

@ConfigurationProperties(prefix = "lifeos.storage")
public record StorageProperties(
        String root,
        String knowledgeDir,
        String exportDir,
        long maxKnowledgeFileBytes,
        List<String> allowedKnowledgeExtensions
) {
    public Path knowledgePath() {
        return Path.of(knowledgeDir);
    }

    public Path exportPath() {
        return Path.of(exportDir);
    }
}