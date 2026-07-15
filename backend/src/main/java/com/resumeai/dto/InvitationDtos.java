package com.resumeai.dto;

import com.resumeai.domain.Invitation;
import com.resumeai.domain.enums.InvitationStatus;
import com.resumeai.domain.enums.InvitationType;
import com.resumeai.domain.enums.Role;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public final class InvitationDtos {

    private InvitationDtos() {
    }

    public record CompanyInviteRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 200) String companyName) {
    }

    public record TeamInviteRequest(
            @NotBlank @Email String email,
            @NotNull Role role) {
    }

    public record AcceptCompanyRequest(
            @NotBlank String token,
            @NotNull @Valid CompanyDtos.CompanyRequest company,
            @NotNull @Valid AdminAccount admin) {

        public record AdminAccount(
                @NotBlank @Size(max = 150) String fullName,
                @NotBlank @Size(min = 8, max = 100) String password,
                @Size(max = 40) String phone) {
        }
    }

    public record AcceptTeamRequest(
            @NotBlank String token,
            @NotBlank @Size(max = 150) String fullName,
            @NotBlank @Size(min = 8, max = 100) String password,
            @Size(max = 40) String phone) {
    }

    public record InvitationResponse(
            UUID id,
            String email,
            InvitationType type,
            Role role,
            String companyName,
            UUID companyId,
            InvitationStatus status,
            String invitedByName,
            Instant expiresAt,
            Instant acceptedAt,
            Instant createdAt) {

        public static InvitationResponse from(Invitation i) {
            return new InvitationResponse(
                    i.getId(),
                    i.getEmail(),
                    i.getType(),
                    i.getRole(),
                    i.getType() == InvitationType.COMPANY
                            ? i.getCompanyName()
                            : (i.getCompany() != null ? i.getCompany().getName() : null),
                    i.getCompany() != null ? i.getCompany().getId() : null,
                    i.getStatus(),
                    i.getInvitedBy() != null ? i.getInvitedBy().getFullName() : null,
                    i.getExpiresAt(),
                    i.getAcceptedAt(),
                    i.getCreatedAt());
        }
    }

    /** Safe subset shown on the public accept-invitation page. */
    public record PublicInvitationResponse(
            String email,
            InvitationType type,
            Role role,
            String companyName,
            InvitationStatus status,
            boolean expired) {
    }
}
