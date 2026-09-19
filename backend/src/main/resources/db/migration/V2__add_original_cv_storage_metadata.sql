ALTER TABLE cv_documents
    ADD COLUMN IF NOT EXISTS storage_key varchar(160),
    ADD COLUMN IF NOT EXISTS stored_at timestamp(6) with time zone,
    ADD COLUMN IF NOT EXISTS retention_until timestamp(6) with time zone;

CREATE UNIQUE INDEX IF NOT EXISTS uk_cv_documents_storage_key
    ON cv_documents (storage_key)
    WHERE storage_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_cv_documents_retention_until
    ON cv_documents (retention_until)
    WHERE retention_until IS NOT NULL;
