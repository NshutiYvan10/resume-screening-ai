-- =============================================================
-- Candidate document library, so candidates stop re-uploading the same material
-- for every application.
--
-- Résumés are FILES; cover letters are TEXT templates. They are separate tables
-- because they are genuinely different things: a text template has no stored path,
-- content type, size or checksum, and it feeds applications.cover_letter (TEXT)
-- rather than a file path. Forcing both into one table would leave half the columns
-- meaningless on every row.
--
-- THE IMPORTANT PART: an application keeps its OWN copy of both.
-- applications.resume_stored_path is the immutable evidence a screening decision was
-- made against - screening_results scores that exact file, resume_fingerprint detects
-- duplicate résumés from it, and approved PDF reports cite those outcomes. Likewise
-- applications.cover_letter holds the text as submitted. If an application merely
-- pointed at a library row, then renaming, editing, replacing or deleting an entry
-- would retroactively rewrite or destroy the evidence behind a completed screening,
-- an interview decision or a signed-off report.
--
-- So applying COPIES: the résumé bytes to the application's own path, and the cover
-- letter text into applications.cover_letter. The source_* columns below are
-- provenance only ("applied using Backend CV v3") and are allowed to go null.
-- =============================================================

CREATE TABLE candidate_documents (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind         VARCHAR(20) NOT NULL,    -- RESUME (files only; cover letters are text, see below)
    -- what the candidate calls it; renaming touches only this
    label        VARCHAR(150) NOT NULL,
    file_name    VARCHAR(255) NOT NULL,   -- original upload name, for download
    stored_path  VARCHAR(500) NOT NULL,
    content_type VARCHAR(100),
    size_bytes   BIGINT,
    -- SHA-256 of the bytes, so a re-upload of an identical file can be recognised
    checksum     VARCHAR(64),
    is_default   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_candidate_documents_user ON candidate_documents(user_id, kind, created_at DESC);

-- At most one default per kind per candidate, enforced by the database rather than by
-- remembering to clear the previous default in application code.
CREATE UNIQUE INDEX uq_candidate_documents_default
    ON candidate_documents(user_id, kind) WHERE is_default;

-- ---- cover letters: saved text templates ----
CREATE TABLE candidate_cover_letters (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label      VARCHAR(150) NOT NULL,
    body       TEXT NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_candidate_cover_letters_user ON candidate_cover_letters(user_id, created_at DESC);
CREATE UNIQUE INDEX uq_candidate_cover_letters_default
    ON candidate_cover_letters(user_id) WHERE is_default;

-- ---- provenance on applications ----
-- ON DELETE SET NULL on both: clearing out the library must never damage a submitted
-- application, because the application holds its own copy of the résumé and the text.
ALTER TABLE applications
    ADD COLUMN source_document_id     UUID REFERENCES candidate_documents(id) ON DELETE SET NULL,
    ADD COLUMN source_cover_letter_id UUID REFERENCES candidate_cover_letters(id) ON DELETE SET NULL;
