package com.resumeai.service;

import com.resumeai.common.exception.ApiException;
import com.resumeai.domain.Company;
import com.resumeai.domain.Job;
import com.resumeai.domain.JobQualification;
import com.resumeai.domain.User;
import com.resumeai.domain.enums.CompanyStatus;
import com.resumeai.domain.enums.EmploymentType;
import com.resumeai.domain.enums.JobStatus;
import com.resumeai.domain.enums.NotificationType;
import com.resumeai.domain.enums.Role;
import com.resumeai.domain.enums.WorkMode;
import com.resumeai.dto.CommonDtos.PageResponse;
import com.resumeai.dto.JobDtos.*;
import com.resumeai.repository.ApplicationRepository;
import com.resumeai.repository.CompanyRepository;
import com.resumeai.repository.JobRepository;
import com.resumeai.repository.UserRepository;
import com.resumeai.security.SecurityUtils;
import com.resumeai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final ScreeningService screeningService;

    // ------------------------------------------------------------- public

    @Transactional(readOnly = true)
    public PageResponse<PublicJobResponse> listPublic(String search, String location,
                                                      EmploymentType employmentType, WorkMode workMode,
                                                      UUID companyId, int page, int size) {
        return PageResponse.of(
                jobRepository.searchPublicJobs(emptyToNull(search), emptyToNull(location),
                        employmentType, workMode, companyId,
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "publishedAt"))),
                PublicJobResponse::from);
    }

    @Transactional(readOnly = true)
    public PublicJobResponse getPublic(UUID jobId) {
        Job job = find(jobId);
        if (job.getStatus() != JobStatus.PUBLISHED || job.getCompany().getStatus() != CompanyStatus.ACTIVE) {
            throw ApiException.notFound("Job not found");
        }
        return PublicJobResponse.from(job);
    }

    // ------------------------------------------------------- company side

    @Transactional(readOnly = true)
    public PageResponse<JobResponse> listCompanyJobs(JobStatus status, String search, int page, int size) {
        UUID companyId = requireCompany();
        return PageResponse.of(
                jobRepository.searchCompanyJobs(companyId, status, emptyToNull(search),
                        PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))),
                j -> JobResponse.from(j, applicationRepository.countByJobId(j.getId())));
    }

    @Transactional(readOnly = true)
    public JobResponse get(UUID jobId) {
        Job job = find(jobId);
        requireCompanyAccess(job);
        return JobResponse.from(job, applicationRepository.countByJobId(job.getId()));
    }

    @Transactional
    public JobResponse create(JobRequest request) {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        UUID companyId = requireCompany();
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> ApiException.notFound("Company not found"));
        if (company.getStatus() == CompanyStatus.SUSPENDED) {
            throw ApiException.forbidden("Your company is suspended - contact the platform administrator");
        }
        validateSalary(request);

        Job job = Job.builder()
                .company(company)
                .createdBy(userRepository.getReferenceById(actor.getId()))
                .build();
        applyRequest(job, request);
        jobRepository.save(job);

        auditService.log("JOB_CREATED", "JOB", job.getId().toString(), Map.of("title", job.getTitle()));
        return JobResponse.from(job, 0L);
    }

    @Transactional
    public JobResponse update(UUID jobId, JobRequest request) {
        Job job = find(jobId);
        requireCompanyAccess(job);
        if (job.getStatus() == JobStatus.ARCHIVED) {
            throw ApiException.badRequest("Archived jobs cannot be edited");
        }
        validateSalary(request);
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        boolean wasLive = job.getStatus() == JobStatus.PUBLISHED;
        String criteriaBefore = screeningCriteriaFingerprint(job);
        applyRequest(job, request);
        boolean criteriaChanged = !criteriaBefore.equals(screeningCriteriaFingerprint(job));

        // A recruiter editing content that is already live (or pending) must go back
        // through approval — otherwise the approval gate only guards the first publish.
        // Company admins have publish authority, so their edits stay in place.
        if (actor.getRole() != Role.COMPANY_ADMIN
                && (wasLive || job.getStatus() == JobStatus.PENDING_APPROVAL)) {
            job.setStatus(JobStatus.PENDING_APPROVAL);
            job.setApprovedBy(null);
            job.setApprovedAt(null);
            job.setRejectionReason(null);
            job.setSubmittedBy(userRepository.getReferenceById(actor.getId()));
            job.setSubmittedAt(Instant.now());
            auditService.log("JOB_EDIT_RESUBMITTED", "JOB", job.getId().toString(),
                    Map.of("title", job.getTitle(), "wasPublished", wasLive));
            notifyAdminsOfSubmission(job, actor);
        } else {
            auditService.log("JOB_UPDATED", "JOB", job.getId().toString(), Map.of("title", job.getTitle()));
        }

        // Existing AI scores were computed against the OLD criteria; re-screen every
        // application of this job once the edit commits so rankings stay truthful.
        if (criteriaChanged) {
            List<UUID> applicationIds = applicationRepository.findIdsByJobId(job.getId());
            if (!applicationIds.isEmpty()) {
                auditService.log("JOB_APPLICATIONS_RESCREEN_QUEUED", "JOB", job.getId().toString(),
                        Map.of("title", job.getTitle(), "applications", applicationIds.size()));
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        applicationIds.forEach(screeningService::queueScreening);
                    }
                });
            }
        }
        return JobResponse.from(job, applicationRepository.countByJobId(job.getId()));
    }

    /** Compact fingerprint of every field that feeds the AI screening. */
    private String screeningCriteriaFingerprint(Job job) {
        StringBuilder sb = new StringBuilder();
        sb.append(job.getTitle()).append('|')
                .append(job.getDescription()).append('|')
                .append(job.getMinExperienceYears()).append('|')
                .append(job.getEducationLevel());
        job.getQualifications().stream()
                .sorted(java.util.Comparator.comparing(JobQualification::getSkill))
                .forEach(q -> sb.append('|').append(q.getSkill()).append(':')
                        .append(q.getWeight()).append(':').append(q.isRequired()));
        return sb.toString();
    }

    /** Recruiter (or admin) sends a draft/closed job into the approval queue. */
    @Transactional
    public JobResponse submitForApproval(UUID jobId) {
        Job job = find(jobId);
        requireCompanyAccess(job);
        UserPrincipal actor = SecurityUtils.requireCurrentUser();

        if (job.getStatus() != JobStatus.DRAFT && job.getStatus() != JobStatus.CLOSED) {
            throw ApiException.badRequest("Only draft or closed jobs can be submitted for approval");
        }
        ensureCompanyActive(job);
        ensureReadyForBoard(job);

        job.setStatus(JobStatus.PENDING_APPROVAL);
        job.setSubmittedBy(userRepository.getReferenceById(actor.getId()));
        job.setSubmittedAt(Instant.now());
        job.setApprovedBy(null);
        job.setApprovedAt(null);
        job.setRejectionReason(null);

        auditService.log("JOB_SUBMITTED", "JOB", job.getId().toString(), Map.of("title", job.getTitle()));
        notifyAdminsOfSubmission(job, actor);
        return JobResponse.from(job, applicationRepository.countByJobId(job.getId()));
    }

    /** Company admin approves a pending job, which publishes it to the board. */
    @Transactional
    public JobResponse approve(UUID jobId) {
        Job job = find(jobId);
        UserPrincipal actor = requireAdminAccess(job);
        if (job.getStatus() != JobStatus.PENDING_APPROVAL) {
            throw ApiException.badRequest("Only jobs pending approval can be approved");
        }
        ensureCompanyActive(job);
        ensureReadyForBoard(job);
        markPublished(job, actor);

        auditService.log("JOB_APPROVED", "JOB", job.getId().toString(), Map.of("title", job.getTitle()));
        notifyDecision(job, true, null);
        return JobResponse.from(job, applicationRepository.countByJobId(job.getId()));
    }

    /** Company admin rejects a pending job, returning it to draft with a reason. */
    @Transactional
    public JobResponse reject(UUID jobId, String reason) {
        Job job = find(jobId);
        requireAdminAccess(job);
        if (job.getStatus() != JobStatus.PENDING_APPROVAL) {
            throw ApiException.badRequest("Only jobs pending approval can be rejected");
        }
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("A rejection reason is required");
        }
        job.setStatus(JobStatus.DRAFT);
        job.setRejectionReason(reason.trim());

        auditService.log("JOB_REJECTED", "JOB", job.getId().toString(),
                Map.of("title", job.getTitle(), "reason", reason.trim()));
        notifyDecision(job, false, reason.trim());
        return JobResponse.from(job, applicationRepository.countByJobId(job.getId()));
    }

    /** Company admin publishes directly, bypassing the approval queue (admin authority). */
    @Transactional
    public JobResponse publishDirectly(UUID jobId) {
        Job job = find(jobId);
        UserPrincipal actor = requireAdminAccess(job);
        if (job.getStatus() != JobStatus.DRAFT
                && job.getStatus() != JobStatus.CLOSED
                && job.getStatus() != JobStatus.PENDING_APPROVAL) {
            throw ApiException.badRequest("Cannot publish a job in status " + job.getStatus());
        }
        ensureCompanyActive(job);
        ensureReadyForBoard(job);
        markPublished(job, actor);

        auditService.log("JOB_PUBLISHED", "JOB", job.getId().toString(), Map.of("title", job.getTitle()));
        // if someone else submitted this job, tell them it's now live
        if (job.getSubmittedBy() != null && !job.getSubmittedBy().getId().equals(actor.getId())) {
            notifyDecision(job, true, null);
        }
        return JobResponse.from(job, applicationRepository.countByJobId(job.getId()));
    }

    @Transactional
    public JobResponse close(UUID jobId) {
        Job job = find(jobId);
        requireCompanyAccess(job);
        if (job.getStatus() != JobStatus.PUBLISHED) {
            throw ApiException.badRequest("Only published jobs can be closed");
        }
        job.setStatus(JobStatus.CLOSED);
        auditService.log("JOB_CLOSED", "JOB", job.getId().toString(), Map.of("title", job.getTitle()));
        return JobResponse.from(job, applicationRepository.countByJobId(job.getId()));
    }

    @Transactional
    public JobResponse archive(UUID jobId) {
        Job job = find(jobId);
        requireCompanyAccess(job);
        if (job.getStatus() != JobStatus.DRAFT && job.getStatus() != JobStatus.CLOSED) {
            throw ApiException.badRequest(
                    "Only draft or closed jobs can be archived. Reject a pending job or close a published one first.");
        }
        job.setStatus(JobStatus.ARCHIVED);
        auditService.log("JOB_ARCHIVED", "JOB", job.getId().toString(), Map.of("title", job.getTitle()));
        return JobResponse.from(job, applicationRepository.countByJobId(job.getId()));
    }

    private void markPublished(Job job, UserPrincipal actor) {
        job.setStatus(JobStatus.PUBLISHED);
        job.setApprovedBy(userRepository.getReferenceById(actor.getId()));
        job.setApprovedAt(Instant.now());
        job.setRejectionReason(null);
        job.setPublishedAt(Instant.now());
    }

    private void ensureReadyForBoard(Job job) {
        if (job.getQualifications().isEmpty()) {
            throw ApiException.badRequest("Add at least one qualification first");
        }
        if (job.getDeadline() != null && job.getDeadline().isBefore(LocalDate.now())) {
            throw ApiException.badRequest("The deadline is in the past - update it first");
        }
    }

    private void ensureCompanyActive(Job job) {
        if (job.getCompany().getStatus() == com.resumeai.domain.enums.CompanyStatus.SUSPENDED) {
            throw ApiException.forbidden("Your company is suspended - contact the platform administrator");
        }
    }

    private void notifyAdminsOfSubmission(Job job, UserPrincipal actor) {
        userRepository.findByCompanyIdAndRoleIn(job.getCompany().getId(), List.of(Role.COMPANY_ADMIN))
                .forEach(admin -> notificationService.notify(admin, NotificationType.JOB_SUBMITTED,
                        "Job awaiting approval: " + job.getTitle(),
                        actor.getFullName() + " submitted \"" + job.getTitle() + "\" for approval.",
                        "/company/approvals", false));
    }

    private void notifyDecision(Job job, boolean approved, String reason) {
        User recipient = job.getSubmittedBy() != null ? job.getSubmittedBy() : job.getCreatedBy();
        if (recipient == null) {
            return;
        }
        if (approved) {
            notificationService.notify(recipient, NotificationType.JOB_APPROVED,
                    "Job approved & published: " + job.getTitle(),
                    "Your job \"" + job.getTitle() + "\" was approved and is now live on the job board.",
                    "/company/jobs", false);
        } else {
            notificationService.notify(recipient, NotificationType.JOB_REJECTED,
                    "Job returned for changes: " + job.getTitle(),
                    "Your job \"" + job.getTitle() + "\" was not approved. Reason: " + reason,
                    "/company/jobs", false);
        }
    }

    @Transactional
    public void delete(UUID jobId) {
        Job job = find(jobId);
        requireCompanyAccess(job);
        if (job.getStatus() != JobStatus.DRAFT) {
            throw ApiException.badRequest("Only draft jobs can be deleted - close or archive published jobs");
        }
        String title = job.getTitle();
        jobRepository.delete(job);
        auditService.log("JOB_DELETED", "JOB", jobId.toString(), Map.of("title", title));
    }

    // ------------------------------------------------------------ helpers

    private void applyRequest(Job job, JobRequest request) {
        job.setTitle(request.title());
        job.setDepartment(request.department());
        job.setLocation(request.location());
        job.setEmploymentType(request.employmentType());
        job.setWorkMode(request.workMode());
        job.setDescription(request.description());
        job.setResponsibilities(request.responsibilities());
        job.setMinExperienceYears(request.minExperienceYears());
        job.setEducationLevel(request.educationLevel());
        job.setSalaryMin(request.salaryMin());
        job.setSalaryMax(request.salaryMax());
        job.setSalaryCurrency(request.salaryCurrency() != null ? request.salaryCurrency() : "USD");
        job.setDeadline(request.deadline());

        job.getQualifications().clear();
        request.qualifications().forEach(q -> job.getQualifications().add(
                JobQualification.builder()
                        .job(job)
                        .skill(q.skill().trim())
                        .weight(q.weight())
                        .required(q.required())
                        .build()));
    }

    private void validateSalary(JobRequest request) {
        if (request.salaryMin() != null && request.salaryMax() != null
                && request.salaryMin().compareTo(request.salaryMax()) > 0) {
            throw ApiException.badRequest("Minimum salary cannot exceed maximum salary");
        }
    }

    private Job find(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> ApiException.notFound("Job not found"));
    }

    private UUID requireCompany() {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        if (actor.getRole() != Role.COMPANY_ADMIN && actor.getRole() != Role.RECRUITER) {
            throw ApiException.forbidden("Only company users can manage jobs");
        }
        if (actor.getCompanyId() == null) {
            throw ApiException.forbidden("You are not associated with a company");
        }
        return actor.getCompanyId();
    }

    private void requireCompanyAccess(Job job) {
        UUID companyId = requireCompany();
        if (!job.getCompany().getId().equals(companyId)) {
            throw ApiException.forbidden("This job belongs to another company");
        }
    }

    /** Approval decisions (approve/reject/direct-publish) are company-admin authority only. */
    private UserPrincipal requireAdminAccess(Job job) {
        requireCompanyAccess(job);
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        if (actor.getRole() != Role.COMPANY_ADMIN) {
            throw ApiException.forbidden("Only company administrators can approve or publish jobs");
        }
        return actor;
    }

    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
