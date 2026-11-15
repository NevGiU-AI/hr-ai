-- Application-owned visible chat history. Spring AI memory remains a bounded model-context window; these tables
-- preserve the reviewer-visible timeline and minimized citation navigation metadata for the conversation lifetime.
CREATE TABLE IF NOT EXISTS cv_chat_messages (
    id BIGSERIAL PRIMARY KEY,
    conversation_id VARCHAR(36) NOT NULL,
    role VARCHAR(10) NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_cv_chat_messages_conversation
        FOREIGN KEY (conversation_id)
        REFERENCES cv_chat_conversations (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_cv_chat_messages_conversation_order
    ON cv_chat_messages (conversation_id, id);

CREATE TABLE IF NOT EXISTS cv_chat_message_citations (
    id BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL,
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    reference VARCHAR(80) NOT NULL,
    candidate_id BIGINT NOT NULL,
    document_id BIGINT,
    page_number INTEGER,
    CONSTRAINT uk_cv_chat_message_citations_reference UNIQUE (message_id, reference),
    CONSTRAINT uk_cv_chat_message_citations_order UNIQUE (message_id, display_order),
    CONSTRAINT fk_cv_chat_message_citations_message
        FOREIGN KEY (message_id)
        REFERENCES cv_chat_messages (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS ix_cv_chat_message_citations_message_order
    ON cv_chat_message_citations (message_id, display_order);

-- Preserve the reviewer-visible text of conversations created before V8. Citation navigation cannot be reconstructed
-- from Spring AI memory because that bounded model-context table stores only role and content. The NOT EXISTS guard
-- keeps the adoption path idempotent if an existing installation recreates Flyway history and reapplies migrations.
INSERT INTO cv_chat_messages (conversation_id, role, content, created_at)
SELECT memory.conversation_id,
       memory.type,
       memory.content,
       memory."timestamp" AT TIME ZONE 'UTC'
FROM spring_ai_chat_memory memory
WHERE memory.type IN ('USER', 'ASSISTANT')
  AND NOT EXISTS (
      SELECT 1
      FROM cv_chat_messages visible
      WHERE visible.conversation_id = memory.conversation_id
  )
ORDER BY memory.conversation_id, memory.sequence_id;
