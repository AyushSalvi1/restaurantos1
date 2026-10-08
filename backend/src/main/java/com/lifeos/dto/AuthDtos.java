package com.lifeos.dto;

import com.lifeos.entity.enums.EmploymentType;
import com.lifeos.entity.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** Authentication, session and identity payloads. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank
            @Size(min = 10, max = 72)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                    message = "must contain at least one letter and one number")
            String password,
            @NotBlank @Size(max = 120) String fullName,
            @Size(max = 64) String timezone,
            @Size(max = 500) String avatarUrl
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password,
            @Size(max = 300) String deviceName
    ) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(String refreshToken, Boolean allDevices) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank
            @Size(min = 10, max = 72)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                    message = "must contain at least one letter and one number")
            String newPassword
    ) {
    }

    public record VerifyEmailRequest(@NotBlank String token) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank
            @Size(min = 10, max = 72)
            @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
                    message = "must contain at least one letter and one number")
            String newPassword
    ) {
    }

    public record UpdateProfileRequest(
            @Size(max = 120) String fullName,
            @Size(max = 500) String avatarUrl,
            @Size(max = 64) String timezone,
            @Size(max = 16) String locale,
            @Size(max = 120) String occupation,
            EmploymentType employmentType
    ) {
    }

    public record UserResponse(
            String id,
            String email,
            String fullName,
            Role role,
            String avatarUrl,
            String timezone,
            String locale,
            String occupation,
            EmploymentType employmentType,
            boolean emailVerified,
            boolean onboardingCompleted,
            Instant createdAt,
            Instant lastLoginAt
    ) {
    }

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            Instant accessTokenExpiresAt,
            UserResponse user
    ) {
    }

    public record SessionResponse(
            String id,
            String userAgent,
            String ipAddress,
            Instant issuedAt,
            Instant expiresAt,
            boolean current
    ) {
    }

    public record SessionList(List<SessionResponse> sessions) {
    }

    public record AccountDeletionRequest(@NotBlank String password, Boolean confirmEmail) {
    }

    public record MessageResponse(String message) {
        public static MessageResponse of(String message) {
            return new MessageResponse(message);
        }
    }
}