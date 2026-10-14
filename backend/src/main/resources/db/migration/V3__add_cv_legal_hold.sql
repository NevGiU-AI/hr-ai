ALTER TABLE cv_documents
    ADD COLUMN IF NOT EXISTS legal_hold boolean NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS legal_hold_at timestamp(6) with time zone,
    ADD COLUMN IF NOT EXISTS legal_hold_by_user_id bigint;

-- Match the scheduler predicate so PostgreSQL can locate overdue, deletable originals without scanning every CV.
CREATE INDEX IF NOT EXISTS idx_cv_documents_expiry_candidates
    ON cv_documents (retention_until, id)
    WHERE storage_key IS NOT NULL AND legal_hold = false;
