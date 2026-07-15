-- Rich employer-branding profile: media + storytelling fields

ALTER TABLE companies
    ADD COLUMN logo_path      VARCHAR(500),
    ADD COLUMN cover_path     VARCHAR(500),
    ADD COLUMN tagline        VARCHAR(200),
    ADD COLUMN founded_year   INT,
    ADD COLUMN mission        TEXT,
    ADD COLUMN company_values JSONB,
    ADD COLUMN benefits       JSONB,
    ADD COLUMN linkedin_url   VARCHAR(255),
    ADD COLUMN twitter_url    VARCHAR(255);

CREATE TABLE company_photos (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id  UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    path        VARCHAR(500) NOT NULL,
    caption     VARCHAR(200),
    sort_order  INT NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_company_photos_company ON company_photos(company_id, sort_order);
