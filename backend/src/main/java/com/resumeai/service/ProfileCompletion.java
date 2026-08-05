package com.resumeai.service;

import com.resumeai.domain.CandidateProfile;
import com.resumeai.domain.User;
import com.resumeai.domain.enums.Role;
import com.resumeai.dto.ProfileDtos.CompletionResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The single definition of "how complete is this profile", used by both the gate and
 * the onboarding UI so they can never disagree.
 *
 * <p>The required set is deliberately small (the hybrid gate): enough that a colleague
 * or recruiter sees a real person, not so much that anyone abandons onboarding. Everything
 * else counts toward the percentage but never blocks access.
 */
final class ProfileCompletion {

    private ProfileCompletion() {
    }

    /** A labelled predicate: the label is what the UI shows as "still missing". */
    private record Check(String label, Supplier<Boolean> present) {
    }

    static CompletionResponse evaluate(User user, CandidateProfile candidate) {
        Map<String, Supplier<Boolean>> required = new LinkedHashMap<>();
        Map<String, Supplier<Boolean>> optional = new LinkedHashMap<>();

        // Every role is a person with a face and a place; that is the whole required set
        // for staff, and the reason the gate is cheap to satisfy.
        required.put("Full name", () -> notBlank(user.getFullName()));
        required.put("Profile photo", () -> notBlank(user.getPhotoPath()));

        switch (user.getRole()) {
            case SUPER_ADMIN -> {
                // Platform staff are never gated (see gated()), so nothing may sit in the
                // required set - otherwise this would report "incomplete" while the gate
                // reports "complete", and the two would disagree about the same profile.
                required.remove("Profile photo");
                optional.put("Profile photo", () -> notBlank(user.getPhotoPath()));
                optional.put("Job title", () -> notBlank(user.getJobTitle()));
                optional.put("Time zone", () -> notBlank(user.getTimeZone()));
            }
            case COMPANY_ADMIN -> {
                required.put("Job title", () -> notBlank(user.getJobTitle()));
                required.put("Location", () -> notBlank(user.getLocation()));
                optional.put("Department", () -> notBlank(user.getDepartment()));
                optional.put("Contact phone", () -> notBlank(user.getPhone()));
                optional.put("Time zone", () -> notBlank(user.getTimeZone()));
                optional.put("Preferred language", () -> notBlank(user.getLocale()));
                optional.put("Short bio", () -> notBlank(user.getBio()));
                optional.put("LinkedIn", () -> notBlank(user.getLinkedinUrl()));
            }
            case RECRUITER -> {
                required.put("Job title", () -> notBlank(user.getJobTitle()));
                required.put("Location", () -> notBlank(user.getLocation()));
                optional.put("Department", () -> notBlank(user.getDepartment()));
                optional.put("Areas of specialization", () -> notEmpty(user.getSpecializations()));
                optional.put("Years of recruiting experience", () -> user.getYearsExperience() != null);
                optional.put("Contact phone", () -> notBlank(user.getPhone()));
                optional.put("Time zone", () -> notBlank(user.getTimeZone()));
                optional.put("Professional bio", () -> notBlank(user.getBio()));
                optional.put("LinkedIn", () -> notBlank(user.getLinkedinUrl()));
            }
            case CANDIDATE -> {
                required.put("Professional headline", () -> candidate != null && notBlank(candidate.getHeadline()));
                required.put("Location", () -> notBlank(user.getLocation()));
                required.put("At least one skill", () -> candidate != null && notEmpty(candidate.getSkills()));
                optional.put("Professional summary", () -> candidate != null && notBlank(candidate.getSummary()));
                optional.put("Work experience", () -> candidate != null && notEmpty(candidate.getExperience()));
                optional.put("Education", () -> candidate != null && notEmpty(candidate.getEducation()));
                optional.put("Languages", () -> candidate != null && notEmpty(candidate.getLanguages()));
                optional.put("Certifications", () -> candidate != null && notEmpty(candidate.getCertifications()));
                optional.put("Phone number", () -> notBlank(user.getPhone()));
                optional.put("Preferred work arrangement",
                        () -> candidate != null && candidate.getWorkArrangement() != null);
                optional.put("Availability", () -> candidate != null && candidate.getAvailability() != null);
                optional.put("Salary expectations",
                        () -> candidate != null && (candidate.getSalaryMin() != null || candidate.getSalaryMax() != null));
                optional.put("Preferred job categories",
                        () -> candidate != null && notEmpty(candidate.getPreferredCategories()));
                optional.put("LinkedIn", () -> notBlank(user.getLinkedinUrl()));
                optional.put("Portfolio or website",
                        () -> candidate != null && (notBlank(candidate.getPortfolioUrl())
                                || notBlank(candidate.getWebsiteUrl()) || notBlank(candidate.getGithubUrl())));
            }
        }

        List<String> missingRequired = missing(required);
        List<String> missingOptional = missing(optional);
        int total = required.size() + optional.size();
        int done = total - missingRequired.size() - missingOptional.size();
        // required fields are worth more: a profile with a photo and title but no bio is
        // materially usable, so the bar should feel close after the required set is done
        int percentage = total == 0 ? 100 : weighted(required, optional, missingRequired, missingOptional);
        return new CompletionResponse(missingRequired.isEmpty(), percentage, missingRequired, missingOptional);
    }

    private static int weighted(Map<String, Supplier<Boolean>> required,
                                Map<String, Supplier<Boolean>> optional,
                                List<String> missingRequired,
                                List<String> missingOptional) {
        int requiredDone = required.size() - missingRequired.size();
        int optionalDone = optional.size() - missingOptional.size();
        // required carries 70% of the bar, optional the remaining 30%
        double req = required.isEmpty() ? 1 : (double) requiredDone / required.size();
        double opt = optional.isEmpty() ? 1 : (double) optionalDone / optional.size();
        return (int) Math.round(req * 70 + opt * 30);
    }

    private static List<String> missing(Map<String, Supplier<Boolean>> checks) {
        List<String> out = new ArrayList<>();
        checks.forEach((label, present) -> {
            if (!Boolean.TRUE.equals(present.get())) {
                out.add(label);
            }
        });
        return out;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static boolean notEmpty(Collection<?> c) {
        return c != null && !c.isEmpty();
    }

    /** Roles whose profile is gated at all. */
    static boolean gated(Role role) {
        return role != Role.SUPER_ADMIN;
    }
}
