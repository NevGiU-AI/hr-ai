-- Application-owned authorization scope for durable CV-chat conversations.
-- Spring AI memory is deliberately subordinate to these rows: a model-visible conversation ID is never sufficient
-- to authorize access without the matching organization and owner recorded here.

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'uk_app_users_id_organization'
                     AND conrelid = 'app_users'::regclass) THEN
        ALTER TABLE app_users
            ADD CONSTRAINT uk_app_users_id_organization UNIQUE (id, organization_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'uk_jobs_id_organization'
                     AND conrelid = 'jobs'::regclass) THEN
        ALTER TABLE jobs
            ADD CONSTRAINT uk_jobs_id_organization UNIQUE (id, organization_id);
    END IF;
END
$$;

CREATE TABLE IF NOT EXISTS cv_chat_conversations (
    id VARCHAR(36) PRIMARY KEY,
    organization_id VARCHAR(100) NOT NULL,
    owner_user_id BIGINT NOT NULL,
    confirmed_job_id BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_cv_chat_conversations_id_organization UNIQUE (id, organization_id),
    CONSTRAINT fk_cv_chat_conversations_owner_tenant
        FOREIGN KEY (owner_user_id, organization_id)
        REFERENCES app_users (id, organization_id),
    CONSTRAINT fk_cv_chat_conversations_job_tenant
        FOREIGN KEY (confirmed_job_id, organization_id)
        REFERENCES jobs (id, organization_id)
);

CREATE INDEX IF NOT EXISTS ix_cv_chat_conversations_owner
    ON cv_chat_conversations (organization_id, owner_user_id, updated_at DESC);
CREATE INDEX IF NOT EXISTS ix_cv_chat_conversations_expiry
    ON cv_chat_conversations (expires_at);

CREATE TABLE IF NOT EXISTS cv_chat_conversation_candidates (
    conversation_id VARCHAR(36) NOT NULL,
    candidate_id BIGINT NOT NULL,
    organization_id VARCHAR(100) NOT NULL,
    display_order INTEGER NOT NULL CHECK (display_order >= 0 AND display_order < 5),
    PRIMARY KEY (conversation_id, candidate_id),
    CONSTRAINT uk_cv_chat_conversation_candidates_order UNIQUE (conversation_id, display_order),
    CONSTRAINT fk_cv_chat_conversation_candidates_conversation_tenant
        FOREIGN KEY (conversation_id, organization_id)
        REFERENCES cv_chat_conversations (id, organization_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_cv_chat_conversation_candidates_candidate_tenant
        FOREIGN KEY (candidate_id, organization_id)
        REFERENCES candidates (id, organization_id)
        ON DELETE CASCADE
);

-- This is Spring AI's PostgreSQL JDBC ChatMemoryRepository contract. Flyway owns creation so startup never attempts
-- an implicit schema mutation. The foreign key makes application scope deletion the final cleanup backstop.
CREATE TABLE IF NOT EXISTS spring_ai_chat_memory (
    conversation_id VARCHAR(36) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(10) NOT NULL CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp" TIMESTAMP NOT NULL,
    sequence_id BIGINT NOT NULL,
    CONSTRAINT fk_spring_ai_chat_memory_conversation
        FOREIGN KEY (conversation_id)
        REFERENCES cv_chat_conversations (id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS spring_ai_chat_memory_conversation_id_timestamp_idx
    ON spring_ai_chat_memory (conversation_id, "timestamp");
CREATE INDEX IF NOT EXISTS spring_ai_chat_memory_conversation_id_sequence_id_idx
    ON spring_ai_chat_memory (conversation_id, sequence_id);
