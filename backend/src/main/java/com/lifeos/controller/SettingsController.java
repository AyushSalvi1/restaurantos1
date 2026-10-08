package com.lifeos.controller;

import com.lifeos.dto.OnboardingDtos;
import com.lifeos.security.CurrentUser;
import com.lifeos.service.PreferenceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Onboarding wizard state and personalisation settings. */
@RestController
@RequestMapping("/api")
public class SettingsController {

    private final PreferenceService preferenceService;

    public SettingsController(PreferenceService preferenceService) {
        this.preferenceService = preferenceService;
    }

    @GetMapping("/onboarding")
    public OnboardingDtos.OnboardingState onboardingState() {
        return preferenceService.state(CurrentUser.id());
    }

    @PostMapping("/onboarding")
    public OnboardingDtos.OnboardingState completeOnboarding(
            @Valid @RequestBody OnboardingDtos.OnboardingRequest request) {
        return preferenceService.complete(CurrentUser.id(), request);
    }

    @PostMapping("/onboarding/skip")
    public OnboardingDtos.OnboardingState skipOnboarding() {
        return preferenceService.skip(CurrentUser.id());
    }

    @GetMapping("/settings/preferences")
    public OnboardingDtos.PreferenceResponse preferences() {
        return preferenceService.preferences(CurrentUser.id());
    }

    @PutMapping("/settings/preferences")
    public OnboardingDtos.PreferenceResponse updatePreferences(
            @Valid @RequestBody OnboardingDtos.UpdatePreferenceRequest request) {
        return preferenceService.update(CurrentUser.id(), request);
    }

    @GetMapping("/settings/notifications")
    public OnboardingDtos.NotificationPreferenceResponse notificationPreferences() {
        return preferenceService.notificationPreferences(CurrentUser.id());
    }

    @PutMapping("/settings/notifications")
    public OnboardingDtos.NotificationPreferenceResponse updateNotificationPreferences(
            @Valid @RequestBody OnboardingDtos.NotificationPreferenceRequest request) {
        return preferenceService.updateNotificationPreferences(CurrentUser.id(), request);
    }
}
