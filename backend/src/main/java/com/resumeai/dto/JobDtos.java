package com.resumeai.dto;

import com.resumeai.domain.Job;
import com.resumeai.domain.JobQualification;
import com.resumeai.domain.enums.EducationLevel;
import com.resumeai.domain.enums.EmploymentType;
import com.resumeai.domain.enums.JobStatus;
import com.resumeai.domain.enums.WorkMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class JobDtos {

    private JobDtos() {
    }

    public record QualificationRequest(
            @NotBlank @Size(max = 150) String skill,
            @NotNull @DecimalMin("0.1") @DecimalMax("10") BigDecimal weight,
            boolean required) {
    }

    public record JobRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 120) String department,
            @Size(max = 200) String location,
            @NotNull EmploymentType employmentType,
            @NotNull WorkMode workMode,
            @NotBlank String description,
            String responsibilities,
            @DecimalMin("0") @DecimalMax("60") BigDecimal minExperienceYears,
            EducationLevel educationLevel,
            @DecimalMin("0") BigDecimal salaryMin,
            @DecimalMin("0") BigDecimal salaryMax,
            @Size(max = 10) String salaryCurrency,
            LocalDate deadline,
            @NotEmpty @Valid List<QualificationRequest> qualifications) {
    }

    public record QualificationResponse(UUID id, String skill, BigDecimal weight, boolean required) {
        public static QualificationResponse from(JobQualification q) {
            return new QualificationResponse(q.getId(), q.getSkill(), q.getWeight(), q.isRequired());
        }
    }

    public record JobResponse(
            UUID id,
            UUID companyId,
            String companyName,
            String title,
            String department,
            String location,
            EmploymentType employmentType,
            WorkMode workMode,
            String description,
            String responsibilities,
            BigDecimal minExperienceYears,
            EducationLevel educationLevel,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            String salaryCurrency,
            LocalDate deadline,
            JobStatus status,
            Instant publishedAt,
            Instant createdAt,
            String createdByName,
            List<QualificationResponse> qualifications,
            Long applicationCount) {

        public static JobResponse from(Job j, Long applicationCount) {
            return new JobResponse(
                    j.getId(),
                    j.getCompany().getId(),
                    j.getCompany().getName(),
                    j.getTitle(),
                    j.getDepartment(),
                    j.getLocation(),
                    j.getEmploymentType(),
                    j.getWorkMode(),
                    j.getDescription(),
                    j.getResponsibilities(),
                    j.getMinExperienceYears(),
                    j.getEducationLevel(),
                    j.getSalaryMin(),
                    j.getSalaryMax(),
                    j.getSalaryCurrency(),
                    j.getDeadline(),
                    j.getStatus(),
                    j.getPublishedAt(),
                    j.getCreatedAt(),
                    j.getCreatedBy() != null ? j.getCreatedBy().getFullName() : null,
                    j.getQualifications().stream().map(QualificationResponse::from).toList(),
                    applicationCount);
        }
    }

    /** Public job card - no internal details like qualification weights. */
    public record PublicJobResponse(
            UUID id,
            UUID companyId,
            String companyName,
            String companyIndustry,
            String companyLogoUrl,
            String title,
            String department,
            String location,
            EmploymentType employmentType,
            WorkMode workMode,
            String description,
            String responsibilities,
            BigDecimal minExperienceYears,
            EducationLevel educationLevel,
            BigDecimal salaryMin,
            BigDecimal salaryMax,
            String salaryCurrency,
            LocalDate deadline,
            Instant publishedAt,
            List<String> skills) {

        public static PublicJobResponse from(Job j) {
            return new PublicJobResponse(
                    j.getId(),
                    j.getCompany().getId(),
                    j.getCompany().getName(),
                    j.getCompany().getIndustry(),
                    CompanyDtos.mediaUrl(j.getCompany().getId(), j.getCompany().getLogoPath()),
                    j.getTitle(),
                    j.getDepartment(),
                    j.getLocation(),
                    j.getEmploymentType(),
                    j.getWorkMode(),
                    j.getDescription(),
                    j.getResponsibilities(),
                    j.getMinExperienceYears(),
                    j.getEducationLevel(),
                    j.getSalaryMin(),
                    j.getSalaryMax(),
                    j.getSalaryCurrency(),
                    j.getDeadline(),
                    j.getPublishedAt(),
                    j.getQualifications().stream().map(JobQualification::getSkill).toList());
        }
    }
}
