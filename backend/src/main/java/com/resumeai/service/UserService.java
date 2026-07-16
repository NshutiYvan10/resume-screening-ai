package com.resumeai.service;

import com.resumeai.common.exception.ApiException;
import com.resumeai.domain.User;
import com.resumeai.domain.enums.Role;
import com.resumeai.domain.enums.UserStatus;
import com.resumeai.dto.CommonDtos.PageResponse;
import com.resumeai.dto.UserDtos.UserResponse;
import com.resumeai.repository.RefreshTokenRepository;
import com.resumeai.repository.UserRepository;
import com.resumeai.security.SecurityUtils;
import com.resumeai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> list(UUID companyIdFilter, Role role, UserStatus status,
                                           String search, int page, int size) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        UUID companyId;
        if (actor.getRole() == Role.SUPER_ADMIN) {
            companyId = companyIdFilter;
        } else {
            // company admins only ever see their own team
            companyId = actor.getCompanyId();
            if (companyId == null) {
                throw ApiException.forbidden("You are not associated with a company");
            }
            if (role == null) {
                // hide candidates from company admin user management
            }
        }
        return PageResponse.of(
                userRepository.search(companyId, role, status, emptyToNull(search),
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))),
                UserResponse::from);
    }

    /** Active company-admin/recruiter colleagues of the caller (interview panel picker). */
    @Transactional(readOnly = true)
    public List<UserResponse> activeTeamMembers() {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        if (actor.getCompanyId() == null) {
            throw ApiException.forbidden("You are not associated with a company");
        }
        return userRepository.findByCompanyIdAndRoleIn(actor.getCompanyId(),
                        List.of(Role.COMPANY_ADMIN, Role.RECRUITER)).stream()
                .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                .map(UserResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID userId) {
        User user = find(userId);
        requireManageAccess(user, true);
        return UserResponse.from(user);
    }

    @Transactional
    public UserResponse setStatus(UUID userId, UserStatus status) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        User user = find(userId);
        requireManageAccess(user, false);

        if (user.getId().equals(actor.getId())) {
            throw ApiException.badRequest("You cannot change your own account status");
        }
        if (user.getRole() == Role.SUPER_ADMIN) {
            throw ApiException.forbidden("Super admin accounts cannot be modified here");
        }
        if (status != UserStatus.ACTIVE && status != UserStatus.DISABLED) {
            throw ApiException.badRequest("Status must be ACTIVE or DISABLED");
        }
        // Never let a company lock itself out: keep at least one active admin.
        // Lock the active-admin rows FOR UPDATE so concurrent disables can't both pass this check.
        if (status == UserStatus.DISABLED
                && user.getRole() == Role.COMPANY_ADMIN
                && user.getCompany() != null
                && userRepository.lockByCompanyAndRoleAndStatus(
                        user.getCompany().getId(), Role.COMPANY_ADMIN, UserStatus.ACTIVE).size() <= 1) {
            throw ApiException.badRequest(
                    "This is the company's only active administrator and cannot be disabled");
        }

        user.setStatus(status);
        if (status == UserStatus.DISABLED) {
            refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
        }

        auditService.log(status == UserStatus.DISABLED ? "USER_DISABLED" : "USER_ENABLED",
                "USER", user.getId().toString(),
                Map.of("email", user.getEmail(), "role", user.getRole().name()));
        return UserResponse.from(user);
    }

    private User find(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    private void requireManageAccess(User target, boolean readOnly) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        if (actor.getRole() == Role.SUPER_ADMIN) {
            return;
        }
        if (actor.getRole() == Role.COMPANY_ADMIN
                && target.getCompany() != null
                && target.getCompany().getId().equals(actor.getCompanyId())) {
            return;
        }
        throw ApiException.forbidden("You cannot manage this user");
    }

    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
