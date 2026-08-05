package com.resumeai.domain.enums;

/**
 * Report lifecycle. QUEUED/GENERATING/FAILED cover rendering; DRAFT onwards is
 * the approval track. A PERSONAL report goes straight from GENERATING to APPROVED
 * because it needs no counter-signature.
 */
public enum ReportStatus {
    QUEUED,
    GENERATING,
    FAILED,
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED
}
