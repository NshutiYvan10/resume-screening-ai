package com.resumeai.service;

import com.resumeai.common.exception.ApiException;
import com.resumeai.domain.Application;
import com.resumeai.domain.Interview;
import com.resumeai.domain.Job;
import com.resumeai.domain.Offer;
import com.resumeai.domain.ScreeningResult;
import com.resumeai.domain.User;
import com.resumeai.domain.enums.*;
import com.resumeai.dto.ApplicationDtos.ApplicationResponse;
import com.resumeai.dto.CommonDtos.PageResponse;
import com.resumeai.dto.PipelineDtos.*;
import com.resumeai.repository.ApplicationRepository;
import com.resumeai.repository.InterviewFeedbackRepository;
import com.resumeai.repository.InterviewRepository;
import com.resumeai.repository.JobRepository;
import com.resumeai.repository.OfferRepository;
import com.resumeai.repository.UserRepository;
import com.resumeai.security.SecurityUtils;
import com.resumeai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ApplicationService {

    /** Terminal or candidate-controlled states a recruiter cannot move an application out of. */
    private static final Set<ApplicationStatus> LOCKED_STATES =
            EnumSet.of(ApplicationStatus.WITHDRAWN, ApplicationStatus.HIRED);

    /**
     * The enforced pipeline: candidates advance ONE stage at a time along this
     * path. OFFERED is entered only by extending an approved offer, and HIRED
     * only once that offer is accepted — never by a direct button.
     */
    private static final Map<ApplicationStatus, ApplicationStatus> NEXT_STAGE = Map.of(
            ApplicationStatus.SUBMITTED, ApplicationStatus.UNDER_REVIEW,
            ApplicationStatus.UNDER_REVIEW, ApplicationStatus.SHORTLISTED,
            ApplicationStatus.SHORTLISTED, ApplicationStatus.INTERVIEW);

    private static final Map<ApplicationStatus, ApplicationStatus> PREVIOUS_STAGE = Map.of(
            ApplicationStatus.UNDER_REVIEW, ApplicationStatus.SUBMITTED,
            ApplicationStatus.SHORTLISTED, ApplicationStatus.UNDER_REVIEW,
            ApplicationStatus.INTERVIEW, ApplicationStatus.SHORTLISTED,
            ApplicationStatus.OFFERED, ApplicationStatus.INTERVIEW);

    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final InterviewRepository interviewRepository;
    private final InterviewFeedbackRepository interviewFeedbackRepository;
    private final OfferRepository offerRepository;
    private final FileStorageService fileStorageService;
    private final ScreeningService screeningService;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final ApplicationEventService eventService;

    // ---------------------------------------------------------- candidate

    @Transactional
    public ApplicationResponse apply(UUID jobId, MultipartFile resume, String coverLetter) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        if (actor.getRole() != Role.CANDIDATE) {
            throw ApiException.forbidden("Only candidates can apply for jobs");
        }
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> ApiException.notFound("Job not found"));
        if (job.getStatus() != JobStatus.PUBLISHED
                || job.getCompany().getStatus() != CompanyStatus.ACTIVE) {
            throw ApiException.badRequest("This job is not open for applications");
        }
        if (job.getDeadline() != null && job.getDeadline().isBefore(LocalDate.now())) {
            throw ApiException.badRequest("The application deadline for this job has passed");
        }
        if (resume == null || resume.isEmpty()) {
            throw ApiException.badRequest("A resume file is required");
        }

        // A candidate gets one application per job. If they previously withdrew, they may
        // re-apply (the existing row is reactivated and re-screened). Any other prior status
        // (submitted, under review, shortlisted, rejected, hired, ...) blocks re-applying.
        Application existing = applicationRepository.findByJobIdAndCandidateId(jobId, actor.getId())
                .orElse(null);
        if (existing != null && existing.getStatus() != ApplicationStatus.WITHDRAWN) {
            throw ApiException.conflict("You have already applied for this job");
        }

        User candidate = userRepository.getReferenceById(actor.getId());
        String storedPath = fileStorageService.storeResume(resume, job.getCompany().getId());

        Application application;
        boolean reapplied = existing != null;
        // Old resume is removed only after the transaction commits (see below), so a
        // rollback can't leave the row pointing at a deleted file.
        String previousResumePath = reapplied ? existing.getResumeStoredPath() : null;
        if (reapplied) {
            // reactivate the withdrawn application in place (preserves one-per-job uniqueness)
            existing.setCoverLetter(coverLetter);
            existing.setResumeFileName(resume.getOriginalFilename());
            existing.setResumeStoredPath(storedPath);
            existing.setResumeContentType(resume.getContentType());
            existing.setStatus(ApplicationStatus.SUBMITTED);
            existing.setRecruiterNote(null);
            existing.setStatusUpdatedBy(null);
            existing.setAppliedAt(Instant.now());
            existing.setStatusUpdatedAt(Instant.now());
            resetScreening(existing);
            application = existing;
        } else {
            application = Application.builder()
                    .job(job)
                    .candidate(candidate)
                    .coverLetter(coverLetter)
                    .resumeFileName(resume.getOriginalFilename())
                    .resumeStoredPath(storedPath)
                    .resumeContentType(resume.getContentType())
                    .build();
            ScreeningResult screening = ScreeningResult.builder()
                    .application(application)
                    .status(ScreeningStatus.PENDING)
                    .build();
            application.setScreeningResult(screening);
            applicationRepository.save(application);
        }

        auditService.log(reapplied ? "APPLICATION_RESUBMITTED" : "APPLICATION_SUBMITTED",
                "APPLICATION", application.getId().toString(),
                Map.of("jobTitle", job.getTitle(), "jobId", jobId.toString()));
        eventService.record(application, ApplicationEventType.APPLIED,
                Map.of("resubmitted", reapplied));

        // confirmation to the candidate (in-app + email)
        notificationService.notify(candidate, NotificationType.APPLICATION_RECEIVED,
                "Application received: " + job.getTitle(),
                "Your application for " + job.getTitle() + " at " + job.getCompany().getName()
                        + " has been received and is being processed by our AI screening engine. "
                        + "We will notify you when its status changes.",
                "/candidate/applications", true);

        // heads-up to the recruiter who owns the job (in-app only)
        if (job.getCreatedBy() != null) {
            notificationService.notify(job.getCreatedBy(), NotificationType.NEW_APPLICATION,
                    "New application for " + job.getTitle(),
                    application.getCandidate().getFullName() + " applied for " + job.getTitle() + ".",
                    "/company/jobs/" + job.getId() + "/applications", false);
        }

        // kick off async AI screening, but only once this transaction has committed
        // (otherwise the async worker races the insert and can't find the application)
        UUID applicationId = application.getId();
        String orphanedResumePath = previousResumePath;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // remove the superseded resume only after the new path is durably persisted
                if (orphanedResumePath != null) {
                    fileStorageService.deleteQuietly(orphanedResumePath);
                }
                screeningService.queueScreening(applicationId);
            }
        });

        return ApplicationResponse.from(application, false, false);
    }

    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> myApplications(int page, int size) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        return PageResponse.of(
                applicationRepository.findByCandidateId(actor.getId(),
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "appliedAt"))),
                a -> ApplicationResponse.from(a, false, false));
    }

    @Transactional
    public ApplicationResponse withdraw(UUID applicationId) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        Application application = find(applicationId);
        if (!application.getCandidate().getId().equals(actor.getId())) {
            throw ApiException.forbidden("Not your application");
        }
        if (application.getStatus() == ApplicationStatus.WITHDRAWN) {
            throw ApiException.badRequest("Application is already withdrawn");
        }
        if (application.getStatus() == ApplicationStatus.HIRED) {
            throw ApiException.badRequest("You cannot withdraw a completed application");
        }
        application.setStatus(ApplicationStatus.WITHDRAWN);
        application.setStatusUpdatedAt(Instant.now());

        auditService.log("APPLICATION_WITHDRAWN", "APPLICATION", applicationId.toString(),
                Map.of("jobTitle", application.getJob().getTitle()));
        eventService.record(application, ApplicationEventType.WITHDRAWN, Map.of());
        return ApplicationResponse.from(application, false, false);
    }

    // -------------------------------------------------- admin oversight

    /** Company-wide candidate pipeline across every job — company admin only. */
    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> companyPipeline(ApplicationStatus status, UUID jobId,
                                                             BigDecimal minScore, String sortBy,
                                                             int page, int size) {
        UserPrincipal actor = requireCompanyAdmin();
        Sort sort = "appliedAt".equals(sortBy)
                ? Sort.by(Sort.Direction.DESC, "appliedAt")
                : JpaSort.unsafe(Sort.Direction.DESC, "COALESCE(sr.matchScore, -1)");
        return PageResponse.of(
                applicationRepository.searchCompanyApplications(actor.getCompanyId(), status, jobId,
                        minScore, PageRequest.of(page, size, sort)),
                a -> ApplicationResponse.from(a, true, true));
    }

    /** CSV export of the whole company pipeline — company admin only. */
    @Transactional(readOnly = true)
    public String exportCompanyPipelineCsv() {
        UserPrincipal actor = requireCompanyAdmin();
        List<Application> apps = applicationRepository.findAllForCompany(actor.getCompanyId());
        StringBuilder sb = new StringBuilder("Candidate,Email,Job,Status,Match Score,Applied At,Screening\n");
        for (Application a : apps) {
            ScreeningResult sr = a.getScreeningResult();
            sb.append(csv(a.getCandidate().getFullName())).append(',')
                    .append(csv(a.getCandidate().getEmail())).append(',')
                    .append(csv(a.getJob().getTitle())).append(',')
                    .append(a.getStatus()).append(',')
                    .append(sr != null && sr.getMatchScore() != null ? sr.getMatchScore() : "").append(',')
                    .append(a.getAppliedAt()).append(',')
                    .append(sr != null ? sr.getStatus() : "").append('\n');
        }
        auditService.log("PIPELINE_EXPORTED", "COMPANY", actor.getCompanyId().toString(),
                Map.of("rows", apps.size()));
        return sb.toString();
    }

    private String csv(String v) {
        if (v == null) {
            return "";
        }
        if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }

    private UserPrincipal requireCompanyAdmin() {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        if (actor.getRole() != Role.COMPANY_ADMIN || actor.getCompanyId() == null) {
            throw ApiException.forbidden("Only company administrators can access company-wide oversight");
        }
        return actor;
    }

    // ----------------------------------------------------------- recruiter

    @Transactional(readOnly = true)
    public PageResponse<ApplicationResponse> listForJob(UUID jobId, ApplicationStatus status,
                                                        BigDecimal minScore, String sortBy,
                                                        int page, int size) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> ApiException.notFound("Job not found"));
        requireRecruiterAccess(job.getCompany().getId());

        Sort sort = "appliedAt".equals(sortBy)
                ? Sort.by(Sort.Direction.DESC, "appliedAt")
                // rank by AI match score, unscreened applications last
                : JpaSort.unsafe(Sort.Direction.DESC, "COALESCE(sr.matchScore, -1)");

        return PageResponse.of(
                applicationRepository.searchJobApplications(jobId, status, minScore,
                        PageRequest.of(page, size, sort)),
                a -> ApplicationResponse.from(a, true, true));
    }

    @Transactional(readOnly = true)
    public ApplicationResponse get(UUID applicationId) {
        Application application = find(applicationId);
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        if (actor.getRole() == Role.CANDIDATE) {
            if (!application.getCandidate().getId().equals(actor.getId())) {
                throw ApiException.forbidden("Not your application");
            }
            return ApplicationResponse.from(application, false, false);
        }
        requireRecruiterAccess(application.getJob().getCompany().getId());
        return ApplicationResponse.from(application, true, true);
    }

    // ------------------------------------------------- pipeline state machine

    /** Advance one stage along the enforced path (through INTERVIEW). */
    @Transactional
    public ApplicationResponse advanceStage(UUID applicationId) {
        Application application = find(applicationId);
        requireRecruiterAccess(application.getJob().getCompany().getId());
        requireActiveStage(application);

        ApplicationStatus next = NEXT_STAGE.get(application.getStatus());
        if (next == null) {
            if (application.getStatus() == ApplicationStatus.INTERVIEW) {
                throw ApiException.badRequest(
                        "Moving to Offered requires creating an offer, getting it approved and extending it");
            }
            if (application.getStatus() == ApplicationStatus.OFFERED) {
                throw ApiException.badRequest(
                        "Moving to Hired requires the candidate to accept the offer — use Mark hired");
            }
            throw ApiException.badRequest("Cannot advance from " + application.getStatus());
        }
        transitionTo(application, next);
        return ApplicationResponse.from(application, true, true);
    }

    /** Admin-only correction: move back one stage. */
    @Transactional
    public ApplicationResponse backtrackStage(UUID applicationId) {
        Application application = find(applicationId);
        requireCompanyAdminAccess(application);
        requireActiveStage(application);

        ApplicationStatus previous = PREVIOUS_STAGE.get(application.getStatus());
        if (previous == null) {
            throw ApiException.badRequest("Cannot move back from " + application.getStatus());
        }
        if (application.getStatus() == ApplicationStatus.OFFERED) {
            Offer offer = offerRepository.findByApplicationId(applicationId).orElse(null);
            if (offer != null && (offer.getStatus() == OfferStatus.EXTENDED
                    || offer.getStatus() == OfferStatus.ACCEPTED)) {
                throw ApiException.badRequest(
                        "Resolve the outstanding offer (declined) before moving the candidate back");
            }
        }
        transitionTo(application, previous);
        return ApplicationResponse.from(application, true, true);
    }

    /** Reject from any active stage. A standardized reason is mandatory. */
    @Transactional
    public ApplicationResponse reject(UUID applicationId, RejectRequest request) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        Application application = find(applicationId);
        requireRecruiterAccess(application.getJob().getCompany().getId());
        requireActiveStage(application);

        ApplicationStatus from = application.getStatus();
        application.setStatus(ApplicationStatus.REJECTED);
        application.setRejectionReason(request.reason());
        application.setRejectionNote(request.internalNote());
        application.setStatusUpdatedBy(userRepository.getReferenceById(actor.getId()));
        application.setStatusUpdatedAt(Instant.now());

        eventService.record(application, ApplicationEventType.REJECTED, Map.of(
                "from", from.name(), "reason", request.reason().name(),
                "internalNote", request.internalNote() != null ? request.internalNote() : ""));
        auditService.log("APPLICATION_REJECTED", "APPLICATION", applicationId.toString(),
                Map.of("from", from.name(), "reason", request.reason().name(),
                        "candidate", application.getCandidate().getEmail(),
                        "jobTitle", application.getJob().getTitle()));

        // candidate-facing message stays minimal and job-related; the internal
        // reason code is never sent to the candidate
        String jobTitle = application.getJob().getTitle();
        String company = application.getJob().getCompany().getName();
        String message = "Thank you for your interest in " + jobTitle + " at " + company
                + ". After careful consideration, they have decided to move forward with other candidates.";
        if (request.candidateMessage() != null && !request.candidateMessage().isBlank()) {
            message += " Message from the hiring team: " + request.candidateMessage().trim();
        }
        notificationService.notify(application.getCandidate(),
                NotificationType.APPLICATION_STATUS_CHANGED,
                "Update on your application for " + jobTitle, message,
                "/candidate/applications", true);

        return ApplicationResponse.from(application, true, true);
    }

    /** Admin-only: reopen a rejected application to the stage it was rejected from. */
    @Transactional
    public ApplicationResponse reopen(UUID applicationId) {
        Application application = find(applicationId);
        requireCompanyAdminAccess(application);
        if (application.getStatus() != ApplicationStatus.REJECTED) {
            throw ApiException.badRequest("Only rejected applications can be reopened");
        }

        ApplicationStatus target = eventService.timeline(applicationId).stream()
                .filter(e -> e.getType() == ApplicationEventType.REJECTED)
                .findFirst()
                .map(e -> {
                    Object from = e.getDetails() != null ? e.getDetails().get("from") : null;
                    try {
                        return from != null ? ApplicationStatus.valueOf(from.toString())
                                : ApplicationStatus.UNDER_REVIEW;
                    } catch (IllegalArgumentException ex) {
                        return ApplicationStatus.UNDER_REVIEW;
                    }
                })
                .orElse(ApplicationStatus.UNDER_REVIEW);

        application.setRejectionReason(null);
        application.setRejectionNote(null);
        transitionTo(application, target);
        eventService.record(application, ApplicationEventType.REOPENED, Map.of("to", target.name()));
        return ApplicationResponse.from(application, true, true);
    }

    /** Final step: requires an ACCEPTED offer. Fires the onboarding handoff. */
    @Transactional
    public ApplicationResponse markHired(UUID applicationId) {
        Application application = find(applicationId);
        requireRecruiterAccess(application.getJob().getCompany().getId());
        if (application.getStatus() != ApplicationStatus.OFFERED) {
            throw ApiException.badRequest("Only candidates with an extended offer can be hired");
        }
        Offer offer = offerRepository.findByApplicationId(applicationId)
                .orElseThrow(() -> ApiException.badRequest("No offer exists for this application"));
        if (offer.getStatus() != OfferStatus.ACCEPTED) {
            throw ApiException.badRequest("The offer must be accepted before marking the candidate hired");
        }

        application.setHiredAt(Instant.now());
        transitionTo(application, ApplicationStatus.HIRED);
        eventService.record(application, ApplicationEventType.HIRED, Map.of(
                "startDate", offer.getStartDate() != null ? offer.getStartDate().toString() : "",
                "salary", offer.getSalary() + " " + offer.getCurrency()));

        // onboarding handoff: alert every company admin with the start date
        String startInfo = offer.getStartDate() != null
                ? " Start date: " + offer.getStartDate() + "." : "";
        userRepository.findByCompanyIdAndRoleIn(application.getJob().getCompany().getId(),
                        List.of(Role.COMPANY_ADMIN))
                .forEach(admin -> notificationService.notify(admin, NotificationType.PIPELINE,
                        "New hire: " + application.getCandidate().getFullName(),
                        application.getCandidate().getFullName() + " accepted the offer for "
                                + application.getJob().getTitle() + " and is marked hired."
                                + startInfo + " Begin onboarding preparation.",
                        "/company/applications/" + applicationId, false));

        return ApplicationResponse.from(application, true, true);
    }

    /** Append a note to the candidate's timeline (never overwritten). */
    @Transactional
    public void addNote(UUID applicationId, NoteRequest request) {
        Application application = find(applicationId);
        requireRecruiterAccess(application.getJob().getCompany().getId());
        eventService.record(application, ApplicationEventType.NOTE_ADDED,
                Map.of("text", request.text().trim()));
    }

    /** Composite pipeline workspace payload: interviews, offer, timeline, legal actions. */
    @Transactional(readOnly = true)
    public PipelineResponse pipeline(UUID applicationId) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        Application application = find(applicationId);
        requireRecruiterAccess(application.getJob().getCompany().getId());

        List<InterviewResponse> interviews = interviewRepository
                .findByApplicationIdOrderByScheduledAtAsc(applicationId).stream()
                .map(i -> toInterviewResponse(i, actor))
                .toList();

        OfferResponse offer = offerRepository.findByApplicationId(applicationId)
                .map(OfferResponse::from).orElse(null);

        List<EventResponse> timeline = eventService.timeline(applicationId).stream()
                .map(EventResponse::from).toList();

        return new PipelineResponse(interviews, offer, timeline,
                allowedActions(application, actor));
    }

    /**
     * Interview payload with the structured-hiring visibility rule: a panelist
     * who has not submitted their own scorecard cannot read colleagues'
     * feedback (prevents anchoring bias). Non-panel recruiters/admins see all.
     */
    private InterviewResponse toInterviewResponse(Interview interview, UserPrincipal viewer) {
        boolean viewerOnPanel = interview.getPanel().stream()
                .anyMatch(u -> u.getId().equals(viewer.getId()));
        boolean viewerSubmitted = interview.getFeedback().stream()
                .anyMatch(f -> f.getInterviewer().getId().equals(viewer.getId()));
        boolean hideOthers = viewerOnPanel && !viewerSubmitted;

        List<FeedbackResponse> feedback = interview.getFeedback().stream()
                .map(f -> hideOthers && !f.getInterviewer().getId().equals(viewer.getId())
                        ? FeedbackResponse.hidden(f)
                        : FeedbackResponse.from(f))
                .toList();

        List<PanelistResponse> panel = interview.getPanel().stream()
                .map(u -> new PanelistResponse(u.getId(), u.getFullName(),
                        interview.getFeedback().stream()
                                .anyMatch(f -> f.getInterviewer().getId().equals(u.getId()))))
                .toList();

        return new InterviewResponse(interview.getId(), interview.getScheduledAt(),
                interview.getDurationMinutes(), interview.getType(), interview.getLocation(),
                interview.getNotes(), interview.getStatus(),
                interview.getCreatedBy() != null ? interview.getCreatedBy().getFullName() : null,
                panel, feedback, viewerOnPanel, viewerSubmitted);
    }

    /** Which pipeline actions the current viewer may take right now (drives the UI). */
    private List<String> allowedActions(Application application, UserPrincipal actor) {
        List<String> actions = new ArrayList<>();
        ApplicationStatus status = application.getStatus();
        boolean isAdmin = actor.getRole() == Role.COMPANY_ADMIN;
        Offer offer = offerRepository.findByApplicationId(application.getId()).orElse(null);

        switch (status) {
            case SUBMITTED, UNDER_REVIEW, SHORTLISTED -> {
                actions.add("ADVANCE");
                actions.add("REJECT");
                if (isAdmin && PREVIOUS_STAGE.containsKey(status)) {
                    actions.add("BACKTRACK");
                }
            }
            case INTERVIEW -> {
                actions.add("SCHEDULE_INTERVIEW");
                actions.add("REJECT");
                if (offer == null) {
                    actions.add("CREATE_OFFER");
                }
                if (isAdmin) {
                    actions.add("BACKTRACK");
                }
            }
            case OFFERED -> {
                actions.add("REJECT");
                if (offer != null && offer.getStatus() == OfferStatus.ACCEPTED) {
                    actions.add("MARK_HIRED");
                }
                if (isAdmin) {
                    actions.add("BACKTRACK");
                }
            }
            case REJECTED -> {
                if (isAdmin) {
                    actions.add("REOPEN");
                }
            }
            default -> {
            }
        }
        if (status != ApplicationStatus.HIRED && status != ApplicationStatus.WITHDRAWN
                && status != ApplicationStatus.REJECTED) {
            actions.add("ADD_NOTE");
        }
        return actions;
    }

    /** Shared stage-move bookkeeping: status, attribution, event, audit, candidate notice. */
    void transitionTo(Application application, ApplicationStatus next) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        ApplicationStatus from = application.getStatus();
        application.setStatus(next);
        application.setStatusUpdatedBy(userRepository.getReferenceById(actor.getId()));
        application.setStatusUpdatedAt(Instant.now());

        eventService.record(application, ApplicationEventType.STAGE_CHANGED,
                Map.of("from", from.name(), "to", next.name()));
        auditService.log("APPLICATION_STATUS_CHANGED", "APPLICATION",
                application.getId().toString(),
                Map.of("from", from.name(), "to", next.name(),
                        "candidate", application.getCandidate().getEmail(),
                        "jobTitle", application.getJob().getTitle()));
        notifyCandidateOfStatus(application, next);
    }

    private void requireActiveStage(Application application) {
        if (LOCKED_STATES.contains(application.getStatus())
                || application.getStatus() == ApplicationStatus.REJECTED) {
            throw ApiException.badRequest("Application in status " + application.getStatus()
                    + " can no longer be updated");
        }
    }

    private void requireCompanyAdminAccess(Application application) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        requireRecruiterAccess(application.getJob().getCompany().getId());
        if (actor.getRole() != Role.COMPANY_ADMIN) {
            throw ApiException.forbidden("Only company administrators can perform this correction");
        }
    }

    @Transactional(readOnly = true)
    public ResumeDownload downloadResume(UUID applicationId) {
        Application application = find(applicationId);
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        if (actor.getRole() == Role.CANDIDATE) {
            if (!application.getCandidate().getId().equals(actor.getId())) {
                throw ApiException.forbidden("Not your application");
            }
        } else {
            requireRecruiterAccess(application.getJob().getCompany().getId());
        }
        Resource resource = new FileSystemResource(
                fileStorageService.resolve(application.getResumeStoredPath()));
        if (!resource.exists()) {
            throw ApiException.notFound("Resume file no longer exists");
        }
        return new ResumeDownload(resource, application.getResumeFileName(),
                application.getResumeContentType());
    }

    @Transactional
    public void rescreen(UUID applicationId) {
        Application application = find(applicationId);
        requireRecruiterAccess(application.getJob().getCompany().getId());
        ScreeningResult sr = application.getScreeningResult();
        if (sr != null && sr.getStatus() == ScreeningStatus.PROCESSING) {
            throw ApiException.badRequest("Screening is already in progress");
        }
        auditService.log("APPLICATION_RESCREEN_REQUESTED", "APPLICATION", applicationId.toString(),
                Map.of("jobTitle", application.getJob().getTitle()));
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                screeningService.queueScreening(applicationId);
            }
        });
    }

    public record ResumeDownload(Resource resource, String fileName, String contentType) {
    }

    // ------------------------------------------------------------ helpers

    private void notifyCandidateOfStatus(Application application, ApplicationStatus status) {
        String jobTitle = application.getJob().getTitle();
        String company = application.getJob().getCompany().getName();

        String title;
        String message;
        boolean email = true;
        switch (status) {
            case SHORTLISTED -> {
                title = "You've been shortlisted for " + jobTitle;
                message = "Great news! " + company + " has shortlisted you for the position of "
                        + jobTitle + ". They may reach out soon with next steps.";
            }
            case INTERVIEW -> {
                title = "Interview stage: " + jobTitle;
                message = company + " has moved your application for " + jobTitle
                        + " to the interview stage. Expect an email or call with the interview details.";
            }
            case OFFERED -> {
                title = "Job offer: " + jobTitle;
                message = "Congratulations! " + company + " has extended an offer for the position of "
                        + jobTitle + ".";
            }
            case HIRED -> {
                title = "Welcome aboard: " + jobTitle;
                message = "Congratulations! " + company + " has marked you as hired for " + jobTitle + ".";
            }
            case REJECTED -> {
                title = "Update on your application for " + jobTitle;
                message = "Thank you for your interest in " + jobTitle + " at " + company
                        + ". After careful consideration, they have decided to move forward with other "
                        + "candidates. We encourage you to apply for other openings.";
            }
            case UNDER_REVIEW -> {
                title = "Your application is under review";
                message = company + " is now reviewing your application for " + jobTitle + ".";
                email = false;
            }
            default -> {
                return;
            }
        }
        notificationService.notify(application.getCandidate(),
                NotificationType.APPLICATION_STATUS_CHANGED, title, message,
                "/candidate/applications", email);
    }

    /** Reset a reactivated application's screening back to a clean PENDING state. */
    private void resetScreening(Application application) {
        ScreeningResult sr = application.getScreeningResult();
        if (sr == null) {
            sr = ScreeningResult.builder().application(application).build();
            application.setScreeningResult(sr);
        }
        sr.setStatus(ScreeningStatus.PENDING);
        sr.setMatchScore(null);
        sr.setSkillsScore(null);
        sr.setExperienceScore(null);
        sr.setEducationScore(null);
        sr.setExtractedSkills(null);
        sr.setExtractedEducation(null);
        sr.setExtractedExperienceYears(null);
        sr.setBiasFlag(false);
        sr.setBiasFlagReason(null);
        sr.setMatchedSkills(null);
        sr.setMissingRequired(null);
        sr.setMissingOptional(null);
        sr.setReasoning(null);
        sr.setParseQuality(null);
        sr.setParseWarnings(null);
        sr.setIdentityVerified(true);
        sr.setIdentityFlags(null);
        sr.setIdentitySummary(null);
        sr.setExtractedName(null);
        sr.setExtractedEmail(null);
        sr.setExtractedPhone(null);
        sr.setResumeFingerprint(null);
        sr.setErrorMessage(null);
        sr.setScreenedAt(null);
    }

    private Application find(UUID applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> ApiException.notFound("Application not found"));
    }

    private void requireRecruiterAccess(UUID companyId) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        if (actor.getRole() != Role.COMPANY_ADMIN && actor.getRole() != Role.RECRUITER) {
            throw ApiException.forbidden("Only company users can review applications");
        }
        if (!companyId.equals(actor.getCompanyId())) {
            throw ApiException.forbidden("This application belongs to another company");
        }
    }
}
