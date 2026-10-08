package com.lifeos.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifeos.dto.OnboardingDtos;
import com.lifeos.entity.NotificationPreference;
import com.lifeos.entity.User;
import com.lifeos.entity.UserPreference;
import com.lifeos.repository.NotificationPreferenceRepository;
import com.lifeos.repository.UserPreferenceRepository;
import com.lifeos.repository.UserRepository;
import com.lifeos.util.Csv;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Onboarding and personalisation settings.
 *
 * <p>Preferences are stored in a dedicated row rather than on the user, so a settings change never
 * competes with an account change. Comma-separated columns are used for short tag lists because they
 * are read with the user's own profile and never queried by content.</p>
 */
@Service
public class PreferenceService {

    private static final Logger log = LoggerFactory.getLogger(PreferenceService.class);
    private static final LocalTime DEFAULT_WORK_START = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_WORK_END = LocalTime.of(17, 0);
    private static final int DEFAULT_FOCUS_MINUTES = 50;
    private static final int DEFAULT_BREAK_MINUTES = 10;

    private final UserRepository userRepository;
    private final UserPreferenceRepository preferenceRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final UserZoneService userZoneService;
    private final ObjectMapper objectMapper;

    public PreferenceService(UserRepository userRepository,
                             UserPreferenceRepository preferenceRepository,
                             NotificationPreferenceRepository notificationPreferenceRepository,
                             UserZoneService userZoneService,
                             ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.preferenceRepository = preferenceRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.userZoneService = userZoneService;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------ onboarding

    @Transactional(readOnly = true)
    public OnboardingDtos.OnboardingState state(String userId) {
        User user = requireUser(userId);
        return new OnboardingDtos.OnboardingState(user.isOnboardingCompleted(),
                user.isOnboardingCompleted(), toUserResponse(user), preferences(userId));
    }

    @Transactional
    public OnboardingDtos.OnboardingState complete(String userId, OnboardingDtos.OnboardingRequest request) {
        User user = requireUser(userId);
        UserPreference preference = requirePreference(userId);

        if (request.name() != null && !request.name().isBlank()) {
            user.setFullName(request.name().strip().substring(0, Math.min(120, request.name().strip().length())));
        }
        if (request.avatarUrl() != null && !request.avatarUrl().isBlank()) {
            user.setAvatarUrl(request.avatarUrl().strip());
        }
        if (request.occupation() != null && !request.occupation().isBlank()) {
            user.setOccupation(request.occupation().strip());
        }
        if (request.employmentType() != null) {
            user.setEmploymentType(request.employmentType());
        }
        if (request.timezone() != null && !request.timezone().isBlank()) {
            user.setTimezone(userZoneService.requireValid(request.timezone().strip()).getId());
            userZoneService.invalidate(userId);
        }

        if (request.workingHoursStart() != null) {
            preference.setWorkingHoursStart(request.workingHoursStart());
        }
        if (request.workingHoursEnd() != null) {
            preference.setWorkingHoursEnd(request.workingHoursEnd());
        }
        preference.setDayStart(resolveDayStart(request, preference));
        preference.setDayEnd(resolveDayEnd(request, preference));
        if (request.preferredProductivityStyle() != null) {
            preference.setPreferredProductivityStyle(request.preferredProductivityStyle());
        }
        if (request.preferredFocusMinutes() != null) {
            preference.setPreferredFocusMinutes(request.preferredFocusMinutes());
        }
        if (request.breakMinutes() != null) {
            preference.setBreakMinutes(request.breakMinutes());
        }
        if (request.weeklyProductivityHours() != null) {
            preference.setWeeklyProductivityHours(request.weeklyProductivityHours());
        }
        preference.setAreasOfInterest(Csv.join(request.areasOfInterest()));
        preference.setCurrentSkills(Csv.join(request.currentSkills()));
        preference.setPrimaryGoalAreas(Csv.join(request.primaryGoals()));
        preference.setFinancialGoals(request.financialGoals());
        preference.setLearningGoals(request.learningGoals());
        if (request.lifeBalanceWeights() != null && !request.lifeBalanceWeights().isEmpty()) {
            preference.setLifeBalanceWeights(writeWeights(request.lifeBalanceWeights()));
        }

        user.setOnboardingCompleted(true);
        userRepository.save(user);
        preferenceRepository.save(preference);
        return new OnboardingDtos.OnboardingState(true, Boolean.TRUE.equals(request.skipped()),
                toUserResponse(user), preferences(userId));
    }

    @Transactional
    public OnboardingDtos.OnboardingState skip(String userId) {
        User user = requireUser(userId);
        user.setOnboardingCompleted(true);
        userRepository.save(user);
        return new OnboardingDtos.OnboardingState(true, true, toUserResponse(user), preferences(userId));
    }

    // ---------------------------------------------------------- preferences

    @Transactional(readOnly = true)
    public OnboardingDtos.PreferenceResponse preferences(String userId) {
        UserPreference preference = preferenceRepository.findByUserId(userId).orElseGet(() -> defaults(userId));
        return new OnboardingDtos.PreferenceResponse(
                preference.getWorkingHoursStart(),
                preference.getWorkingHoursEnd(),
                preference.getDayStart(),
                preference.getDayEnd(),
                preference.getPreferredProductivityStyle(),
                preference.getPreferredFocusMinutes(),
                preference.getBreakMinutes(),
                preference.getWeeklyProductivityHours(),
                Csv.splitToList(preference.getAreasOfInterest()),
                Csv.splitToList(preference.getCurrentSkills()),
                Csv.splitToList(preference.getPrimaryGoalAreas()),
                Csv.splitToList(preference.getFinancialGoals()),
                Csv.splitToList(preference.getLearningGoals()),
                readWeights(preference.getLifeBalanceWeights()));
    }

    @Transactional
    public OnboardingDtos.PreferenceResponse update(String userId,
                                                   OnboardingDtos.UpdatePreferenceRequest request) {
        UserPreference preference = requirePreference(userId);
        if (request.workingHoursStart() != null) {
            preference.setWorkingHoursStart(request.workingHoursStart());
        }
        if (request.workingHoursEnd() != null) {
            preference.setWorkingHoursEnd(request.workingHoursEnd());
        }
        if (request.dayStart() != null) {
            preference.setDayStart(request.dayStart());
        }
        if (request.dayEnd() != null) {
            preference.setDayEnd(request.dayEnd());
        }
        if (request.preferredProductivityStyle() != null) {
            preference.setPreferredProductivityStyle(request.preferredProductivityStyle());
        }
        if (request.preferredFocusMinutes() != null) {
            preference.setPreferredFocusMinutes(request.preferredFocusMinutes());
        }
        if (request.breakMinutes() != null) {
            preference.setBreakMinutes(request.breakMinutes());
        }
        if (request.weeklyProductivityHours() != null) {
            preference.setWeeklyProductivityHours(request.weeklyProductivityHours());
        }
        if (request.areasOfInterest() != null) {
            preference.setAreasOfInterest(Csv.join(request.areasOfInterest()));
        }
        if (request.currentSkills() != null) {
            preference.setCurrentSkills(Csv.join(request.currentSkills()));
        }
        if (request.primaryGoalAreas() != null) {
            preference.setPrimaryGoalAreas(Csv.join(request.primaryGoalAreas()));
        }
        if (request.financialGoals() != null) {
            preference.setFinancialGoals(Csv.join(request.financialGoals()));
        }
        if (request.learningGoals() != null) {
            preference.setLearningGoals(Csv.join(request.learningGoals()));
        }
        if (request.lifeBalanceWeights() != null) {
            preference.setLifeBalanceWeights(writeWeights(request.lifeBalanceWeights()));
        }
        preferenceRepository.save(preference);
        return preferences(userId);
    }

    // -------------------------------------------------- notification settings

    @Transactional(readOnly = true)
    public OnboardingDtos.NotificationPreferenceResponse notificationPreferences(String userId) {
        NotificationPreference preference = notificationPreferenceRepository.findByUserId(userId).orElse(null);
        if (preference == null) {
            return new OnboardingDtos.NotificationPreferenceResponse(
                    true, true, true, true, true, true, true, true, true, true, null, null);
        }
        return new OnboardingDtos.NotificationPreferenceResponse(
                preference.isInAppEnabled(), preference.isEmailEnabled(),
                preference.isTaskEnabled(), preference.isGoalEnabled(),
                preference.isHabitEnabled(), preference.isCalendarEnabled(),
                preference.isFinanceEnabled(), preference.isLearningEnabled(),
                preference.isAiInsightEnabled(), preference.isReminderEnabled(),
                preference.getQuietHoursStart(), preference.getQuietHoursEnd());
    }

    @Transactional
    public OnboardingDtos.NotificationPreferenceResponse updateNotificationPreferences(
            String userId, OnboardingDtos.NotificationPreferenceRequest request) {
        NotificationPreference preference = notificationPreferenceRepository.findByUserId(userId)
                .orElseGet(() -> {
                    NotificationPreference created = new NotificationPreference();
                    created.setUserId(userId);
                    return created;
                });
        if (request.inAppEnabled() != null) {
            preference.setInAppEnabled(request.inAppEnabled());
        }
        if (request.emailEnabled() != null) {
            preference.setEmailEnabled(request.emailEnabled());
        }
        if (request.taskEnabled() != null) {
            preference.setTaskEnabled(request.taskEnabled());
        }
        if (request.goalEnabled() != null) {
            preference.setGoalEnabled(request.goalEnabled());
        }
        if (request.habitEnabled() != null) {
            preference.setHabitEnabled(request.habitEnabled());
        }
        if (request.calendarEnabled() != null) {
            preference.setCalendarEnabled(request.calendarEnabled());
        }
        if (request.financeEnabled() != null) {
            preference.setFinanceEnabled(request.financeEnabled());
        }
        if (request.learningEnabled() != null) {
            preference.setLearningEnabled(request.learningEnabled());
        }
        if (request.aiInsightEnabled() != null) {
            preference.setAiInsightEnabled(request.aiInsightEnabled());
        }
        if (request.reminderEnabled() != null) {
            preference.setReminderEnabled(request.reminderEnabled());
        }
        preference.setQuietHoursStart(request.quietHoursStart());
        preference.setQuietHoursEnd(request.quietHoursEnd());
        notificationPreferenceRepository.save(preference);
        return notificationPreferences(userId);
    }

    // ------------------------------------------------------------ internals

    private LocalTime resolveDayStart(OnboardingDtos.OnboardingRequest request, UserPreference preference) {
        if (request.workingHoursStart() != null) {
            return request.workingHoursStart().minusHours(1);
        }
        return preference.getDayStart() != null ? preference.getDayStart() : DEFAULT_WORK_START.minusHours(1);
    }

    private LocalTime resolveDayEnd(OnboardingDtos.OnboardingRequest request, UserPreference preference) {
        if (request.workingHoursEnd() != null) {
            return request.workingHoursEnd().plusHours(2);
        }
        return preference.getDayEnd() != null ? preference.getDayEnd() : DEFAULT_WORK_END.plusHours(2);
    }

    private User requireUser(String userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    private UserPreference requirePreference(String userId) {
        return preferenceRepository.findByUserId(userId).orElseGet(() -> {
            UserPreference created = defaults(userId);
            return preferenceRepository.save(created);
        });
    }

    private UserPreference defaults(String userId) {
        UserPreference preference = new UserPreference();
        preference.setUserId(userId);
        preference.setWorkingHoursStart(DEFAULT_WORK_START);
        preference.setWorkingHoursEnd(DEFAULT_WORK_END);
        preference.setDayStart(DEFAULT_WORK_START.minusHours(1));
        preference.setDayEnd(DEFAULT_WORK_END.plusHours(2));
        preference.setPreferredFocusMinutes(DEFAULT_FOCUS_MINUTES);
        preference.setBreakMinutes(DEFAULT_BREAK_MINUTES);
        preference.setWeeklyProductivityHours(new BigDecimal("20.00"));
        return preference;
    }

    private String writeWeights(Map<String, Integer> weights) {
        Map<String, Integer> sanitised = new LinkedHashMap<>();
        weights.forEach((key, value) -> {
            if (value != null) {
                sanitised.put(key, Math.max(0, Math.min(100, value)));
            }
        });
        try {
            return objectMapper.writeValueAsString(sanitised);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Life balance weights could not be stored", ex);
        }
    }

    private Map<String, Integer> readWeights(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            log.warn("Ignoring unreadable stored life balance weights");
            return Map.of();
        }
    }

    /** Mirrors the shape the auth endpoints return so the wizard can render the account inline. */
    private com.lifeos.dto.AuthDtos.UserResponse toUserResponse(User user) {
        return com.lifeos.mapper.UserMapper.toResponse(user);
    }
}
