package com.lifeos.util;

import com.lifeos.exception.AppException;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Input validation for user-supplied uploads and free-text content. */
public final class UploadValidation {

    private static final Pattern SAFE_FILENAME = Pattern.compile("[A-Za-z0-9._\\- ]+");
    private static final Pattern URL_PATTERN =
            Pattern.compile("^https?://[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%-]+$");

    private UploadValidation() {
    }

    public static String requireExtension(MultipartFile file, Set<String> allowed) {
        if (file == null || file.isEmpty()) {
            throw AppException.badRequest("A non-empty file is required");
        }
        String filename = file.getOriginalFilename();
        String extension = extensionOf(filename);
        if (extension.isEmpty() || !allowed.contains(extension)) {
            throw AppException.badRequest("Unsupported file type. Allowed types: " + String.join(", ", allowed));
        }
        return extension;
    }

    public static void requireSize(MultipartFile file, long maxBytes) {
        if (file.getSize() > maxBytes) {
            throw new AppException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "The uploaded file exceeds the maximum allowed size of " + (maxBytes / 1024 / 1024) + " MB");
        }
    }

    public static String extensionOf(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        int index = filename.lastIndexOf('.');
        String extension = filename.substring(index + 1).toLowerCase(Locale.ROOT);
        return SAFE_FILENAME.matcher(extension).matches() ? extension : "";
    }

    /** Produces a storage-safe filename, never echoing client-supplied path segments. */
    public static String safeFilename(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "upload";
        }
        String base = originalName.replace('\\', '/');
        base = base.substring(base.lastIndexOf('/') + 1);
        base = base.replaceAll("[^A-Za-z0-9._\\- ]", "_");
        if (base.isBlank() || base.length() > 120) {
            return "upload";
        }
        return base;
    }

    public static String requireValidUrl(String url) {
        if (url == null || !URL_PATTERN.matcher(url.strip()).matches()) {
            throw AppException.badRequest("A valid http(s) URL is required");
        }
        return url.strip();
    }
}