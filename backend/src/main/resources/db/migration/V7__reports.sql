-- Real report documents: a row is created up front (QUEUED) and the PDF is rendered
-- asynchronously, so the UI always has something to poll and every generation attempt
-- is traceable even when it fails.
-- Prepared-by / approved-by identities are stored on the row as well as referenced,
-- because they are printed into the PDF and must survive a user rename or deactivation.

CREATE TABLE reports (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_no      VARCHAR(30) NOT NULL UNIQUE,
    type              VARCHAR(50) NOT NULL,   -- see ReportType
    scope             VARCHAR(20) NOT NULL,   -- PLATFORM | COMPANY | PERSONAL
    status            VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
    -- QUEUED | GENERATING | FAILED | DRAFT | PENDING_APPROVAL | APPROVED | REJECTED
    title             VARCHAR(200) NOT NULL,
    company_id        UUID REFERENCES companies(id),
    subject_user_id   UUID REFERENCES users(id),
    period_start      DATE,
    period_end        DATE,
    parameters        JSONB,
    generated_by      UUID REFERENCES users(id),
    generated_by_name VARCHAR(150) NOT NULL,
    generated_by_role VARCHAR(30) NOT NULL,
    generated_at      TIMESTAMPTZ,
    file_path         VARCHAR(500),
    file_size_bytes   BIGINT,
    page_count        INT,
    checksum          VARCHAR(64),
    failure_reason    TEXT,
    approved_by       UUID REFERENCES users(id),
    approved_by_name  VARCHAR(150),
    approved_by_role  VARCHAR(30),
    approved_at       TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reports_company ON reports(company_id, created_at DESC);
CREATE INDEX idx_reports_generated_by ON reports(generated_by, created_at DESC);
CREATE INDEX idx_reports_status ON reports(status);
CREATE INDEX idx_reports_type ON reports(type);

-- append-only sign-off trail; the winning entry is cached onto reports.approved_by
CREATE TABLE report_approvals (
    id         BIGSERIAL PRIMARY KEY,
    report_id  UUID NOT NULL REFERENCES reports(id) ON DELETE CASCADE,
    action     VARCHAR(30) NOT NULL,   -- GENERATED | SUBMITTED_FOR_APPROVAL | APPROVED | REJECTED
    actor_id   UUID REFERENCES users(id),
    actor_name VARCHAR(150) NOT NULL,
    actor_role VARCHAR(30) NOT NULL,
    note       TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_report_approvals_report ON report_approvals(report_id, created_at);
