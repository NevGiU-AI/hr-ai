CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS hstore;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Composite uniqueness lets the foreign keys below prove that every indexed row belongs to the
-- same organization as both its candidate and source document.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'uk_candidates_id_organization'
                     AND conrelid = 'candidates'::regclass) THEN
        ALTER TABLE candidates
            ADD CONSTRAINT uk_candidates_id_organization UNIQUE (id, organization_id);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conname = 'uk_cv_documents_id_organization'
                     AND conrelid = 'cv_documents'::regclass) THEN
        ALTER TABLE cv_documents
            ADD CONSTRAINT uk_cv_documents_id_organization UNIQUE (id, organization_id);
    END IF;
END
$$;

-- IF NOT EXISTS is required by the project's controlled baseline-adoption path, where Flyway
-- may apply the versioned history to a schema that was already created by a previous release.
CREATE TABLE IF NOT EXISTS cv_semantic_chunks (
    -- These four columns are the native Spring AI PgVectorStore contract. Flyway owns
    -- their creation; Spring AI schema initialization remains disabled.
    id uuid DEFAULT uuid_generate_v4() PRIMARY KEY,
    content text NOT NULL,
    metadata json NOT NULL,
    embedding vector(1536) NOT NULL,
    -- Generated relational columns preserve database-level tenant and lifecycle guarantees
    -- while Spring AI keeps the portable retrieval metadata in its JSON document envelope.
    organization_id varchar(100) GENERATED ALWAYS AS (metadata ->> 'organizationId') STORED NOT NULL,
    candidate_id bigint GENERATED ALWAYS AS ((metadata ->> 'candidateId')::bigint) STORED NOT NULL,
    document_id bigint GENERATED ALWAYS AS ((metadata ->> 'documentId')::bigint) STORED NOT NULL,
    page_number integer GENERATED ALWAYS AS ((metadata ->> 'pageNumber')::integer) STORED,
    chunk_index integer GENERATED ALWAYS AS ((metadata ->> 'chunkIndex')::integer) STORED NOT NULL,
    -- Keep the ISO-8601 value as text in the generated column. PostgreSQL does not allow the
    -- timezone-sensitive timestamptz input function in a generated-column expression.
    source_version varchar(40) GENERATED ALWAYS AS (metadata ->> 'sourceVersion') STORED NOT NULL,
    indexed_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cv_semantic_chunks_candidate_tenant
        FOREIGN KEY (candidate_id, organization_id)
        REFERENCES candidates (id, organization_id) ON DELETE CASCADE,
    CONSTRAINT fk_cv_semantic_chunks_document_tenant
        FOREIGN KEY (document_id, organization_id)
        REFERENCES cv_documents (id, organization_id) ON DELETE CASCADE,
    CONSTRAINT uk_cv_semantic_chunks_document_chunk UNIQUE (document_id, chunk_index),
    CONSTRAINT ck_cv_semantic_chunks_page_number CHECK (page_number IS NULL OR page_number > 0),
    CONSTRAINT ck_cv_semantic_chunks_chunk_index CHECK (chunk_index >= 0),
    CONSTRAINT ck_cv_semantic_chunks_content CHECK (length(btrim(content)) > 0)
);

CREATE INDEX IF NOT EXISTS idx_cv_semantic_chunks_tenant_candidate
    ON cv_semantic_chunks (organization_id, candidate_id, document_id);

CREATE INDEX IF NOT EXISTS idx_cv_semantic_chunks_embedding_hnsw
    ON cv_semantic_chunks USING hnsw (embedding vector_cosine_ops);
