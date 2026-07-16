-- Screening explainability: per-qualification evidence, reasoning narrative
-- and parse-quality signal so recruiters can verify (or distrust) a score.

ALTER TABLE screening_results
    ADD COLUMN matched_skills   JSONB,
    ADD COLUMN missing_required JSONB,
    ADD COLUMN missing_optional JSONB,
    ADD COLUMN reasoning        TEXT,
    ADD COLUMN parse_quality    VARCHAR(10),
    ADD COLUMN parse_warnings   JSONB;
