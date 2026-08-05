package com.resumeai.dto;

import com.resumeai.domain.enums.Availability;
import com.resumeai.domain.enums.Role;
import com.resumeai.domain.enums.WorkArrangement;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Requests and responses for the role-aware profile + onboarding flow. */
public final class ProfileDtos {

    private ProfileDtos() {
    }

    /** Public URL for a stored user photo, mirroring CompanyDtos.mediaUrl. */
    public static String photoUrl(UUID userId, String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return null;
        }
        int slash = Math.max(storedPath.lastIndexOf('/'), storedPath.lastIndexOf('\\'));
        return "/api/v1/media/user/" + userId + "/" + storedPath.substring(slash + 1);
    }

    // ----------------------------------------------------------- completion

    /**
     * How far along a profile is. {@code complete} gates access to the app; the
     * percentage and missing list drive the onboarding UI.
     */
    public record CompletionResponse(boolean complete,
                                     int percentage,
                                     List<String> missingRequired,
                                     List<String> missingOptional) {
    }

    // -------------------------------------------------------------- requests

    /** Shared/staff profile fields. Recruiter-only fields are ignored for other roles. */
    public record StaffProfileRequest(@Size(max = 150) String fullName,
                                      @Size(max = 40) String phone,
                                      @Size(max = 150) String jobTitle,
                                      @Size(max = 120) String department,
                                      @Size(max = 4000) String bio,
                                      @Size(max = 200) String location,
                                      @Size(max = 60) String timeZone,
                                      @Size(max = 20) String locale,
                                      @Size(max = 255) String linkedinUrl,
                                      List<@Size(max = 60) String> specializations,
                                      BigDecimal yearsExperience) {
    }

    /** Candidate professional profile. Demographics are deliberately NOT here. */
    public record CandidateProfileRequest(@Size(max = 150) String fullName,
                                          @Size(max = 40) String phone,
                                          @Size(max = 200) String headline,
                                          @Size(max = 4000) String summary,
                                          @Size(max = 200) String location,
                                          @Size(max = 60) String timeZone,
                                          @Size(max = 255) String linkedinUrl,
                                          @Size(max = 120) String workAuthorization,
                                          List<Map<String, Object>> languages,
                                          List<@Size(max = 80) String> skills,
                                          List<Map<String, Object>> education,
                                          List<Map<String, Object>> experience,
                                          List<Map<String, Object>> certifications,
                                          @Size(max = 255) String portfolioUrl,
                                          @Size(max = 255) String githubUrl,
                                          @Size(max = 255) String websiteUrl,
                                          BigDecimal salaryMin,
                                          BigDecimal salaryMax,
                                          @Size(max = 10) String salaryCurrency,
                                          WorkArrangement workArrangement,
                                          Availability availability,
                                          Integer noticePeriodDays,
                                          List<@Size(max = 80) String> preferredCategories,
                                          Boolean openToRelocation) {
    }

    /**
     * Voluntary demographics, submitted through their own endpoint so this data never
     * travels in the same payload as the recruiter-visible profile.
     */
    public record DemographicsRequest(LocalDate dateOfBirth,
                                      @Size(max = 40) String gender,
                                      @Size(max = 100) String nationality,
                                      @Size(max = 100) String ethnicity,
                                      @Size(max = 40) String disability,
                                      @Size(max = 40) String veteranStatus) {
    }

    // ------------------------------------------------------------- responses

    /** The signed-in user's own profile: everything they may see and edit. */
    public record MyProfileResponse(UUID id,
                                    String email,
                                    Role role,
                                    String fullName,
                                    String phone,
                                    String photoUrl,
                                    String jobTitle,
                                    String department,
                                    String bio,
                                    String location,
                                    String timeZone,
                                    String locale,
                                    String linkedinUrl,
                                    List<String> specializations,
                                    BigDecimal yearsExperience,
                                    String companyName,
                                    CandidateProfileResponse candidate,
                                    boolean demographicsProvided,
                                    CompletionResponse completion,
                                    Instant profileCompletedAt) {
    }

    public record CandidateProfileResponse(String headline,
                                           String summary,
                                           String workAuthorization,
                                           List<Map<String, Object>> languages,
                                           List<String> skills,
                                           List<Map<String, Object>> education,
                                           List<Map<String, Object>> experience,
                                           List<Map<String, Object>> certifications,
                                           String portfolioUrl,
                                           String githubUrl,
                                           String websiteUrl,
                                           BigDecimal salaryMin,
                                           BigDecimal salaryMax,
                                           String salaryCurrency,
                                           WorkArrangement workArrangement,
                                           Availability availability,
                                           Integer noticePeriodDays,
                                           List<String> preferredCategories,
                                           boolean openToRelocation) {
    }
}
