package com.resumeai.service;

import com.resumeai.common.exception.ApiException;
import com.resumeai.domain.CandidateDemographics;
import com.resumeai.domain.CandidateProfile;
import com.resumeai.domain.User;
import com.resumeai.domain.enums.Role;
import com.resumeai.dto.ProfileDtos;
import com.resumeai.dto.ProfileDtos.*;
import com.resumeai.repository.CandidateDemographicsRepository;
import com.resumeai.repository.CandidateProfileRepository;
import com.resumeai.repository.UserRepository;
import com.resumeai.security.SecurityUtils;
import com.resumeai.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The signed-in user's own profile. Everything here acts on the caller — there is no
 * "edit someone else's profile" path, which is why no tenant checks appear: the actor
 * id <em>is</em> the authorisation.
 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final CandidateDemographicsRepository demographicsRepository;
    private final FileStorageService fileStorageService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public MyProfileResponse myProfile() {
        User user = currentUser();
        return toResponse(user, candidateProfile(user, false));
    }

    @Transactional
    public MyProfileResponse updateStaffProfile(StaffProfileRequest request) {
        User user = currentUser();
        if (user.getRole() == Role.CANDIDATE) {
            throw ApiException.badRequest("Candidates should use the candidate profile endpoint");
        }
        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        user.setPhone(trimToNull(request.phone()));
        user.setJobTitle(trimToNull(request.jobTitle()));
        user.setDepartment(trimToNull(request.department()));
        user.setBio(trimToNull(request.bio()));
        user.setLocation(trimToNull(request.location()));
        user.setTimeZone(trimToNull(request.timeZone()));
        user.setLocale(trimToNull(request.locale()));
        user.setLinkedinUrl(trimToNull(request.linkedinUrl()));
        if (user.getRole() == Role.RECRUITER) {
            user.setSpecializations(request.specializations());
            user.setYearsExperience(request.yearsExperience());
        }
        stampCompletion(user, null);
        auditService.log("PROFILE_UPDATED", "USER", user.getId().toString(), Map.of());
        return toResponse(user, null);
    }

    @Transactional
    public MyProfileResponse updateCandidateProfile(CandidateProfileRequest request) {
        User user = currentUser();
        if (user.getRole() != Role.CANDIDATE) {
            throw ApiException.badRequest("Only candidates have a candidate profile");
        }
        if (request.fullName() != null && !request.fullName().isBlank()) {
            user.setFullName(request.fullName().trim());
        }
        user.setPhone(trimToNull(request.phone()));
        user.setLocation(trimToNull(request.location()));
        user.setTimeZone(trimToNull(request.timeZone()));
        user.setLinkedinUrl(trimToNull(request.linkedinUrl()));

        CandidateProfile p = candidateProfile(user, true);
        p.setHeadline(trimToNull(request.headline()));
        p.setSummary(trimToNull(request.summary()));
        p.setWorkAuthorization(trimToNull(request.workAuthorization()));
        p.setLanguages(request.languages());
        p.setSkills(request.skills());
        p.setEducation(request.education());
        p.setExperience(request.experience());
        p.setCertifications(request.certifications());
        p.setPortfolioUrl(trimToNull(request.portfolioUrl()));
        p.setGithubUrl(trimToNull(request.githubUrl()));
        p.setWebsiteUrl(trimToNull(request.websiteUrl()));
        p.setSalaryMin(request.salaryMin());
        p.setSalaryMax(request.salaryMax());
        if (request.salaryCurrency() != null && !request.salaryCurrency().isBlank()) {
            p.setSalaryCurrency(request.salaryCurrency().trim());
        }
        p.setWorkArrangement(request.workArrangement());
        p.setAvailability(request.availability());
        p.setNoticePeriodDays(request.noticePeriodDays());
        p.setPreferredCategories(request.preferredCategories());
        if (request.openToRelocation() != null) {
            p.setOpenToRelocation(request.openToRelocation());
        }
        candidateProfileRepository.save(p);
        stampCompletion(user, p);
        auditService.log("PROFILE_UPDATED", "USER", user.getId().toString(), Map.of());
        return toResponse(user, p);
    }

    @Transactional
    public MyProfileResponse uploadPhoto(MultipartFile file) {
        User user = currentUser();
        String previous = user.getPhotoPath();
        user.setPhotoPath(fileStorageService.storeUserImage(file, user.getId()));
        // replace only once the new path is durably persisted, otherwise a rollback
        // leaves the row pointing at a file that has already been deleted
        deleteAfterCommit(previous);
        CandidateProfile p = candidateProfile(user, false);
        stampCompletion(user, p);
        auditService.log("PROFILE_PHOTO_UPDATED", "USER", user.getId().toString(), Map.of());
        return toResponse(user, p);
    }

    @Transactional
    public MyProfileResponse removePhoto() {
        User user = currentUser();
        String previous = user.getPhotoPath();
        if (previous == null) {
            throw ApiException.badRequest("There is no profile photo to remove");
        }
        user.setPhotoPath(null);
        deleteAfterCommit(previous);
        CandidateProfile p = candidateProfile(user, false);
        stampCompletion(user, p);
        auditService.log("PROFILE_PHOTO_REMOVED", "USER", user.getId().toString(), Map.of());
        return toResponse(user, p);
    }

    /**
     * Voluntary demographics. Separate endpoint and separate table: this data is for
     * aggregate reporting and must never ride along with the recruiter-visible profile.
     */
    @Transactional
    public MyProfileResponse updateDemographics(DemographicsRequest request) {
        User user = currentUser();
        if (user.getRole() != Role.CANDIDATE) {
            throw ApiException.badRequest("Demographics are only collected for candidates");
        }
        CandidateDemographics d = demographicsRepository.findById(user.getId())
                .orElseGet(() -> CandidateDemographics.builder().user(user).build());
        d.setDateOfBirth(request.dateOfBirth());
        d.setGender(trimToNull(request.gender()));
        d.setNationality(trimToNull(request.nationality()));
        d.setEthnicity(trimToNull(request.ethnicity()));
        d.setDisability(trimToNull(request.disability()));
        d.setVeteranStatus(trimToNull(request.veteranStatus()));
        if (d.getConsentedAt() == null) {
            d.setConsentedAt(Instant.now());
        }
        demographicsRepository.save(d);
        // the values themselves are never audited - only that a change happened
        auditService.log("DEMOGRAPHICS_UPDATED", "USER", user.getId().toString(), Map.of());
        return toResponse(user, candidateProfile(user, false));
    }

    @Transactional
    public MyProfileResponse deleteDemographics() {
        User user = currentUser();
        demographicsRepository.findById(user.getId()).ifPresent(demographicsRepository::delete);
        auditService.log("DEMOGRAPHICS_DELETED", "USER", user.getId().toString(), Map.of());
        return toResponse(user, candidateProfile(user, false));
    }

    // ------------------------------------------------------------- internals

    private User currentUser() {
        UserPrincipal actor = SecurityUtils.requireCurrentUser();
        return userRepository.findById(actor.getId())
                .orElseThrow(() -> ApiException.notFound("Your account could not be found"));
    }

    private CandidateProfile candidateProfile(User user, boolean createIfMissing) {
        if (user.getRole() != Role.CANDIDATE) {
            return null;
        }
        return candidateProfileRepository.findById(user.getId())
                .orElseGet(() -> createIfMissing
                        ? CandidateProfile.builder().user(user).build()
                        : null);
    }

    /** Stamp the completion moment once, so "when did they finish onboarding" stays true. */
    private void stampCompletion(User user, CandidateProfile candidate) {
        CandidateProfile p = candidate != null ? candidate : candidateProfile(user, false);
        boolean complete = ProfileCompletion.evaluate(user, p).complete();
        if (complete && user.getProfileCompletedAt() == null) {
            user.setProfileCompletedAt(Instant.now());
        } else if (!complete) {
            // dropping a required field re-opens the gate rather than silently
            // leaving a stale "completed" stamp behind
            user.setProfileCompletedAt(null);
        }
    }

    private void deleteAfterCommit(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                fileStorageService.deleteQuietly(relativePath);
            }
        });
    }

    private MyProfileResponse toResponse(User user, CandidateProfile p) {
        CandidateProfileResponse candidate = p == null ? null : new CandidateProfileResponse(
                p.getHeadline(), p.getSummary(), p.getWorkAuthorization(), p.getLanguages(), p.getSkills(),
                p.getEducation(), p.getExperience(), p.getCertifications(), p.getPortfolioUrl(),
                p.getGithubUrl(), p.getWebsiteUrl(), p.getSalaryMin(), p.getSalaryMax(),
                p.getSalaryCurrency(), p.getWorkArrangement(), p.getAvailability(),
                p.getNoticePeriodDays(), p.getPreferredCategories(), p.isOpenToRelocation());
        return new MyProfileResponse(user.getId(), user.getEmail(), user.getRole(), user.getFullName(),
                user.getPhone(), ProfileDtos.photoUrl(user.getId(), user.getPhotoPath()), user.getJobTitle(),
                user.getDepartment(), user.getBio(), user.getLocation(), user.getTimeZone(),
                user.getLocale(), user.getLinkedinUrl(), user.getSpecializations(),
                user.getYearsExperience(),
                user.getCompany() != null ? user.getCompany().getName() : null,
                candidate,
                demographicsRepository.existsById(user.getId()),
                ProfileCompletion.evaluate(user, p),
                user.getProfileCompletedAt());
    }

    private static String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
