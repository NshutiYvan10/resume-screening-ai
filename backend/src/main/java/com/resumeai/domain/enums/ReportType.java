package com.resumeai.domain.enums;

/**
 * Catalogue of report types. Role eligibility, scope, title and template are
 * declared centrally in {@code ReportCatalog}; adding a type means adding a value
 * here, a catalog entry and a Thymeleaf fragment - nothing else changes.
 */
public enum ReportType {
    // super admin - platform oversight
    PLATFORM_ACTIVITY_SUMMARY,
    PLATFORM_AUDIT_TRAIL,
    AI_SCREENING_HEALTH,
    // company admin - company oversight
    COMPANY_HIRING_PERFORMANCE,
    TEAM_PERFORMANCE,
    JOB_POSTING_PERFORMANCE,
    COMPANY_AUDIT_TRAIL,
    // recruiter
    RECRUITER_ACTIVITY,
    CANDIDATE_SHORTLIST,
    // candidate
    CANDIDATE_APPLICATION_HISTORY
}
