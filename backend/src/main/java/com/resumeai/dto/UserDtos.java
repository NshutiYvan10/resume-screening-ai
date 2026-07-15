package com.resumeai.dto;

import com.resumeai.domain.User;
import com.resumeai.domain.enums.Role;
import com.resumeai.domain.enums.UserStatus;

import java.time.Instant;
import java.util.UUID;

public final class UserDtos {

    private UserDtos() {
    }

    public record UserResponse(
            UUID id,
            String email,
            String fullName,
            String phone,
            Role role,
            UserStatus status,
            boolean emailVerified,
            UUID companyId,
            String companyName,
            Instant lastLoginAt,
            Instant createdAt) {

        public static UserResponse from(User u) {
            return new UserResponse(
                    u.getId(),
                    u.getEmail(),
                    u.getFullName(),
                    u.getPhone(),
                    u.getRole(),
                    u.getStatus(),
                    u.isEmailVerified(),
                    u.getCompany() != null ? u.getCompany().getId() : null,
                    u.getCompany() != null ? u.getCompany().getName() : null,
                    u.getLastLoginAt(),
                    u.getCreatedAt());
        }
    }
}
