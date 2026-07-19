-- Resume identity verification (advisory, like bias_flag): compares the
-- applicant's account name/email/phone against what was read from the resume,
-- plus a normalized-text fingerprint used to detect the same resume submitted
-- by a different candidate.

ALTER TABLE screening_results
    ADD COLUMN identity_verified  BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN identity_flags     JSONB,
    ADD COLUMN identity_summary   TEXT,
    ADD COLUMN extracted_name     VARCHAR(200),
    ADD COLUMN extracted_email    VARCHAR(320),
    ADD COLUMN extracted_phone    VARCHAR(50),
    ADD COLUMN resume_fingerprint VARCHAR(64);

-- speeds up the "same resume by a different candidate" duplicate lookup
CREATE INDEX idx_screening_results_fingerprint
    ON screening_results (resume_fingerprint);
