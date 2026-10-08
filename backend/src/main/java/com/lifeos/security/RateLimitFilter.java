package com.lifeos.security;

import com.lifeos.config.RateLimitProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Small in-process sliding-window limiter protecting credential endpoints and AI calls.
 * Fronted by Nginx in production; this layer still protects direct backend access.
 */
@Component
@Order(90)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimitProperties properties;
    private final Map<String, Window> authWindows = new ConcurrentHashMap<>();
    private final Map<String, Window> aiWindows = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.enabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return !(path.startsWith("/api/auth/") || path.startsWith("/api/ai/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        String client = clientKey(request);

        if (path.startsWith("/api/ai/")) {
            Window window = aiWindows.computeIfAbsent(client, key -> new Window());
            if (!window.tryAcquire(properties.aiRequestsPerMinute(), 60_000L)) {
                reject(response, "Too many AI requests. Please wait a moment.");
                return;
            }
        } else if (isCredentialEndpoint(path)) {
            Window window = authWindows.computeIfAbsent(client, key -> new Window());
            long windowMillis = properties.window() == null ? 600_000L : properties.window().toMillis();
            if (!window.tryAcquire(properties.authAttemptsPerWindow(), windowMillis)) {
                reject(response, "Too many authentication attempts. Please try again later.");
                return;
            }
        }

        filterChain.doFilter(request, response);
        prune();
    }

    private boolean isCredentialEndpoint(String path) {
        return path.equals("/api/auth/login")
                || path.equals("/api/auth/register")
                || path.equals("/api/auth/forgot-password")
                || path.equals("/api/auth/reset-password");
    }

    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        log.warn("Rate limit exceeded: {}", message);
        response.setStatus(429);
        response.setContentType("application/json");
        response.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"" + message + "\"}");
    }

    private void prune() {
        Instant cutoff = Instant.now().minusSeconds(3600);
        authWindows.entrySet().removeIf(e -> e.getValue().lastAccess.isBefore(cutoff));
        aiWindows.entrySet().removeIf(e -> e.getValue().lastAccess.isBefore(cutoff));
    }

    private static final class Window {
        private final AtomicInteger counter = new AtomicInteger();
        private volatile long windowStart = System.currentTimeMillis();
        private volatile Instant lastAccess = Instant.now();

        synchronized boolean tryAcquire(int limit, long windowMillis) {
            long now = System.currentTimeMillis();
            lastAccess = Instant.now();
            if (now - windowStart >= windowMillis) {
                windowStart = now;
                counter.set(0);
            }
            return counter.incrementAndGet() <= limit;
        }
    }
}