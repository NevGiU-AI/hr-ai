-- Existing messages have no authoritative outcome; do not infer one from their text or citations.
ALTER TABLE cv_chat_messages
    ADD COLUMN answer_kind VARCHAR(32),
    ADD CONSTRAINT ck_cv_chat_messages_answer_kind CHECK (
        answer_kind IS NULL OR
        (role = 'ASSISTANT' AND answer_kind IN ('ANSWER', 'INSUFFICIENT_EVIDENCE', 'OUT_OF_SCOPE', 'TOOL_STATUS'))
    );
