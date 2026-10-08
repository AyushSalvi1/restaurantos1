package com.lifeos.service;

import com.lifeos.entity.User;
import com.lifeos.entity.UserPreference;
import com.lifeos.exception.AppException;
import com.lifeos.repository.UserPreferenceRepository;
import com.lifeos.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the timezone every date-bound feature works in. Short-lived cache only: a user changing
 * their timezone in settings must be reflected immediately.
 */
@Service
public class UserZoneService {

    private static final long CACHE_MILLIS = 60_000L;

    private final UserRepository userRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public UserZoneService(UserRepository userRepository, UserPreferenceRepository preferenceRepository) {
        this.userRepository = userRepository;
        this.preferenceRepository = preferenceRepository;
    }

    @Transactional(readOnly = true)
    public ZoneId zoneOf(String userId) {
        Cached cached = cache.get(userId);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.loadedAt < CACHE_MILLIS) {
            return cached.zone;
        }
        String zoneId = "UTC";
        User user = userRepository.findByIdAndDeletedAtIsNull(userId).orElse(null);
        if (user != null && user.getTimezone() != null && !user.getTimezone().isBlank()) {
            zoneId = user.getTimezone();
        } else {
            zoneId = ZoneId.systemDefault().getId();
        }
        ZoneId zone = safeZone(zoneId);
        cache.put(userId, new Cached(zone, now));
        return zone;
    }

    public void invalidate(String userId) {
        cache.remove(userId);
    }

    private ZoneId safeZone(String zoneId) {
        try {
            return ZoneId.of(zoneId);
        } catch (RuntimeException ex) {
            return ZoneId.of("UTC");
        }
    }

    public ZoneId requireValid(String zoneId) {
        try {
            return ZoneId.of(zoneId);
        } catch (RuntimeException ex) {
            throw AppException.badRequest("Unknown timezone: " + zoneId);
        }
    }

    private record Cached(ZoneId zone, long loadedAt) {
    }
}