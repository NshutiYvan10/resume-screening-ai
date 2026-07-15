package com.resumeai.service;

import com.resumeai.common.TokenUtil;
import com.resumeai.common.exception.ApiException;
import com.resumeai.config.AppProperties;
import com.resumeai.domain.RefreshToken;
import com.resumeai.domain.User;
import com.resumeai.domain.UserToken;
import com.resumeai.domain.enums.NotificationType;
import com.resumeai.domain.enums.Role;
import com.resumeai.domain.enums.UserStatus;
import com.resumeai.domain.enums.UserTokenType;
import com.resumeai.dto.AuthDtos.*;
import com.resumeai.dto.UserDtos.UserResponse;
import com.resumeai.repository.RefreshTokenRepository;
import com.resumeai.repository.UserRepository;
import com.resumeai.repository.UserTokenRepository;
import com.resumeai.security.JwtService;
import com.resumeai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserTokenRepository userTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final AppProperties properties;

    // ------------------------------------------------------------ login

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(() -> new BadCredentialsException("bad credentials"));

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            auditService.log(null, user.getCompany() != null ? user.getCompany().getId() : null,
                    "LOGIN_FAILED", "USER", user.getId().toString(), Map.of("email", user.getEmail()));
            throw new BadCredentialsException("bad credentials");
        }
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new DisabledException("disabled");
        }
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            throw ApiException.forbidden("Please verify your email address before signing in");
        }

        user.setLastLoginAt(Instant.now());
        UserPrincipal principal = new UserPrincipal(user);
        String refreshToken = issueRefreshToken(user);

        auditService.log(principal, principal.getCompanyId(), "LOGIN", "USER",
                user.getId().toString(), Map.of("email", user.getEmail()));

        return new AuthResponse(jwtService.generateAccessToken(principal), refreshToken,
                UserResponse.from(user));
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        String hash = TokenUtil.sha256(request.refreshToken());
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> ApiException.unauthorized("Invalid refresh token"));

        if (!stored.isActive()) {
            throw ApiException.unauthorized("Refresh token expired or revoked");
        }
        User user = stored.getUser();
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.unauthorized("Account is not active");
        }

        // rotation: revoke the used token, issue a fresh one
        stored.setRevokedAt(Instant.now());
        String newRefreshToken = issueRefreshToken(user);
        UserPrincipal principal = new UserPrincipal(user);

        return new AuthResponse(jwtService.generateAccessToken(principal), newRefreshToken,
                UserResponse.from(user));
    }

    @Transactional
    public void logout(LogoutRequest request) {
        if (request.refreshToken() != null) {
            refreshTokenRepository.findByTokenHash(TokenUtil.sha256(request.refreshToken()))
                    .ifPresent(token -> token.setRevokedAt(Instant.now()));
        }
    }

    private String issueRefreshToken(User user) {
        String raw = TokenUtil.generateToken();
        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash(TokenUtil.sha256(raw))
                .expiresAt(Instant.now().plus(Duration.ofDays(properties.getJwt().getRefreshTokenDays())))
                .build();
        refreshTokenRepository.save(token);
        return raw;
    }

    // ------------------------------------------------------- registration

    @Transactional
    public MessageResponse registerCandidate(RegisterRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw ApiException.conflict("An account with this email already exists");
        }
        User user = User.builder()
                .email(request.email().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .phone(request.phone())
                .role(Role.CANDIDATE)
                .status(UserStatus.PENDING_VERIFICATION)
                .build();
        userRepository.save(user);

        sendVerificationEmail(user);

        auditService.log(null, null, "CANDIDATE_REGISTERED", "USER", user.getId().toString(),
                Map.of("email", user.getEmail()));

        return new MessageResponse("Registration successful. Please check your email to verify your account.");
    }

    @Transactional
    public MessageResponse verifyEmail(VerifyEmailRequest request) {
        UserToken token = userTokenRepository
                .findByTokenHashAndType(TokenUtil.sha256(request.token()), UserTokenType.EMAIL_VERIFICATION)
                .orElseThrow(() -> ApiException.badRequest("Invalid verification link"));

        if (token.getUsedAt() != null) {
            return new MessageResponse("Email already verified. You can sign in.");
        }
        if (token.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("Verification link has expired. Please request a new one.");
        }

        User user = token.getUser();
        user.setEmailVerified(true);
        if (user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            user.setStatus(UserStatus.ACTIVE);
        }
        token.setUsedAt(Instant.now());

        notificationService.notify(user, NotificationType.ACCOUNT, "Welcome to ResumeAI",
                "Your email has been verified. You can now browse jobs and submit applications.",
                "/jobs", false);

        auditService.log(null, null, "EMAIL_VERIFIED", "USER", user.getId().toString(),
                Map.of("email", user.getEmail()));

        return new MessageResponse("Email verified successfully. You can now sign in.");
    }

    @Transactional
    public MessageResponse resendVerification(ResendVerificationRequest request) {
        userRepository.findByEmailIgnoreCase(request.email()).ifPresent(user -> {
            if (!user.isEmailVerified() && user.getStatus() == UserStatus.PENDING_VERIFICATION) {
                userTokenRepository.deleteByUserIdAndType(user.getId(), UserTokenType.EMAIL_VERIFICATION);
                sendVerificationEmail(user);
            }
        });
        // Always the same response - do not leak whether the account exists
        return new MessageResponse("If an unverified account exists for this email, a new link has been sent.");
    }

    private void sendVerificationEmail(User user) {
        String raw = TokenUtil.generateToken();
        userTokenRepository.save(UserToken.builder()
                .user(user)
                .tokenHash(TokenUtil.sha256(raw))
                .type(UserTokenType.EMAIL_VERIFICATION)
                .expiresAt(Instant.now().plus(Duration.ofHours(48)))
                .build());

        String url = emailService.frontendUrl("/verify-email?token=" + raw);
        String html = emailService.template("Verify your email address",
                "<p>Hi " + user.getFullName() + ",</p>"
                        + "<p>Thanks for creating a ResumeAI account. Please confirm your email address "
                        + "to activate your account. This link expires in 48 hours.</p>",
                "Verify email", url);
        emailService.send(user.getEmail(), "Verify your ResumeAI account", html, url);
    }

    // ---------------------------------------------------- password reset

    @Transactional
    public MessageResponse forgotPassword(ForgotPasswordRequest request) {
        userRepository.findByEmailIgnoreCase(request.email()).ifPresent(user -> {
            if (user.getStatus() == UserStatus.DISABLED) {
                return;
            }
            userTokenRepository.deleteByUserIdAndType(user.getId(), UserTokenType.PASSWORD_RESET);
            String raw = TokenUtil.generateToken();
            userTokenRepository.save(UserToken.builder()
                    .user(user)
                    .tokenHash(TokenUtil.sha256(raw))
                    .type(UserTokenType.PASSWORD_RESET)
                    .expiresAt(Instant.now().plus(Duration.ofHours(2)))
                    .build());

            String url = emailService.frontendUrl("/reset-password?token=" + raw);
            String html = emailService.template("Reset your password",
                    "<p>Hi " + user.getFullName() + ",</p>"
                            + "<p>We received a request to reset your ResumeAI password. "
                            + "This link expires in 2 hours. If you didn't request this, you can ignore this email.</p>",
                    "Reset password", url);
            emailService.send(user.getEmail(), "Reset your ResumeAI password", html, url);
        });
        return new MessageResponse("If an account exists for this email, a password reset link has been sent.");
    }

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest request) {
        UserToken token = userTokenRepository
                .findByTokenHashAndType(TokenUtil.sha256(request.token()), UserTokenType.PASSWORD_RESET)
                .orElseThrow(() -> ApiException.badRequest("Invalid or expired reset link"));

        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("Invalid or expired reset link");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        token.setUsedAt(Instant.now());
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());

        auditService.log(null, user.getCompany() != null ? user.getCompany().getId() : null,
                "PASSWORD_RESET", "USER", user.getId().toString(), Map.of("email", user.getEmail()));

        return new MessageResponse("Password has been reset. You can now sign in with your new password.");
    }

    // ------------------------------------------------------------ profile

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        return UserResponse.from(userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found")));
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        user.setFullName(request.fullName());
        user.setPhone(request.phone());
        auditService.log("PROFILE_UPDATED", "USER", user.getId().toString(), Map.of());
        return UserResponse.from(user);
    }

    @Transactional
    public MessageResponse changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
        auditService.log("PASSWORD_CHANGED", "USER", user.getId().toString(), Map.of());
        return new MessageResponse("Password changed successfully");
    }

    // ------------------------------------------------------- housekeeping

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredRefreshTokens() {
        refreshTokenRepository.deleteExpired(Instant.now().minus(Duration.ofDays(1)));
    }
}
