package com.resumeai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100) String password,
            @Size(max = 40) String phone) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    public record LogoutRequest(String refreshToken) {
    }

    public record VerifyEmailRequest(@NotBlank String token) {
    }

    public record ResendVerificationRequest(@NotBlank @Email String email) {
    }

    public record ForgotPasswordRequest(@NotBlank @Email String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank @Size(min = 8, max = 100) String newPassword) {
    }

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 8, max = 100) String newPassword) {
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 150) String fullName,
            @Size(max = 40) String phone) {
    }

    public record AuthResponse(String accessToken, String refreshToken, UserDtos.UserResponse user) {
    }

    public record MessageResponse(String message) {
    }
}
