package com.resumeai.service;

import com.resumeai.common.exception.ApiException;
import com.resumeai.domain.Company;
import com.resumeai.domain.Job;
import com.resumeai.domain.JobQualification;
import com.resumeai.domain.enums.CompanyStatus;
import com.resumeai.domain.enums.EmploymentType;
import com.resumeai.domain.enums.JobStatus;
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

import java.time.Instant;
import java.time.LocalDate;
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
        applyRequest(job, request);

        auditService.log("JOB_UPDATED", "JOB", job.getId().toString(), Map.of("title", job.getTitle()));
        return JobResponse.from(job, applicationRepository.countByJobId(job.getId()));
    }

    @Transactional
    public JobResponse changeStatus(UUID jobId, JobStatus newStatus) {
        Job job = find(jobId);
        requireCompanyAccess(job);

        boolean allowed = switch (newStatus) {
            case PUBLISHED -> job.getStatus() == JobStatus.DRAFT || job.getStatus() == JobStatus.CLOSED;
            case CLOSED -> job.getStatus() == JobStatus.PUBLISHED;
            case ARCHIVED -> job.getStatus() == JobStatus.DRAFT || job.getStatus() == JobStatus.CLOSED;
            case DRAFT -> false;
        };
        if (!allowed) {
            throw ApiException.badRequest(
                    "Cannot move job from " + job.getStatus() + " to " + newStatus);
        }
        if (newStatus == JobStatus.PUBLISHED) {
            if (job.getQualifications().isEmpty()) {
                throw ApiException.badRequest("Add at least one qualification before publishing");
            }
            if (job.getDeadline() != null && job.getDeadline().isBefore(LocalDate.now())) {
                throw ApiException.badRequest("Deadline is in the past - update it before publishing");
            }
            job.setPublishedAt(Instant.now());
        }
        job.setStatus(newStatus);

        auditService.log("JOB_" + newStatus.name(), "JOB", job.getId().toString(),
                Map.of("title", job.getTitle()));
        return JobResponse.from(job, applicationRepository.countByJobId(job.getId()));
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

    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
