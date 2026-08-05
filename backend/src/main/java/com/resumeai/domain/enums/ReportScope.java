package com.resumeai.domain.enums;

/**
 * How wide a report reaches. Drives who may read it and how it is signed off.
 *
 * <p>Only COMPANY reports get a counter-signature, because only there does a
 * superior exist: a company administrator signs off a recruiter's or a fellow
 * administrator's work. PLATFORM reports are produced by the platform
 * administrator, who sits at the top of the chain with nobody above them, and
 * PERSONAL reports describe their own author. Both of those are final on
 * generation and carry the author's own acknowledgement instead — asking someone
 * to "request approval" from themselves would be an empty ceremony.
 */
public enum ReportScope {
    PLATFORM,
    COMPANY,
    PERSONAL
}
