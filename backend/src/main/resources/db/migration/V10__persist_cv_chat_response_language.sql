-- Preserve only the language the application knows when it renders a reply. No historical backfill.
ALTER TABLE cv_chat_messages
    ADD COLUMN IF NOT EXISTS response_language VARCHAR(2);

-- Schema adoption can replay V10 with the column and constraint already present.
ALTER TABLE cv_chat_messages
    DROP CONSTRAINT IF EXISTS ck_cv_chat_messages_response_language;

ALTER TABLE cv_chat_messages
    ADD CONSTRAINT ck_cv_chat_messages_response_language CHECK (
        response_language IS NULL OR
        (role = 'ASSISTANT' AND response_language IN ('en', 'fr', 'pt'))
    );
