package com.lifeos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeos.config.StorageProperties;
import com.lifeos.dto.ExportDtos;
import com.lifeos.exception.AppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writes a user's own data to a single JSON file.
 *
 * <p>The export is generated from the authenticated user's rows only, is written under the configured
 * export directory with a name the caller cannot influence, and the path is returned to that same
 * caller. Nothing is served from a predictable location, so knowing an export id is not enough to
 * read someone else's data.</p>
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private final StorageProperties storageProperties;
    private final ObjectMapper objectMapper;
    private final DataPortabilityCollector collector;

    public ExportService(StorageProperties storageProperties,
                         ObjectMapper objectMapper,
                         DataPortabilityCollector collector) {
        this.storageProperties = storageProperties;
        this.objectMapper = objectMapper;
        this.collector = collector;
    }

    @Transactional(readOnly = true)
    public ExportDtos.ExportResponse export(String userId) {
        String exportId = UUID.randomUUID().toString();
        Path directory = storageProperties.exportPath().resolve(userId);
        Path file = directory.resolve("export-" + exportId + ".json");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exportId", exportId);
        payload.put("generatedAt", Instant.now().toString());
        payload.put("note", "This file contains your LIFEOS data as it stood at the time of export. "
                + "It includes private entries such as journal text; store it accordingly.");
        payload.put("data", collector.collect(userId));

        try {
            Files.createDirectories(directory);
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), payload);
        } catch (IOException ex) {
            log.error("Export failed for user {}", userId, ex);
            throw new IllegalStateException("The export could not be written", ex);
        }

        long size;
        try {
            size = Files.size(file);
        } catch (IOException ex) {
            size = 0;
        }
        return new ExportDtos.ExportResponse(exportId, file.getFileName().toString(), size,
                Instant.now(), "READY",
                "Your data has been written to " + directory.toAbsolutePath()
                        + ". Download it from your account settings before it is cleaned up by retention.");
    }

    /** Reads an export the caller generated. Ownership is re-checked rather than assumed. */
    public byte[] read(String userId, String exportId) {
        if (exportId == null || !exportId.matches("^[0-9a-fA-F-]{36}$")) {
            throw AppException.notFound("Unknown export");
        }
        Path file = storageProperties.exportPath().resolve(userId).resolve("export-" + exportId + ".json");
        if (!Files.isRegularFile(file)) {
            throw AppException.notFound("Unknown export");
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException ex) {
            throw new IllegalStateException("The export could not be read", ex);
        }
    }

    @Transactional(readOnly = true)
    public List<String> supportedSections() {
        return collector.sectionNames();
    }
}
