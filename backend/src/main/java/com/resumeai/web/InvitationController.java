package com.resumeai.web;

import com.resumeai.dto.AuthDtos.AuthResponse;
import com.resumeai.dto.CommonDtos.PageResponse;
import com.resumeai.dto.InvitationDtos.*;
import com.resumeai.service.InvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/invitations")
@RequiredArgsConstructor
public class InvitationController {

    private final InvitationService invitationService;

    @PostMapping("/company")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public InvitationResponse inviteCompany(@Valid @RequestBody CompanyInviteRequest request) {
        return invitationService.inviteCompany(request);
    }

    @PostMapping("/team")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public InvitationResponse inviteTeamMember(@Valid @RequestBody TeamInviteRequest request) {
        return invitationService.inviteTeamMember(request);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','COMPANY_ADMIN')")
    public PageResponse<InvitationResponse> list(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return invitationService.list(page, size);
    }

    @PostMapping("/{id}/resend")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','COMPANY_ADMIN')")
    public InvitationResponse resend(@PathVariable UUID id) {
        return invitationService.resend(id);
    }

    @PostMapping("/{id}/revoke")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','COMPANY_ADMIN')")
    public InvitationResponse revoke(@PathVariable UUID id) {
        return invitationService.revoke(id);
    }

    // ------------------------------- public endpoints (no authentication)

    @GetMapping("/public/{token}")
    public PublicInvitationResponse getPublic(@PathVariable String token) {
        return invitationService.getPublicInvitation(token);
    }

    @PostMapping("/accept-company")
    public AuthResponse acceptCompany(@Valid @RequestBody AcceptCompanyRequest request) {
        return invitationService.acceptCompanyInvitation(request);
    }

    @PostMapping("/accept-team")
    public AuthResponse acceptTeam(@Valid @RequestBody AcceptTeamRequest request) {
        return invitationService.acceptTeamInvitation(request);
    }
}
