-- =============================================================
-- User profiles: a real professional identity per role, plus the first
-- user-scoped media (avatars).
--
-- Candidate data lives in its own table rather than 20 more nullable columns on
-- users, and demographics live in a THIRD table on purpose: recruiter-facing
-- queries join candidate_profiles and never candidate_demographics, so gender /
-- date of birth / nationality cannot leak into a hiring decision view by an
-- accidental SELECT *. That separation is the enforcement mechanism, not a
-- convention someone has to remember.
-- =============================================================

-- ---- shared + staff profile fields (all roles) ----
ALTER TABLE users
    ADD COLUMN photo_path           VARCHAR(500),
    ADD COLUMN job_title            VARCHAR(150),
    ADD COLUMN department           VARCHAR(120),
    ADD COLUMN bio                  TEXT,
    ADD COLUMN location             VARCHAR(200),
    ADD COLUMN time_zone            VARCHAR(60),
    ADD COLUMN locale               VARCHAR(20),
    ADD COLUMN linkedin_url         VARCHAR(255),
    -- recruiter-specific, kept here because it is only two fields
    ADD COLUMN specializations      JSONB,
    ADD COLUMN years_experience     NUMERIC(4,1),
    -- set the first time the required set is satisfied; drives the onboarding gate
    ADD COLUMN profile_completed_at TIMESTAMPTZ;

-- Existing accounts predate onboarding. Treat seeded/legacy staff as complete so
-- nobody is locked out of an app they were already using; candidates are left
-- incomplete so they get the new profile flow.
UPDATE users SET profile_completed_at = created_at WHERE role <> 'CANDIDATE';

-- ---- candidate professional profile ----
CREATE TABLE candidate_profiles (
    user_id              UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    headline             VARCHAR(200),
    summary              TEXT,
    nationality_note     VARCHAR(120),   -- work eligibility, NOT demographic nationality
    languages            JSONB,          -- [{language, proficiency}]
    skills               JSONB,          -- ["Java", "Spring Boot", ...]
    education            JSONB,          -- [{institution, degree, field, startYear, endYear, grade}]
    experience           JSONB,          -- [{company, title, startDate, endDate, current, description}]
    certifications       JSONB,          -- [{name, issuer, issuedOn, expiresOn, credentialUrl}]
    portfolio_url        VARCHAR(255),
    github_url           VARCHAR(255),
    website_url          VARCHAR(255),
    salary_min           NUMERIC(14,2),
    salary_max           NUMERIC(14,2),
    salary_currency      VARCHAR(10) DEFAULT 'USD',
    work_arrangement     VARCHAR(20),    -- REMOTE | HYBRID | ONSITE | FLEXIBLE
    availability         VARCHAR(30),    -- IMMEDIATE | WITHIN_A_MONTH | WITHIN_THREE_MONTHS | NOT_LOOKING
    notice_period_days   INT,
    preferred_categories JSONB,          -- ["Backend", "Platform", ...]
    open_to_relocation   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ---- candidate demographics: voluntary, aggregate-reporting only ----
-- Deliberately a separate table. Nothing a recruiter can reach joins it; it
-- exists for diversity reporting in aggregate, and every column is optional.
CREATE TABLE candidate_demographics (
    user_id        UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    date_of_birth  DATE,
    -- free text rather than an enum: accepted values differ by jurisdiction and
    -- an enum would force people into buckets that do not describe them
    gender         VARCHAR(40),
    nationality    VARCHAR(100),
    ethnicity      VARCHAR(100),
    disability     VARCHAR(40),
    veteran_status VARCHAR(40),
    -- records that the candidate actively chose to share, for lawful-basis evidence
    consented_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
