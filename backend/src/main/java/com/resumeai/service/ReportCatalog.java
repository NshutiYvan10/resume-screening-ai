package com.resumeai.service;

import com.resumeai.common.exception.ApiException;
import com.resumeai.domain.enums.ReportScope;
import com.resumeai.domain.enums.ReportType;
import com.resumeai.domain.enums.Role;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The single declaration of what report types exist, who may generate them and how
 * they are rendered. Adding a report type means adding an enum value, one entry here
 * and one Thymeleaf fragment - no service, controller or UI change is required.
 *
 * <p>Whether a type needs sign-off is derived from its scope rather than configured
 * per type, so a new oversight report cannot accidentally skip approval.
 */
public final class ReportCatalog {

    /**
     * @param roles roles allowed to generate this type
     * @param fragment Thymeleaf fragment under templates/reports/ that renders the body
     * @param requiresJob true when the report is about one job posting and needs a jobId
     */
    public record Definition(ReportType type,
                             ReportScope scope,
                             Set<Role> roles,
                             String title,
                             String description,
                             String fragment,
                             boolean requiresJob) {

        /**
         * Only company-scope reports are counter-signed. A platform report's author is
         * the platform administrator, who has no superior to approve it, so it is
         * acknowledged by its author like a personal report.
         */
        public boolean requiresApproval() {
            return scope == ReportScope.COMPANY;
        }
    }

    private static final Map<ReportType, Definition> BY_TYPE = new EnumMap<>(ReportType.class);

    private static void define(ReportType type, ReportScope scope, Set<Role> roles,
                               String title, String description, String fragment, boolean requiresJob) {
        BY_TYPE.put(type, new Definition(type, scope, roles, title, description, fragment, requiresJob));
    }

    static {
        // ---------------- platform oversight (super admin) ----------------
        define(ReportType.PLATFORM_ACTIVITY_SUMMARY, ReportScope.PLATFORM, Set.of(Role.SUPER_ADMIN),
                "Platform Activity Summary",
                "Tenants, users, job postings and application volume across the whole platform.",
                "platform-activity", false);
        define(ReportType.PLATFORM_AUDIT_TRAIL, ReportScope.PLATFORM, Set.of(Role.SUPER_ADMIN),
                "Platform Audit Trail",
                "Chronological record of privileged actions taken across all companies.",
                "audit-trail", false);
        define(ReportType.AI_SCREENING_HEALTH, ReportScope.PLATFORM, Set.of(Role.SUPER_ADMIN),
                "AI Screening Health Report",
                "Screening throughput, failure rate and fairness/identity flags raised by the AI service.",
                "screening-health", false);

        // ---------------- company oversight (company admin) ----------------
        define(ReportType.COMPANY_HIRING_PERFORMANCE, ReportScope.COMPANY, Set.of(Role.COMPANY_ADMIN),
                "Hiring Performance Report",
                "Pipeline, time-to-hire, offer outcomes and match-score quality for the company.",
                "hiring-performance", false);
        define(ReportType.TEAM_PERFORMANCE, ReportScope.COMPANY, Set.of(Role.COMPANY_ADMIN),
                "Team Performance Report",
                "Per-recruiter workload and outcomes across the hiring team.",
                "team-performance", false);
        define(ReportType.JOB_POSTING_PERFORMANCE, ReportScope.COMPANY, Set.of(Role.COMPANY_ADMIN),
                "Job Posting Performance Report",
                "Applications received and average match score for every posting.",
                "job-performance", false);
        define(ReportType.COMPANY_AUDIT_TRAIL, ReportScope.COMPANY, Set.of(Role.COMPANY_ADMIN),
                "Company Audit Trail",
                "Who did what inside this company, for compliance and dispute handling.",
                "audit-trail", false);

        // ---------------- recruiter ----------------
        define(ReportType.RECRUITER_ACTIVITY, ReportScope.PERSONAL, Set.of(Role.RECRUITER),
                "My Recruiting Activity",
                "Your postings, applications received and screening quality.",
                "recruiter-activity", false);
        define(ReportType.CANDIDATE_SHORTLIST, ReportScope.COMPANY,
                Set.of(Role.RECRUITER, Role.COMPANY_ADMIN),
                "Candidate Shortlist",
                "Ranked candidates for one job posting, for sharing with a hiring manager.",
                "candidate-shortlist", true);

        // ---------------- candidate ----------------
        define(ReportType.CANDIDATE_APPLICATION_HISTORY, ReportScope.PERSONAL, Set.of(Role.CANDIDATE),
                "My Application History",
                "Every role you applied to, where it stands and how you were scored.",
                "candidate-history", false);
    }

    public static Definition require(ReportType type) {
        Definition def = BY_TYPE.get(type);
        if (def == null) {
            throw ApiException.badRequest("Unknown report type");
        }
        return def;
    }

    /** Report types this role is allowed to generate, in catalogue order. */
    public static List<Definition> forRole(Role role) {
        return BY_TYPE.values().stream().filter(d -> d.roles().contains(role)).toList();
    }

    public static void requireRoleMay(Role role, ReportType type) {
        if (!require(type).roles().contains(role)) {
            throw ApiException.forbidden("Your role cannot generate this report type");
        }
    }

    private ReportCatalog() {
    }
}
