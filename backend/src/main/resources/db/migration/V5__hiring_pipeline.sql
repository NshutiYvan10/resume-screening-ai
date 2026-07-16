-- =============================================================
-- Post-screening hiring pipeline: interviews with scorecards,
-- offer approval workflow, rejection compliance, activity timeline.
-- =============================================================

-- rejection compliance + hire record on applications
ALTER TABLE applications
    ADD COLUMN rejection_reason VARCHAR(40),
    ADD COLUMN rejection_note   TEXT,
    ADD COLUMN hired_at         TIMESTAMPTZ;

CREATE TABLE interviews (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id   UUID NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    scheduled_at     TIMESTAMPTZ NOT NULL,
    duration_minutes INT NOT NULL DEFAULT 60,
    type             VARCHAR(20) NOT NULL DEFAULT 'VIDEO',   -- PHONE | VIDEO | ONSITE | TECHNICAL
    location         VARCHAR(500),                            -- meeting link or room
    notes            TEXT,                                    -- instructions for the panel
    status           VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',-- SCHEDULED | COMPLETED | CANCELLED
    created_by       UUID REFERENCES users(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_interviews_application ON interviews(application_id);

CREATE TABLE interview_panel (
    interview_id UUID NOT NULL REFERENCES interviews(id) ON DELETE CASCADE,
    user_id      UUID NOT NULL REFERENCES users(id),
    PRIMARY KEY (interview_id, user_id)
);

CREATE TABLE interview_feedback (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    interview_id   UUID NOT NULL REFERENCES interviews(id) ON DELETE CASCADE,
    interviewer_id UUID NOT NULL REFERENCES users(id),
    rating         INT NOT NULL,                              -- 1 (poor) .. 4 (excellent)
    recommendation VARCHAR(20) NOT NULL,                      -- STRONG_YES | YES | NO | STRONG_NO
    strengths      TEXT,
    concerns       TEXT,
    submitted_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_feedback_per_interviewer UNIQUE (interview_id, interviewer_id)
);

CREATE TABLE offers (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL UNIQUE REFERENCES applications(id) ON DELETE CASCADE,
    salary         NUMERIC(14,2) NOT NULL,
    currency       VARCHAR(10) NOT NULL DEFAULT 'USD',
    start_date     DATE,
    expires_at     DATE,
    notes          TEXT,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING_APPROVAL',
    -- PENDING_APPROVAL | APPROVED | EXTENDED | ACCEPTED | DECLINED
    created_by     UUID REFERENCES users(id),
    approved_by    UUID REFERENCES users(id),
    approved_at    TIMESTAMPTZ,
    extended_at    TIMESTAMPTZ,
    responded_at   TIMESTAMPTZ,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- per-candidate activity timeline: every pipeline action, attributable
CREATE TABLE application_events (
    id             BIGSERIAL PRIMARY KEY,
    application_id UUID NOT NULL REFERENCES applications(id) ON DELETE CASCADE,
    actor_id       UUID REFERENCES users(id),
    actor_name     VARCHAR(150),
    type           VARCHAR(40) NOT NULL,
    details        JSONB,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_application_events_app ON application_events(application_id, created_at);
