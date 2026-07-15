-- Job posting approval workflow: recruiters submit, company admins approve/publish.

ALTER TABLE jobs
    ADD COLUMN submitted_by      UUID REFERENCES users(id),
    ADD COLUMN submitted_at      TIMESTAMPTZ,
    ADD COLUMN approved_by       UUID REFERENCES users(id),
    ADD COLUMN approved_at       TIMESTAMPTZ,
    ADD COLUMN rejection_reason  TEXT;

-- Existing PUBLISHED jobs are treated as already approved (backfill so they don't
-- look like they skipped the new gate).
UPDATE jobs SET approved_at = published_at WHERE status = 'PUBLISHED' AND published_at IS NOT NULL;
