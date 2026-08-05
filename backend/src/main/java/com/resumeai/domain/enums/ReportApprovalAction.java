package com.resumeai.domain.enums;

/** One entry in a report's approval trail. */
public enum ReportApprovalAction {
    GENERATED,
    /** Author's own sign-off, used where no superior exists (platform and personal reports). */
    ACKNOWLEDGED,
    SUBMITTED_FOR_APPROVAL,
    APPROVED,
    REJECTED
}
