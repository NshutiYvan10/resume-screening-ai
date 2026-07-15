package com.resumeai.service;

import com.resumeai.common.exception.ApiException;
import com.resumeai.domain.Application;
import com.resumeai.domain.Job;
import com.resumeai.domain.ScreeningResult;
import com.resumeai.domain.User;
import com.resumeai.domain.enums.*;
import com.resumeai.dto.ApplicationDtos.ApplicationResponse;
import com.resumeai.dto.ApplicationDtos.StatusUpdateRequest;
import com.resumeai.dto.CommonDtos.PageResponse;
import com.resumeai.repository.ApplicationRepository;
import com.resumeai.repository.JobRepository;
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
import java.util.EnumSet;
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

    private final ApplicationRepository applicationRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final ScreeningService screeningService;
    private final NotificationService notificationService;
    private final AuditService auditService;

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
        if (applicationRepository.existsByJobIdAndCandidateId(jobId, actor.getId())) {
            throw ApiException.conflict("You have already applied for this job");
        }
        if (resume == null || resume.isEmpty()) {
            throw ApiException.badRequest("A resume file is required");
        }

        User candidate = userRepository.getReferenceById(actor.getId());
        String storedPath = fileStorageService.storeResume(resume, job.getCompany().getId());

        Application application = Application.builder()
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

        auditService.log("APPLICATION_SUBMITTED", "APPLICATION", application.getId().toString(),
                Map.of("jobTitle", job.getTitle(), "jobId", jobId.toString()));

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
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
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
        return ApplicationResponse.from(application, false, false);
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

    @Transactional
    public ApplicationResponse updateStatus(UUID applicationId, StatusUpdateRequest request) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        Application application = find(applicationId);
        requireRecruiterAccess(application.getJob().getCompany().getId());

        ApplicationStatus newStatus = request.status();
        if (newStatus == ApplicationStatus.SUBMITTED || newStatus == ApplicationStatus.WITHDRAWN) {
            throw ApiException.badRequest("Applications cannot be moved to " + newStatus + " manually");
        }
        if (LOCKED_STATES.contains(application.getStatus())) {
            throw ApiException.badRequest("Application in status " + application.getStatus()
                    + " can no longer be updated");
        }
        if (application.getStatus() == newStatus) {
            throw ApiException.badRequest("Application is already " + newStatus);
        }

        ApplicationStatus previous = application.getStatus();
        application.setStatus(newStatus);
        application.setRecruiterNote(request.note());
        application.setStatusUpdatedBy(userRepository.getReferenceById(actor.getId()));
        application.setStatusUpdatedAt(Instant.now());

        auditService.log("APPLICATION_STATUS_CHANGED", "APPLICATION", applicationId.toString(),
                Map.of("from", previous.name(), "to", newStatus.name(),
                        "candidate", application.getCandidate().getEmail(),
                        "jobTitle", application.getJob().getTitle()));

        notifyCandidateOfStatus(application, newStatus);
        return ApplicationResponse.from(application, true, true);
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
