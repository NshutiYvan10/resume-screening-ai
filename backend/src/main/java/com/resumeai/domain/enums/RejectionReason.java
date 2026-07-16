package com.resumeai.domain.enums;

/**
 * Standardized rejection reasons. Required on every rejection so decisions
 * remain consistent, analyzable and defensible (EEOC/OFCCP-style record
 * keeping). Keep reasons job-related — never personal characteristics.
 */
public enum RejectionReason {
    MISSING_REQUIRED_SKILLS,
    INSUFFICIENT_EXPERIENCE,
    EDUCATION_REQUIREMENTS,
    FAILED_INTERVIEW,
    BETTER_CANDIDATE_SELECTED,
    SALARY_EXPECTATIONS,
    POSITION_CLOSED,
    UNRESPONSIVE,
    OTHER
}
