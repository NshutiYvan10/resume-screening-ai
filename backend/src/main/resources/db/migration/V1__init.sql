-- =============================================================
-- AI-Powered Resume Screening System - initial schema
-- =============================================================

CREATE TABLE companies (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(200) NOT NULL,
    industry        VARCHAR(120),
    website         VARCHAR(255),
    company_size    VARCHAR(50),
    location        VARCHAR(200),
    description     TEXT,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE | SUSPENDED
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(100),
    full_name       VARCHAR(150) NOT NULL,
    phone           VARCHAR(40),
    role            VARCHAR(30) NOT NULL,                    -- SUPER_ADMIN | COMPANY_ADMIN | RECRUITER | CANDIDATE
    status          VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',   -- PENDING_VERIFICATION | ACTIVE | DISABLED
    company_id      UUID REFERENCES companies(id),
    email_verified  BOOLEAN NOT NULL DEFAULT FALSE,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_company ON users(company_id);
CREATE INDEX idx_users_role ON users(role);

CREATE TABLE invitations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email           VARCHAR(255) NOT NULL,
    type            VARCHAR(20) NOT NULL,                    -- COMPANY | TEAM_MEMBER
    role            VARCHAR(30),                             -- for TEAM_MEMBER: COMPANY_ADMIN | RECRUITER
    company_name    VARCHAR(200),                            -- for COMPANY invitations
    company_id      UUID REFERENCES companies(id),           -- for TEAM_MEMBER invitations
    token_hash      VARCHAR(100) NOT NULL UNIQUE,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | ACCEPTED | REVOKED | EXPIRED
    invited_by      UUID REFERENCES users(id),
    expires_at      TIMESTAMPTZ NOT NULL,
    accepted_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_invitations_email ON invitations(email);
CREATE INDEX idx_invitations_company ON invitations(company_id);

-- email verification + password reset tokens
CREATE TABLE user_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(100) NOT NULL UNIQUE,
    type            VARCHAR(30) NOT NULL,                    -- EMAIL_VERIFICATION | PASSWORD_RESET
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_tokens_user ON user_tokens(user_id);

CREATE TABLE refresh_tokens (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash      VARCHAR(100) NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);

CREATE TABLE jobs (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id           UUID NOT NULL REFERENCES companies(id),
    created_by           UUID REFERENCES users(id),
    title                VARCHAR(200) NOT NULL,
    department           VARCHAR(120),
    location             VARCHAR(200),
    employment_type      VARCHAR(30) NOT NULL DEFAULT 'FULL_TIME',  -- FULL_TIME | PART_TIME | CONTRACT | INTERNSHIP
    work_mode            VARCHAR(20) NOT NULL DEFAULT 'ONSITE',     -- ONSITE | REMOTE | HYBRID
    description          TEXT NOT NULL,
    responsibilities     TEXT,
    min_experience_years NUMERIC(4,1) DEFAULT 0,
    education_level      VARCHAR(50),                               -- CERTIFICATE | DIPLOMA | BACHELORS | MASTERS | PHD
    salary_min           NUMERIC(14,2),
    salary_max           NUMERIC(14,2),
    salary_currency      VARCHAR(10) DEFAULT 'USD',
    deadline             DATE,
    status               VARCHAR(20) NOT NULL DEFAULT 'DRAFT',      -- DRAFT | PUBLISHED | CLOSED | ARCHIVED
    published_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_jobs_company ON jobs(company_id);
CREATE INDEX idx_jobs_status ON jobs(status);

CREATE TABLE job_qualifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id      UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    skill       VARCHAR(150) NOT NULL,
    weight      NUMERIC(5,2) NOT NULL DEFAULT 1,
    required    BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_job_qualifications_job ON job_qualifications(job_id);

CREATE TABLE applications (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id              UUID NOT NULL REFERENCES jobs(id),
    candidate_id        UUID NOT NULL REFERENCES users(id),
    status              VARCHAR(20) NOT NULL DEFAULT 'SUBMITTED',
    -- SUBMITTED | UNDER_REVIEW | SHORTLISTED | INTERVIEW | OFFERED | HIRED | REJECTED | WITHDRAWN
    cover_letter        TEXT,
    resume_file_name    VARCHAR(255) NOT NULL,
    resume_stored_path  VARCHAR(500) NOT NULL,
    resume_content_type VARCHAR(100),
    recruiter_note      TEXT,
    status_updated_by   UUID REFERENCES users(id),
    status_updated_at   TIMESTAMPTZ,
    applied_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_application_job_candidate UNIQUE (job_id, candidate_id)
);
CREATE INDEX idx_applications_job ON applications(job_id);
CREATE INDEX idx_applications_candidate ON applications(candidate_id);
CREATE INDEX idx_applications_status ON applications(status);

CREATE TABLE screening_results (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id              UUID NOT NULL UNIQUE REFERENCES applications(id) ON DELETE CASCADE,
    status                      VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING | PROCESSING | COMPLETED | FAILED
    match_score                 NUMERIC(5,2),
    skills_score                NUMERIC(5,2),
    experience_score            NUMERIC(5,2),
    education_score             NUMERIC(5,2),
    extracted_skills            JSONB,
    extracted_education         VARCHAR(255),
    extracted_experience_years  NUMERIC(4,1),
    bias_flag                   BOOLEAN NOT NULL DEFAULT FALSE,
    bias_flag_reason            TEXT,
    error_message               TEXT,
    screened_at                 TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_screening_results_status ON screening_results(status);

CREATE TABLE notifications (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        VARCHAR(50) NOT NULL,
    title       VARCHAR(200) NOT NULL,
    message     TEXT NOT NULL,
    link        VARCHAR(500),
    read_at     TIMESTAMPTZ,
    email_sent  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user ON notifications(user_id, created_at DESC);

CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    UUID,
    actor_email VARCHAR(255),
    actor_role  VARCHAR(30),
    company_id  UUID,
    action      VARCHAR(80) NOT NULL,
    entity_type VARCHAR(50),
    entity_id   VARCHAR(64),
    details     JSONB,
    ip_address  VARCHAR(64),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_company ON audit_logs(company_id, created_at DESC);
CREATE INDEX idx_audit_logs_actor ON audit_logs(actor_id, created_at DESC);
CREATE INDEX idx_audit_logs_created ON audit_logs(created_at DESC);
