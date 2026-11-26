-- Existing messages have no authoritative outcome; do not infer one from their text or citations.
-- Baseline adoption may replay migrations against an already populated schema.
ALTER TABLE cv_chat_messages
    ADD COLUMN IF NOT EXISTS answer_kind VARCHAR(32);

-- On a normal V8 -> V9 upgrade the constraint does not exist, so this is a no-op.
-- When adopting a schema that already has V9's objects but no Flyway history, the constraint
-- may already exist. Drop it before adding the intended definition to avoid a duplicate name.
-- This does not modify message rows or their saved outcomes.
ALTER TABLE cv_chat_messages
    DROP CONSTRAINT IF EXISTS ck_cv_chat_messages_answer_kind;

-- NULL remains valid for legacy messages and user turns. Only assistant turns may carry an outcome.
ALTER TABLE cv_chat_messages
    ADD CONSTRAINT ck_cv_chat_messages_answer_kind CHECK (
        answer_kind IS NULL OR
        (role = 'ASSISTANT' AND answer_kind IN ('ANSWER', 'INSUFFICIENT_EVIDENCE', 'OUT_OF_SCOPE', 'TOOL_STATUS'))
    );
