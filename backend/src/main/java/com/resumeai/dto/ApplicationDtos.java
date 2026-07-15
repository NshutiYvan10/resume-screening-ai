package com.resumeai.dto;

import com.resumeai.domain.Application;
import com.resumeai.domain.ScreeningResult;
import com.resumeai.domain.enums.ApplicationStatus;
import com.resumeai.domain.enums.ScreeningStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ApplicationDtos {

    private ApplicationDtos() {
    }

    public record StatusUpdateRequest(
            @NotNull ApplicationStatus status,
            @Size(max = 4000) String note) {
    }

    public record ScreeningResponse(
            ScreeningStatus status,
            BigDecimal matchScore,
            BigDecimal skillsScore,
            BigDecimal experienceScore,
            BigDecimal educationScore,
            List<String> extractedSkills,
            String extractedEducation,
            BigDecimal extractedExperienceYears,
            boolean biasFlag,
            String biasFlagReason,
            String errorMessage,
            Instant screenedAt) {

        public static ScreeningResponse from(ScreeningResult sr) {
            if (sr == null) {
                return null;
            }
            return new ScreeningResponse(sr.getStatus(), sr.getMatchScore(), sr.getSkillsScore(),
                    sr.getExperienceScore(), sr.getEducationScore(), sr.getExtractedSkills(),
                    sr.getExtractedEducation(), sr.getExtractedExperienceYears(), sr.isBiasFlag(),
                    sr.getBiasFlagReason(), sr.getErrorMessage(), sr.getScreenedAt());
        }
    }

    public record ApplicationResponse(
            UUID id,
            UUID jobId,
            String jobTitle,
            UUID companyId,
            String companyName,
            UUID candidateId,
            String candidateName,
            String candidateEmail,
            String candidatePhone,
            ApplicationStatus status,
            String coverLetter,
            String resumeFileName,
            String recruiterNote,
            Instant appliedAt,
            Instant statusUpdatedAt,
            ScreeningResponse screening) {

        public static ApplicationResponse from(Application a, boolean includeCandidateContact,
                                               boolean includeRecruiterNote) {
            return new ApplicationResponse(
                    a.getId(),
                    a.getJob().getId(),
                    a.getJob().getTitle(),
                    a.getJob().getCompany().getId(),
                    a.getJob().getCompany().getName(),
                    a.getCandidate().getId(),
                    a.getCandidate().getFullName(),
                    includeCandidateContact ? a.getCandidate().getEmail() : null,
                    includeCandidateContact ? a.getCandidate().getPhone() : null,
                    a.getStatus(),
                    a.getCoverLetter(),
                    a.getResumeFileName(),
                    includeRecruiterNote ? a.getRecruiterNote() : null,
                    a.getAppliedAt(),
                    a.getStatusUpdatedAt(),
                    ScreeningResponse.from(a.getScreeningResult()));
        }
    }
}
