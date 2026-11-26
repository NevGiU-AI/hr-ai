-- Existing messages have no authoritative outcome; do not infer one from their text or citations.
-- Baseline adoption may replay migrations against an already populated schema.
ALTER TABLE cv_chat_messages
    ADD COLUMN IF NOT EXISTS answer_kind VARCHAR(32);

ALTER TABLE cv_chat_messages
    DROP CONSTRAINT IF EXISTS ck_cv_chat_messages_answer_kind;

ALTER TABLE cv_chat_messages
    ADD CONSTRAINT ck_cv_chat_messages_answer_kind CHECK (
        answer_kind IS NULL OR
        (role = 'ASSISTANT' AND answer_kind IN ('ANSWER', 'INSUFFICIENT_EVIDENCE', 'OUT_OF_SCOPE', 'TOOL_STATUS'))
    );
