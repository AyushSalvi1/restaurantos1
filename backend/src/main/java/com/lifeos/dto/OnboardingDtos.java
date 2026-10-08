package com.lifeos.dto;

import com.lifeos.entity.enums.EmploymentType;
import com.lifeos.entity.enums.ProductivityStyle;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/** Onboarding wizard payloads and the personalisation preferences it produces. */
public final class OnboardingDtos {

    private OnboardingDtos() {
    }

    /**
     * Every field except {@code name} and {@code timezone} is optional so the wizard can be skipped.
     * Submitted once at the end of the wizard; partial progress is held client side.
     */
    public record OnboardingRequest(
            @Size(max = 120) String name,
            @Size(max = 500) String avatarUrl,
            @Size(max = 120) String occupation,
            EmploymentType employmentType,
            @Size(max = 64) String timezone,
            LocalTime workingHoursStart,
            LocalTime workingHoursEnd,
            List<String> primaryGoals,
            List<String> areasOfInterest,
            List<String> currentSkills,
            ProductivityStyle preferredProductivityStyle,
            @Min(10) @Max(180) Integer preferredFocusMinutes,
            @Min(0) @Max(60) Integer breakMinutes,
            @DecimalMin("1.0") @DecimalMax("80.0") BigDecimal weeklyProductivityHours,
            String financialGoals,
            String learningGoals,
            Map<String, Integer> lifeBalanceWeights,
            Boolean skipped
    ) {
    }

    public record OnboardingState(
            boolean completed,
            boolean skipped,
            AuthDtos.UserResponse user,
            PreferenceResponse preferences
    ) {
    }

    public record PreferenceResponse(
            LocalTime workingHoursStart,
            LocalTime workingHoursEnd,
            LocalTime dayStart,
            LocalTime dayEnd,
            ProductivityStyle preferredProductivityStyle,
            int preferredFocusMinutes,
            int breakMinutes,
            BigDecimal weeklyProductivityHours,
            List<String> areasOfInterest,
            List<String> currentSkills,
            List<String> primaryGoalAreas,
            List<String> financialGoals,
            List<String> learningGoals,
            Map<String, Integer> lifeBalanceWeights
    ) {
    }

    public record UpdatePreferenceRequest(
            LocalTime workingHoursStart,
            LocalTime workingHoursEnd,
            LocalTime dayStart,
            LocalTime dayEnd,
            ProductivityStyle preferredProductivityStyle,
            @Min(10) @Max(180) Integer preferredFocusMinutes,
            @Min(0) @Max(60) Integer breakMinutes,
            @DecimalMin("1.0") @DecimalMax("80.0") BigDecimal weeklyProductivityHours,
            List<String> areasOfInterest,
            List<String> currentSkills,
            List<String> primaryGoalAreas,
            List<String> financialGoals,
            List<String> learningGoals,
            Map<String, Integer> lifeBalanceWeights
    ) {
    }

    public record NotificationPreferenceRequest(
            Boolean inAppEnabled,
            Boolean emailEnabled,
            Boolean taskEnabled,
            Boolean goalEnabled,
            Boolean habitEnabled,
            Boolean calendarEnabled,
            Boolean financeEnabled,
            Boolean learningEnabled,
            Boolean aiInsightEnabled,
            Boolean reminderEnabled,
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd
    ) {
    }

    public record NotificationPreferenceResponse(
            boolean inAppEnabled,
            boolean emailEnabled,
            boolean taskEnabled,
            boolean goalEnabled,
            boolean habitEnabled,
            boolean calendarEnabled,
            boolean financeEnabled,
            boolean learningEnabled,
            boolean aiInsightEnabled,
            boolean reminderEnabled,
            LocalTime quietHoursStart,
            LocalTime quietHoursEnd
    ) {
    }
}