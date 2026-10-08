package com.lifeos.controller;

import com.lifeos.dto.AuthDtos;
import com.lifeos.mapper.UserMapper;
import com.lifeos.security.CurrentUser;
import com.lifeos.service.AuthService;
import com.lifeos.service.UserZoneService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Registration, login, token refresh, sessions, password recovery and account deletion. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserZoneService userZoneService;

    public AuthController(AuthService authService, UserZoneService userZoneService) {
        this.authService = authService;
        this.userZoneService = userZoneService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthDtos.AuthResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest request,
                                                          HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.register(request, httpRequest));
    }

    @PostMapping("/login")
    public AuthDtos.AuthResponse login(@Valid @RequestBody AuthDtos.LoginRequest request,
                                       HttpServletRequest httpRequest) {
        return authService.login(request, httpRequest);
    }

    @PostMapping("/refresh")
    public AuthDtos.AuthResponse refresh(@Valid @RequestBody AuthDtos.RefreshRequest request,
                                         HttpServletRequest httpRequest) {
        return authService.refresh(request, httpRequest);
    }

    @PostMapping("/logout")
    public AuthDtos.MessageResponse logout(@RequestBody(required = false) AuthDtos.LogoutRequest request) {
        boolean allDevices = request != null && Boolean.TRUE.equals(request.allDevices());
        authService.logout(CurrentUser.id(), request == null ? null : request.refreshToken(), allDevices);
        return new AuthDtos.MessageResponse(allDevices
                ? "Signed out of every device."
                : "Signed out.");
    }

    @GetMapping("/sessions")
    public AuthDtos.SessionList sessions(@RequestParam(required = false) String currentRefreshToken) {
        return authService.sessions(CurrentUser.id(), currentRefreshToken);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public AuthDtos.MessageResponse revokeSession(@PathVariable String sessionId) {
        authService.revokeSession(CurrentUser.id(), sessionId);
        return new AuthDtos.MessageResponse("Session revoked.");
    }

    /**
     * Always reports the same outcome whether or not the address is registered, so the endpoint
     * cannot be used to discover which emails have accounts.
     */
    @PostMapping("/forgot-password")
    public AuthDtos.MessageResponse forgotPassword(@Valid @RequestBody AuthDtos.ForgotPasswordRequest request,
                                                  HttpServletRequest httpRequest) {
        authService.forgotPassword(request.email(), httpRequest);
        return new AuthDtos.MessageResponse(
                "If that address has an account, a reset link has been sent to it.");
    }

    @PostMapping("/reset-password")
    public AuthDtos.MessageResponse resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest request) {
        authService.resetPassword(request);
        return new AuthDtos.MessageResponse("Your password has been changed. Sign in with the new one.");
    }

    @PostMapping("/verify-email")
    public AuthDtos.MessageResponse verifyEmail(@Valid @RequestBody AuthDtos.VerifyEmailRequest request) {
        authService.verifyEmail(request.token());
        return new AuthDtos.MessageResponse("Your email address is verified.");
    }

    @PostMapping("/resend-verification")
    public AuthDtos.MessageResponse resendVerification() {
        authService.resendVerification(CurrentUser.id());
        return new AuthDtos.MessageResponse("Verification email sent.");
    }

    @PostMapping("/change-password")
    public AuthDtos.MessageResponse changePassword(@Valid @RequestBody AuthDtos.ChangePasswordRequest request) {
        authService.changePassword(CurrentUser.id(), request);
        return new AuthDtos.MessageResponse("Your password has been changed.");
    }

    @GetMapping("/me")
    public AuthDtos.UserResponse me() {
        String userId = CurrentUser.id();
        return UserMapper.toResponse(authService.requireUser(userId));
    }

    @PutMapping("/me")
    public AuthDtos.UserResponse updateProfile(@Valid @RequestBody AuthDtos.UpdateProfileRequest request) {
        String userId = CurrentUser.id();
        AuthDtos.UserResponse updated = authService.updateProfile(userId, request);
        if (request.timezone() != null && !request.timezone().isBlank()) {
            userZoneService.invalidate(userId);
        }
        return updated;
    }

    @DeleteMapping("/me")
    public AuthDtos.MessageResponse deleteAccount(@Valid @RequestBody AuthDtos.AccountDeletionRequest request) {
        authService.deleteAccount(CurrentUser.id(), request.password());
        return new AuthDtos.MessageResponse(
                "Your account has been deleted along with the data stored for it.");
    }
}
