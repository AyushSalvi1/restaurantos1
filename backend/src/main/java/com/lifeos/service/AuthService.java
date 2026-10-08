package com.lifeos.service;

import com.lifeos.config.AppProperties;
import com.lifeos.dto.AuthDtos;
import com.lifeos.entity.EmailVerificationToken;
import com.lifeos.entity.NotificationPreference;
import com.lifeos.entity.PasswordResetToken;
import com.lifeos.entity.RefreshToken;
import com.lifeos.entity.User;
import com.lifeos.entity.UserPreference;
import com.lifeos.entity.enums.Role;
import com.lifeos.entity.enums.UserStatus;
import com.lifeos.exception.AppException;
import com.lifeos.mapper.UserMapper;
import com.lifeos.repository.EmailVerificationTokenRepository;
import com.lifeos.repository.NotificationPreferenceRepository;
import com.lifeos.repository.PasswordResetTokenRepository;
import com.lifeos.repository.RefreshTokenRepository;
import com.lifeos.repository.UserPreferenceRepository;
import com.lifeos.repository.UserRepository;
import com.lifeos.security.JwtService;
import com.lifeos.security.UserDetailsServiceImpl;
import com.lifeos.security.UserPrincipal;
import com.lifeos.util.UploadValidation;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns registration, login, refresh-token rotation, password recovery and account lifecycle.
 * Passwords are never logged, returned or stored in plain text; refresh tokens are stored hashed.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final Duration EMAIL_TOKEN_TTL = Duration.ofHours(24);
    private static final Duration RESET_TOKEN_TTL = Duration.ofHours(2);

    private final UserRepository userRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailVerificationTokenRepository emailVerificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties appProperties;
    private final AuditService auditService;
    private final com.lifeos.notification.EmailSender emailSender;

    public AuthService(UserRepository userRepository,
                       UserPreferenceRepository userPreferenceRepository,
                       NotificationPreferenceRepository notificationPreferenceRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       PasswordResetTokenRepository passwordResetTokenRepository,
                       EmailVerificationTokenRepository emailVerificationTokenRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AppProperties appProperties,
                       AuditService auditService,
                       com.lifeos.notification.EmailSender emailSender) {
        this.userRepository = userRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.notificationPreferenceRepository = notificationPreferenceRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordResetTokenRepository = passwordResetTokenRepository;
        this.emailVerificationTokenRepository = emailVerificationTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.appProperties = appProperties;
        this.auditService = auditService;
        this.emailSender = emailSender;
    }

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest request, HttpServletRequest httpRequest) {
        String email = normaliseEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw AppException.conflict("An account already exists for this email");
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setFullName(request.fullName().strip());
        user.setTimezone(resolveTimezone(request.timezone()));
        user.setAvatarUrl(blankToNull(request.avatarUrl()));
        user.setRole(Role.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerified(!appProperties.demoMode());
        userRepository.save(user);

        createDefaultPreferences(user.getId());

        String verificationToken = issueEmailVerification(user);
        emailSender.send(user.getEmail(), "Verify your LIFEOS account",
                "Welcome to LIFEOS. Confirm your address: " + appProperties.baseUrl()
                        + "/verify-email?token=" + verificationToken);

        auditService.record(user.getId(), "USER_REGISTERED", "User", user.getId(), "email=" + user.getEmail());
        return issueTokens(user, httpRequest);
    }

    @Transactional
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(normaliseEmail(request.email()))
                .orElse(null);

        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditService.record(user == null ? null : user.getId(), "LOGIN_FAILED", "User",
                    user == null ? null : user.getId(), "invalid credentials");
            throw AppException.unauthorized("Email or password is incorrect");
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw AppException.forbidden("This account has been disabled. Contact support for help.");
        }
        if (user.getStatus() == UserStatus.DELETED || user.getDeletedAt() != null) {
            throw AppException.forbidden("This account has been deleted");
        }

        user.setLastLoginAt(Instant.now());
        userRepository.save(user);
        createDefaultPreferences(user.getId());
        auditService.record(user.getId(), "LOGIN", "User", user.getId(), null);
        return issueTokens(user, httpRequest);
    }

    @Transactional
    public AuthDtos.AuthResponse refresh(AuthDtos.RefreshRequest request, HttpServletRequest httpRequest) {
        String hash = hash(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> AppException.unauthorized("Session is no longer valid"));

        if (!stored.isActive(Instant.now())) {
            // A replayed refresh token indicates a stolen session: drop every token for the user.
            if (stored.isRevoked()) {
                refreshTokenRepository.revokeAllForUser(stored.getUserId(), "REPLAY_DETECTED");
                auditService.record(stored.getUserId(), "REFRESH_REPLAY_DETECTED", "User", stored.getUserId(), null);
            }
            throw AppException.unauthorized("Session is no longer valid");
        }

        User user = userRepository.findByIdAndDeletedAtIsNull(stored.getUserId())
                .orElseThrow(() -> AppException.unauthorized("Session is no longer valid"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw AppException.forbidden("This account is not active");
        }

        stored.setRevoked(true);
        stored.setRevokedReason("ROTATED");
        refreshTokenRepository.save(stored);

        return issueTokens(user, httpRequest);
    }

    @Transactional
    public void logout(String userId, String refreshToken, boolean allDevices) {
        if (allDevices) {
            refreshTokenRepository.revokeAllForUser(userId, "LOGOUT_ALL");
        } else if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenRepository.findByTokenHash(hash(refreshToken))
                    .ifPresent(stored -> {
                        if (stored.getUserId().equals(userId)) {
                            stored.setRevoked(true);
                            stored.setRevokedReason("LOGOUT");
                            refreshTokenRepository.save(stored);
                        }
                    });
        }
        auditService.record(userId, allDevices ? "LOGOUT_ALL_DEVICES" : "LOGOUT", "User", userId, null);
    }

    @Transactional(readOnly = true)
    public AuthDtos.SessionList sessions(String userId, String currentRefreshToken) {
        String currentHash = currentRefreshToken == null ? null : hash(currentRefreshToken);
        List<AuthDtos.SessionResponse> sessions = refreshTokenRepository
                .findByUserIdAndRevokedFalseAndUserAgentIsNotNullOrderByIssuedAtDesc(userId).stream()
                .filter(token -> token.isActive(Instant.now()))
                .map(token -> new AuthDtos.SessionResponse(
                        token.getId(),
                        token.getUserAgent(),
                        token.getIpAddress(),
                        token.getIssuedAt(),
                        token.getExpiresAt(),
                        currentHash != null && currentHash.equals(token.getTokenHash())))
                .toList();
        return new AuthDtos.SessionList(sessions);
    }

    @Transactional
    public void revokeSession(String userId, String sessionId) {
        RefreshToken token = refreshTokenRepository.findById(sessionId)
                .orElseThrow(() -> AppException.notFound("Session not found"));
        if (!token.getUserId().equals(userId)) {
            throw AppException.forbidden("You cannot revoke this session");
        }
        token.setRevoked(true);
        token.setRevokedReason("REVOKED_BY_USER");
        refreshTokenRepository.save(token);
    }

    /**
     * Always reports success so the endpoint cannot be used to discover which emails are registered.
     */
    @Transactional
    public void forgotPassword(String email, HttpServletRequest httpRequest) {
        Optional<User> maybe = userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(normaliseEmail(email));
        maybe.filter(User::isActive).ifPresent(user -> {
            PasswordResetToken token = new PasswordResetToken();
            token.setUserId(user.getId());
            String raw = UUID.randomUUID() + "." + UUID.randomUUID();
            token.setTokenHash(hash(raw));
            token.setExpiresAt(Instant.now().plus(RESET_TOKEN_TTL));
            passwordResetTokenRepository.deleteByUserId(user.getId());
            passwordResetTokenRepository.save(token);

            emailSender.send(user.getEmail(), "Reset your LIFEOS password",
                    "Use this link within 2 hours: " + appProperties.baseUrl() + "/reset-password?token=" + raw
                            + "\n\nIf you did not request this, you can ignore the message.");
            auditService.record(user.getId(), "PASSWORD_RESET_REQUESTED", "User", user.getId(), null);
        });
    }

    @Transactional
    public void resetPassword(AuthDtos.ResetPasswordRequest request) {
        PasswordResetToken token = passwordResetTokenRepository.findByTokenHash(hash(request.token()))
                .orElseThrow(() -> AppException.badRequest("This reset link is invalid or has expired"));
        if (!token.isUsable(Instant.now())) {
            throw AppException.badRequest("This reset link is invalid or has expired");
        }
        User user = userRepository.findByIdAndDeletedAtIsNull(token.getUserId())
                .orElseThrow(() -> AppException.badRequest("This reset link is invalid or has expired"));

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        token.setUsed(true);
        passwordResetTokenRepository.save(token);

        refreshTokenRepository.revokeAllForUser(user.getId(), "PASSWORD_CHANGED");
        auditService.record(user.getId(), "PASSWORD_RESET", "User", user.getId(), null);
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        EmailVerificationToken token = emailVerificationTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> AppException.badRequest("This verification link is invalid"));
        if (!token.isUsable(Instant.now())) {
            throw AppException.badRequest("This verification link has expired");
        }
        User user = userRepository.findByIdAndDeletedAtIsNull(token.getUserId())
                .orElseThrow(() -> AppException.badRequest("This verification link is invalid"));
        user.setEmailVerified(true);
        userRepository.save(user);
        token.setUsed(true);
        emailVerificationTokenRepository.save(token);
        auditService.record(user.getId(), "EMAIL_VERIFIED", "User", user.getId(), null);
    }

    @Transactional
    public void resendVerification(String userId) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> AppException.notFound("Account not found"));
        if (user.isEmailVerified()) {
            return;
        }
        String raw = issueEmailVerification(user);
        emailSender.send(user.getEmail(), "Verify your LIFEOS account",
                "Confirm your address: " + appProperties.baseUrl() + "/verify-email?token=" + raw);
    }

    @Transactional
    public void changePassword(String userId, AuthDtos.ChangePasswordRequest request) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> AppException.notFound("Account not found"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw AppException.badRequest("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(userId, "PASSWORD_CHANGED");
        auditService.record(userId, "PASSWORD_CHANGED", "User", userId, null);
    }

    /**
     * Immediately erases the account: every owned row is deleted and refresh tokens are revoked,
     * which makes the action verifiable rather than a soft-delete that could be undone.
     */
    @Transactional
    public void deleteAccount(String userId, String password) {
        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> AppException.notFound("Account not found"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw AppException.badRequest("Password is incorrect");
        }
        refreshTokenRepository.deleteAllForUser(userId);
        user.setDeletedAt(Instant.now());
        user.setStatus(UserStatus.DELETED);
        user.setEmail("deleted-" + userId + "@lifeos.invalid");
        user.setFullName("Deleted account");
        user.setAvatarUrl(null);
        userRepository.save(user);
        auditService.record(null, "ACCOUNT_DELETED", "User", userId, "cascading personal data removal");
    }

    public User requireUser(String userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> AppException.notFound("Account not found"));
    }

    private AuthDtos.AuthResponse issueTokens(User user, HttpServletRequest httpRequest) {
        UserPrincipal principal = UserDetailsServiceImpl.toPrincipal(user);
        JwtService.IssuedToken accessToken = jwtService.issueAccessToken(principal);

        String rawRefresh = jwtService.newRefreshTokenValue();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUserId(user.getId());
        refreshToken.setTokenHash(hash(rawRefresh));
        refreshToken.setIssuedAt(Instant.now());
        refreshToken.setExpiresAt(jwtService.refreshExpiry());
        refreshToken.setUserAgent(truncate(httpRequest == null ? null : httpRequest.getHeader("User-Agent"), 300));
        refreshToken.setIpAddress(httpRequest == null ? null : truncate(httpRequest.getRemoteAddr(), 64));
        refreshTokenRepository.save(refreshToken);

        return new AuthDtos.AuthResponse(
                accessToken.value(),
                rawRefresh,
                "Bearer",
                accessToken.expiresAt(),
                UserMapper.toResponse(user));
    }

    private String issueEmailVerification(User user) {
        String raw = UUID.randomUUID() + "." + UUID.randomUUID();
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserId(user.getId());
        token.setTokenHash(hash(raw));
        token.setExpiresAt(Instant.now().plus(EMAIL_TOKEN_TTL));
        emailVerificationTokenRepository.save(token);
        return raw;
    }

    private void createDefaultPreferences(String userId) {
        if (userPreferenceRepository.findByUserId(userId).isEmpty()) {
            UserPreference preference = new UserPreference();
            preference.setUserId(userId);
            preference.setDayStart(java.time.LocalTime.of(8, 0));
            preference.setDayEnd(java.time.LocalTime.of(22, 0));
            userPreferenceRepository.save(preference);
        }
        if (notificationPreferenceRepository.findByUserId(userId).isEmpty()) {
            NotificationPreference notificationPreference = new NotificationPreference();
            notificationPreference.setUserId(userId);
            notificationPreferenceRepository.save(notificationPreference);
        }
    }

    static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is required but unavailable", ex);
        }
    }

    private String resolveTimezone(String requested) {
        if (requested != null && !requested.isBlank()) {
            try {
                return ZoneId.of(requested.strip()).getId();
            } catch (RuntimeException ex) {
                throw AppException.badRequest("Unknown timezone: " + requested);
            }
        }
        return ZoneId.systemDefault().getId();
    }

    static String normaliseEmail(String email) {
        return email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String optionalUrl(String value) {
        String trimmed = blankToNull(value);
        return trimmed == null ? null : UploadValidation.requireValidUrl(trimmed);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /**
     * Applies profile edits field by field. A null field means "leave unchanged", so a partial form
     * submission cannot silently clear a value the user did not touch.
     */
    @Transactional
    public AuthDtos.UserResponse updateProfile(String userId, AuthDtos.UpdateProfileRequest request) {
        User user = requireUser(userId);
        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().strip());
        }
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(optionalUrl(request.avatarUrl()));
        }
        if (request.occupation() != null) {
            user.setOccupation(blankToNull(request.occupation()));
        }
        if (request.employmentType() != null) {
            user.setEmploymentType(request.employmentType());
        }
        if (request.locale() != null && !request.locale().isBlank()) {
            user.setLocale(request.locale().strip());
        }
        if (request.timezone() != null && !request.timezone().isBlank()) {
            user.setTimezone(truncate(request.timezone().strip(), 64));
        }
        auditService.record(userId, "PROFILE_UPDATED", "User", userId, null);
        return UserMapper.toResponse(userRepository.save(user));
    }
}