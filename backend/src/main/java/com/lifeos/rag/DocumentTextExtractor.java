package com.lifeos.rag;

import com.lifeos.exception.AppException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Extracts plain text from the supported upload formats.
 *
 * <p>Each parser is isolated so one malformed file cannot take the ingestion pipeline down, and
 * failures are surfaced as a per-document status rather than a 500.</p>
 */
@Component
public class DocumentTextExtractor {

    private static final Logger log = LoggerFactory.getLogger(DocumentTextExtractor.class);
    private static final int MAX_CHARACTERS = 2_000_000;

    public String extract(Path file, String extension) {
        String normalised = extension == null ? "" : extension.toLowerCase(java.util.Locale.ROOT);
        try {
            return switch (normalised) {
                case "txt", "md", "markdown" -> readText(file);
                case "pdf" -> readPdf(file);
                case "docx" -> readDocx(file);
                default -> throw AppException.badRequest("Unsupported file type: " + normalised);
            };
        } catch (AppException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            log.warn("Text extraction failed for {}: {}", file.getFileName(), ex.getMessage());
            throw AppException.unprocessable("The document could not be parsed: " + ex.getMessage());
        }
    }

    private String readText(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        if (!looksLikeText(text)) {
            // Some editors write UTF-16; fall back before declaring the upload unreadable.
            text = new String(Files.readAllBytes(file), StandardCharsets.UTF_16);
        }
        return truncate(text);
    }

    private boolean looksLikeText(String sample) {
        int limit = Math.min(sample.length(), 2000);
        int suspicious = 0;
        for (int i = 0; i < limit; i++) {
            if (sample.charAt(i) == 0) {
                suspicious++;
            }
        }
        return suspicious < limit / 20;
    }

    private String readPdf(Path file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return truncate(stripper.getText(document));
        }
    }

    private String readDocx(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file);
             XWPFDocument document = new XWPFDocument(in);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return truncate(extractor.getText());
        }
    }

    private String truncate(String text) {
        String cleaned = text == null ? "" : text.replace("\u0000", "").trim();
        return cleaned.length() <= MAX_CHARACTERS ? cleaned : cleaned.substring(0, MAX_CHARACTERS);
    }

    public static int wordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }
}