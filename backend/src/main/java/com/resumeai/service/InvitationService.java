package com.resumeai.service;

import com.resumeai.common.TokenUtil;
import com.resumeai.common.exception.ApiException;
import com.resumeai.config.AppProperties;
import com.resumeai.domain.Company;
import com.resumeai.domain.Invitation;
import com.resumeai.domain.User;
import com.resumeai.domain.enums.*;
import com.resumeai.dto.AuthDtos.AuthResponse;
import com.resumeai.dto.CommonDtos.PageResponse;
import com.resumeai.dto.InvitationDtos.*;
import com.resumeai.dto.UserDtos.UserResponse;
import com.resumeai.repository.CompanyRepository;
import com.resumeai.repository.InvitationRepository;
import com.resumeai.repository.UserRepository;
import com.resumeai.security.JwtService;
import com.resumeai.security.SecurityUtils;
import com.resumeai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InvitationService {

    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditService auditService;
    private final JwtService jwtService;
    private final AppProperties properties;

    // ----------------------------------------------------------- creation

    /** SUPER_ADMIN invites a new company: the recipient becomes its COMPANY_ADMIN. */
    @Transactional
    public InvitationResponse inviteCompany(CompanyInviteRequest request) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        ensureEmailAvailable(request.email());

        Invitation invitation = Invitation.builder()
                .email(request.email().toLowerCase())
                .type(InvitationType.COMPANY)
                .role(Role.COMPANY_ADMIN)
                .companyName(request.companyName())
                .invitedBy(userRepository.getReferenceById(actor.getId()))
                .expiresAt(expiry())
                .build();

        String raw = TokenUtil.generateToken();
        invitation.setTokenHash(TokenUtil.sha256(raw));
        invitationRepository.save(invitation);

        String url = emailService.frontendUrl("/accept-invitation?token=" + raw);
        String html = emailService.template("You're invited to join ResumeAI",
                "<p>Hello,</p><p><strong>" + request.companyName() + "</strong> has been invited to the "
                        + "ResumeAI recruitment platform. Click below to complete your company profile and "
                        + "create your administrator account. The link expires in "
                        + properties.getInvitations().getExpiryHours() + " hours.</p>",
                "Set up your company", url);
        emailService.send(invitation.getEmail(), "Invitation: set up " + request.companyName() + " on ResumeAI",
                html, url);

        auditService.log("COMPANY_INVITED", "INVITATION", invitation.getId().toString(),
                Map.of("email", invitation.getEmail(), "companyName", request.companyName()));

        return InvitationResponse.from(invitation);
    }

    /** COMPANY_ADMIN invites a team member (recruiter or another admin) into their company. */
    @Transactional
    public InvitationResponse inviteTeamMember(TeamInviteRequest request) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        if (actor.getCompanyId() == null) {
            throw ApiException.forbidden("You are not associated with a company");
        }
        if (request.role() != Role.RECRUITER && request.role() != Role.COMPANY_ADMIN) {
            throw ApiException.badRequest("Team members can only be invited as RECRUITER or COMPANY_ADMIN");
        }
        ensureEmailAvailable(request.email());

        Company company = companyRepository.findById(actor.getCompanyId())
                .orElseThrow(() -> ApiException.notFound("Company not found"));

        Invitation invitation = Invitation.builder()
                .email(request.email().toLowerCase())
                .type(InvitationType.TEAM_MEMBER)
                .role(request.role())
                .company(company)
                .invitedBy(userRepository.getReferenceById(actor.getId()))
                .expiresAt(expiry())
                .build();

        String raw = TokenUtil.generateToken();
        invitation.setTokenHash(TokenUtil.sha256(raw));
        invitationRepository.save(invitation);

        String roleLabel = request.role() == Role.RECRUITER ? "Recruiter" : "Company Administrator";
        String url = emailService.frontendUrl("/accept-invitation?token=" + raw);
        String html = emailService.template("You've been invited to " + company.getName(),
                "<p>Hello,</p><p>You have been invited to join <strong>" + company.getName()
                        + "</strong> on ResumeAI as a <strong>" + roleLabel + "</strong>. "
                        + "Click below to create your account. The link expires in "
                        + properties.getInvitations().getExpiryHours() + " hours.</p>",
                "Create your account", url);
        emailService.send(invitation.getEmail(), "Invitation to join " + company.getName() + " on ResumeAI",
                html, url);

        auditService.log("TEAM_MEMBER_INVITED", "INVITATION", invitation.getId().toString(),
                Map.of("email", invitation.getEmail(), "role", request.role().name()));

        return InvitationResponse.from(invitation);
    }

    private void ensureEmailAvailable(String email) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("A user with this email already exists");
        }
        if (invitationRepository.existsByEmailIgnoreCaseAndStatus(email, InvitationStatus.PENDING)) {
            throw ApiException.conflict("A pending invitation for this email already exists");
        }
    }

    private Instant expiry() {
        return Instant.now().plus(Duration.ofHours(properties.getInvitations().getExpiryHours()));
    }

    // ---------------------------------------------------------- acceptance

    @Transactional(readOnly = true)
    public PublicInvitationResponse getPublicInvitation(String rawToken) {
        Invitation invitation = findByRawToken(rawToken);
        return new PublicInvitationResponse(
                invitation.getEmail(),
                invitation.getType(),
                invitation.getRole(),
                invitation.getType() == InvitationType.COMPANY
                        ? invitation.getCompanyName()
                        : (invitation.getCompany() != null ? invitation.getCompany().getName() : null),
                invitation.getStatus(),
                invitation.getExpiresAt().isBefore(Instant.now()));
    }

    /** Public endpoint: invited company admin completes company profile + own account. */
    @Transactional
    public AuthResponse acceptCompanyInvitation(AcceptCompanyRequest request) {
        Invitation invitation = validateForAcceptance(request.token(), InvitationType.COMPANY);

        Company company = Company.builder()
                .name(request.company().name())
                .industry(request.company().industry())
                .website(request.company().website())
                .companySize(request.company().companySize())
                .location(request.company().location())
                .description(request.company().description())
                .status(CompanyStatus.ACTIVE)
                .build();
        companyRepository.save(company);

        User admin = User.builder()
                .email(invitation.getEmail())
                .passwordHash(passwordEncoder.encode(request.admin().password()))
                .fullName(request.admin().fullName())
                .phone(request.admin().phone())
                .role(Role.COMPANY_ADMIN)
                .status(UserStatus.ACTIVE)
                .company(company)
                .emailVerified(true)
                .build();
        userRepository.save(admin);

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());
        invitation.setCompany(company);

        auditService.log(null, company.getId(), "COMPANY_ONBOARDED", "COMPANY", company.getId().toString(),
                Map.of("companyName", company.getName(), "adminEmail", admin.getEmail()));

        UserPrincipal principal = new UserPrincipal(admin);
        return new AuthResponse(jwtService.generateAccessToken(principal), null, UserResponse.from(admin));
    }

    /** Public endpoint: invited team member creates their account. */
    @Transactional
    public AuthResponse acceptTeamInvitation(AcceptTeamRequest request) {
        Invitation invitation = validateForAcceptance(request.token(), InvitationType.TEAM_MEMBER);

        User user = User.builder()
                .email(invitation.getEmail())
                .passwordHash(passwordEncoder.encode(request.password()))
                .fullName(request.fullName())
                .phone(request.phone())
                .role(invitation.getRole())
                .status(UserStatus.ACTIVE)
                .company(invitation.getCompany())
                .emailVerified(true)
                .build();
        userRepository.save(user);

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(Instant.now());

        auditService.log(null, invitation.getCompany().getId(), "INVITATION_ACCEPTED", "USER",
                user.getId().toString(), Map.of("email", user.getEmail(), "role", user.getRole().name()));

        UserPrincipal principal = new UserPrincipal(user);
        return new AuthResponse(jwtService.generateAccessToken(principal), null, UserResponse.from(user));
    }

    private Invitation validateForAcceptance(String rawToken, InvitationType expectedType) {
        Invitation invitation = findByRawToken(rawToken);
        if (invitation.getType() != expectedType) {
            throw ApiException.badRequest("Invalid invitation type");
        }
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw ApiException.badRequest("This invitation is no longer valid");
        }
        if (invitation.getExpiresAt().isBefore(Instant.now())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            throw ApiException.badRequest("This invitation has expired. Please ask for a new one.");
        }
        if (userRepository.existsByEmailIgnoreCase(invitation.getEmail())) {
            throw ApiException.conflict("An account with this email already exists");
        }
        return invitation;
    }

    private Invitation findByRawToken(String rawToken) {
        return invitationRepository.findByTokenHash(TokenUtil.sha256(rawToken))
                .orElseThrow(() -> ApiException.notFound("Invitation not found"));
    }

    // ---------------------------------------------------------- management

    @Transactional(readOnly = true)
    public PageResponse<InvitationResponse> list(int page, int size) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (actor.getRole() == Role.SUPER_ADMIN) {
            return PageResponse.of(invitationRepository.findByType(InvitationType.COMPANY, pageable),
                    InvitationResponse::from);
        }
        if (actor.getCompanyId() == null) {
            throw ApiException.forbidden("You are not associated with a company");
        }
        return PageResponse.of(invitationRepository.findByCompanyId(actor.getCompanyId(), pageable),
                InvitationResponse::from);
    }

    @Transactional
    public InvitationResponse resend(UUID invitationId) {
        Invitation invitation = requireManageable(invitationId);
        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw ApiException.badRequest("Invitation was already accepted");
        }

        String raw = TokenUtil.generateToken();
        invitation.setTokenHash(TokenUtil.sha256(raw));
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(expiry());

        String url = emailService.frontendUrl("/accept-invitation?token=" + raw);
        String target = invitation.getType() == InvitationType.COMPANY
                ? invitation.getCompanyName()
                : invitation.getCompany().getName();
        String html = emailService.template("Your ResumeAI invitation (resent)",
                "<p>Hello,</p><p>Here is a fresh invitation link to join <strong>" + target
                        + "</strong> on ResumeAI. The link expires in "
                        + properties.getInvitations().getExpiryHours() + " hours.</p>",
                "Accept invitation", url);
        emailService.send(invitation.getEmail(), "Invitation to join " + target + " on ResumeAI (resent)",
                html, url);

        auditService.log("INVITATION_RESENT", "INVITATION", invitation.getId().toString(),
                Map.of("email", invitation.getEmail()));
        return InvitationResponse.from(invitation);
    }

    @Transactional
    public InvitationResponse revoke(UUID invitationId) {
        Invitation invitation = requireManageable(invitationId);
        if (invitation.getStatus() == InvitationStatus.ACCEPTED) {
            throw ApiException.badRequest("Invitation was already accepted");
        }
        invitation.setStatus(InvitationStatus.REVOKED);
        auditService.log("INVITATION_REVOKED", "INVITATION", invitation.getId().toString(),
                Map.of("email", invitation.getEmail()));
        return InvitationResponse.from(invitation);
    }

    private Invitation requireManageable(UUID invitationId) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        Invitation invitation = invitationRepository.findById(invitationId)
                .orElseThrow(() -> ApiException.notFound("Invitation not found"));

        if (actor.getRole() == Role.SUPER_ADMIN) {
            if (invitation.getType() != InvitationType.COMPANY) {
                throw ApiException.forbidden("Super admin manages company invitations only");
            }
            return invitation;
        }
        if (actor.getRole() == Role.COMPANY_ADMIN
                && invitation.getCompany() != null
                && invitation.getCompany().getId().equals(actor.getCompanyId())) {
            return invitation;
        }
        throw ApiException.forbidden("You cannot manage this invitation");
    }
}
