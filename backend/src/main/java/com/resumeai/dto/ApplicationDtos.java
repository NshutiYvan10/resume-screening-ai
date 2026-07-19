package com.resumeai.dto;

import com.resumeai.domain.Application;
import com.resumeai.domain.ScreeningResult;
import com.resumeai.domain.enums.ApplicationStatus;
import com.resumeai.domain.enums.RejectionReason;
import com.resumeai.domain.enums.ScreeningStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ApplicationDtos {

    private ApplicationDtos() {
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
            List<String> matchedSkills,
            List<String> missingRequired,
            List<String> missingOptional,
            String reasoning,
            String parseQuality,
            List<String> parseWarnings,
            boolean identityVerified,
            List<String> identityFlags,
            String identitySummary,
            String extractedName,
            String extractedEmail,
            String extractedPhone,
            String errorMessage,
            Instant screenedAt) {

        public static ScreeningResponse from(ScreeningResult sr) {
            if (sr == null) {
                return null;
            }
            return new ScreeningResponse(sr.getStatus(), sr.getMatchScore(), sr.getSkillsScore(),
                    sr.getExperienceScore(), sr.getEducationScore(), sr.getExtractedSkills(),
                    sr.getExtractedEducation(), sr.getExtractedExperienceYears(), sr.isBiasFlag(),
                    sr.getBiasFlagReason(), sr.getMatchedSkills(), sr.getMissingRequired(),
                    sr.getMissingOptional(), sr.getReasoning(), sr.getParseQuality(),
                    sr.getParseWarnings(), sr.isIdentityVerified(), sr.getIdentityFlags(),
                    sr.getIdentitySummary(), sr.getExtractedName(), sr.getExtractedEmail(),
                    sr.getExtractedPhone(), sr.getErrorMessage(), sr.getScreenedAt());
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
            RejectionReason rejectionReason,
            String rejectionNote,
            Instant hiredAt,
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
                    includeRecruiterNote ? a.getRejectionReason() : null,
                    includeRecruiterNote ? a.getRejectionNote() : null,
                    a.getHiredAt(),
                    a.getAppliedAt(),
                    a.getStatusUpdatedAt(),
                    ScreeningResponse.from(a.getScreeningResult()));
        }
    }
}
