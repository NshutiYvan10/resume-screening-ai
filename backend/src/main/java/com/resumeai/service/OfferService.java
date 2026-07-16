package com.resumeai.service;

import com.resumeai.common.exception.ApiException;
import com.resumeai.domain.Application;
import com.resumeai.domain.Offer;
import com.resumeai.domain.User;
import com.resumeai.domain.enums.*;
import com.resumeai.dto.PipelineDtos.OfferOutcomeRequest;
import com.resumeai.dto.PipelineDtos.OfferRequest;
import com.resumeai.repository.ApplicationRepository;
import com.resumeai.repository.InterviewFeedbackRepository;
import com.resumeai.repository.InterviewRepository;
import com.resumeai.repository.OfferRepository;
import com.resumeai.repository.UserRepository;
import com.resumeai.security.SecurityUtils;
import com.resumeai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Offer workflow: recruiter drafts, company admin approves, then the offer is
 * extended to the candidate — only after at least one completed interview
 * with submitted feedback. Any revision voids prior approval (that versioning
 * is the negotiation trail). HIRED requires ACCEPTED.
 */
@Service
@RequiredArgsConstructor
public class OfferService {

    private final OfferRepository offerRepository;
    private final ApplicationRepository applicationRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewFeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ApplicationEventService eventService;
    private final ApplicationService applicationService;

    @Transactional
    public UUID create(UUID applicationId, OfferRequest request) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        Application application = requireCompanyApplication(applicationId);
        if (application.getStatus() != ApplicationStatus.INTERVIEW) {
            throw ApiException.badRequest("Offers are created from the Interview stage");
        }
        if (offerRepository.findByApplicationId(applicationId).isPresent()) {
            throw ApiException.conflict("An offer already exists — revise it instead");
        }

        boolean isAdmin = actor.getRole() == Role.COMPANY_ADMIN;
        Offer offer = Offer.builder()
                .application(application)
                .salary(request.salary())
                .currency(request.currency())
                .startDate(request.startDate())
                .expiresAt(request.expiresAt())
                .notes(request.notes())
                .createdBy(userRepository.getReferenceById(actor.getId()))
                // the approval authority creating an offer approves it implicitly
                .status(isAdmin ? OfferStatus.APPROVED : OfferStatus.PENDING_APPROVAL)
                .build();
        if (isAdmin) {
            offer.setApprovedBy(userRepository.getReferenceById(actor.getId()));
            offer.setApprovedAt(Instant.now());
        }
        offerRepository.save(offer);

        eventService.record(application, ApplicationEventType.OFFER_CREATED, Map.of(
                "salary", request.salary() + " " + request.currency(),
                "status", offer.getStatus().name()));
        auditService.log("OFFER_CREATED", "APPLICATION", applicationId.toString(),
                Map.of("candidate", application.getCandidate().getEmail(),
                        "salary", request.salary().toPlainString() + " " + request.currency()));

        if (!isAdmin) {
            notifyAdmins(application, "Offer awaiting approval: "
                            + application.getCandidate().getFullName(),
                    actor.getFullName() + " drafted an offer for "
                            + application.getCandidate().getFullName() + " ("
                            + application.getJob().getTitle() + ") at " + request.salary() + " "
                            + request.currency() + ". Review and approve it.");
        }
        return offer.getId();
    }

    /** Company admin approval — the gate before an offer can be extended. */
    @Transactional
    public void approve(UUID offerId) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        Offer offer = requireCompanyOffer(offerId);
        requireAdmin(actor);
        if (offer.getStatus() != OfferStatus.PENDING_APPROVAL) {
            throw ApiException.badRequest("Only offers pending approval can be approved");
        }
        offer.setStatus(OfferStatus.APPROVED);
        offer.setApprovedBy(userRepository.getReferenceById(actor.getId()));
        offer.setApprovedAt(Instant.now());

        Application application = offer.getApplication();
        eventService.record(application, ApplicationEventType.OFFER_APPROVED, Map.of(
                "salary", offer.getSalary() + " " + offer.getCurrency()));
        auditService.log("OFFER_APPROVED", "APPLICATION", application.getId().toString(),
                Map.of("candidate", application.getCandidate().getEmail()));

        if (offer.getCreatedBy() != null && !offer.getCreatedBy().getId().equals(actor.getId())) {
            notificationService.notify(offer.getCreatedBy(), NotificationType.OFFER_DECISION,
                    "Offer approved: " + application.getCandidate().getFullName(),
                    "Your offer for " + application.getCandidate().getFullName()
                            + " was approved. You can now extend it to the candidate.",
                    "/company/applications/" + application.getId(), false);
        }
    }

    /** Any material change voids prior approval and restarts the chain. */
    @Transactional
    public void revise(UUID offerId, OfferRequest request) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        Offer offer = requireCompanyOffer(offerId);
        if (offer.getStatus() == OfferStatus.ACCEPTED) {
            throw ApiException.badRequest("An accepted offer can no longer be revised");
        }

        String before = offer.getSalary() + " " + offer.getCurrency();
        offer.setSalary(request.salary());
        offer.setCurrency(request.currency());
        offer.setStartDate(request.startDate());
        offer.setExpiresAt(request.expiresAt());
        offer.setNotes(request.notes());
        offer.setExtendedAt(null);
        offer.setRespondedAt(null);

        boolean isAdmin = actor.getRole() == Role.COMPANY_ADMIN;
        if (isAdmin) {
            offer.setStatus(OfferStatus.APPROVED);
            offer.setApprovedBy(userRepository.getReferenceById(actor.getId()));
            offer.setApprovedAt(Instant.now());
        } else {
            offer.setStatus(OfferStatus.PENDING_APPROVAL);
            offer.setApprovedBy(null);
            offer.setApprovedAt(null);
        }

        Application application = offer.getApplication();
        eventService.record(application, ApplicationEventType.OFFER_REVISED, Map.of(
                "from", before, "to", request.salary() + " " + request.currency(),
                "status", offer.getStatus().name()));

        if (!isAdmin) {
            notifyAdmins(application, "Revised offer awaiting approval: "
                            + application.getCandidate().getFullName(),
                    actor.getFullName() + " revised the offer for "
                            + application.getCandidate().getFullName() + " (now " + request.salary()
                            + " " + request.currency() + "). Prior approval was voided — please re-approve.");
        }
    }

    /**
     * Extend the approved offer to the candidate. Gate: at least one completed
     * interview with at least one submitted scorecard — no offers without a
     * real evaluation on record.
     */
    @Transactional
    public void extend(UUID offerId) {
        Offer offer = requireCompanyOffer(offerId);
        Application application = offer.getApplication();

        if (offer.getStatus() != OfferStatus.APPROVED) {
            throw ApiException.badRequest("The offer must be approved before it can be extended");
        }
        long completedInterviews = interviewRepository.countByApplicationIdAndStatus(
                application.getId(), InterviewStatus.COMPLETED);
        long feedbackCount = feedbackRepository.countForApplication(application.getId());
        if (completedInterviews == 0 || feedbackCount == 0) {
            throw ApiException.badRequest(
                    "Extend requires at least one completed interview with submitted feedback");
        }

        offer.setStatus(OfferStatus.EXTENDED);
        offer.setExtendedAt(Instant.now());
        // this is the transition that puts the candidate in the OFFERED stage
        applicationService.transitionTo(application, ApplicationStatus.OFFERED);
        eventService.record(application, ApplicationEventType.OFFER_EXTENDED, Map.of(
                "salary", offer.getSalary() + " " + offer.getCurrency(),
                "expires", offer.getExpiresAt() != null ? offer.getExpiresAt().toString() : ""));

        String jobTitle = application.getJob().getTitle();
        String company = application.getJob().getCompany().getName();
        String terms = "Salary: " + offer.getSalary() + " " + offer.getCurrency()
                + (offer.getStartDate() != null ? ". Proposed start date: " + offer.getStartDate() : "")
                + (offer.getExpiresAt() != null ? ". Please respond by " + offer.getExpiresAt() : "")
                + (offer.getNotes() != null && !offer.getNotes().isBlank()
                        ? ". " + offer.getNotes().trim() : "");
        notificationService.notify(application.getCandidate(), NotificationType.APPLICATION_STATUS_CHANGED,
                "Job offer: " + jobTitle,
                "Congratulations! " + company + " has extended an offer for the position of "
                        + jobTitle + ". " + terms
                        + " The hiring team will contact you to finalize details.",
                "/candidate/applications", true);
    }

    /** Record the candidate's decision (captured by the hiring team). */
    @Transactional
    public void recordOutcome(UUID offerId, OfferOutcomeRequest request) {
        Offer offer = requireCompanyOffer(offerId);
        if (offer.getStatus() != OfferStatus.EXTENDED) {
            throw ApiException.badRequest("Only extended offers can be accepted or declined");
        }
        if (request.status() != OfferStatus.ACCEPTED && request.status() != OfferStatus.DECLINED) {
            throw ApiException.badRequest("Outcome must be ACCEPTED or DECLINED");
        }

        offer.setStatus(request.status());
        offer.setRespondedAt(Instant.now());

        Application application = offer.getApplication();
        boolean accepted = request.status() == OfferStatus.ACCEPTED;
        eventService.record(application,
                accepted ? ApplicationEventType.OFFER_ACCEPTED : ApplicationEventType.OFFER_DECLINED,
                Map.of("note", request.note() != null ? request.note() : ""));
        auditService.log(accepted ? "OFFER_ACCEPTED" : "OFFER_DECLINED", "APPLICATION",
                application.getId().toString(),
                Map.of("candidate", application.getCandidate().getEmail()));

        notifyAdmins(application,
                (accepted ? "Offer accepted: " : "Offer declined: ")
                        + application.getCandidate().getFullName(),
                application.getCandidate().getFullName() + " has "
                        + (accepted ? "accepted" : "declined") + " the offer for "
                        + application.getJob().getTitle() + "."
                        + (accepted ? " You can now mark the candidate hired." : ""));
    }

    // ------------------------------------------------------------ helpers

    private void notifyAdmins(Application application, String title, String message) {
        userRepository.findByCompanyIdAndRoleIn(application.getJob().getCompany().getId(),
                        List.of(Role.COMPANY_ADMIN))
                .forEach(admin -> notificationService.notify(admin,
                        NotificationType.OFFER_APPROVAL_NEEDED, title, message,
                        "/company/applications/" + application.getId(), true));
    }

    private void requireAdmin(UserPrincipal actor) {
        if (actor.getRole() != Role.COMPANY_ADMIN) {
            throw ApiException.forbidden("Only company administrators can approve offers");
        }
    }

    private Application requireCompanyApplication(UUID applicationId) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> ApiException.notFound("Application not found"));
        if (actor.getRole() != Role.COMPANY_ADMIN && actor.getRole() != Role.RECRUITER
                || !application.getJob().getCompany().getId().equals(actor.getCompanyId())) {
            throw ApiException.forbidden("This application belongs to another company");
        }
        return application;
    }

    private Offer requireCompanyOffer(UUID offerId) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> ApiException.notFound("Offer not found"));
        requireCompanyApplication(offer.getApplication().getId());
        return offer;
    }
}
