# Organization and tenant isolation

Every authenticated user belongs to one organization. The backend obtains the organization identifier from the
authenticated `AppUserPrincipal`; API request bodies are never trusted to select a tenant.

## Organization versus tenant

An **organization** is the business entity that owns accounts and recruitment data, such as `NevGiU AI` or a customer
company. A **tenant** is the security boundary that isolates that organization's data. The concepts are different, but
the current application maps them one-to-one: the string `organization_id` identifies the organization and is also the
value used to enforce tenant isolation.

The initial data backfill used `staging` in the staging database and `production` in the production database. Those
values were convenient environment-specific migration labels; they do not mean that an environment is a company and
are not the intended multi-company model. Because the environments use separate databases, both may eventually use the
same stable company identifier, such as `nevgiu-ai`, without mixing their data.

Before onboarding independent companies, replace the legacy labels through a reviewed Flyway migration and introduce a
managed `organizations` table with a stable ID, slug, display name, status, and lifecycle metadata. The migration must
update users, jobs, candidates, CV documents, evaluations, and security events together. Until then, administrators
create users only inside their own stored `organization_id`, and there is no organization-creation UI.

Tenant-owned records are:

- users;
- jobs;
- candidates;
- CV documents;
- candidate evaluations; and
- security audit events.

Candidate and job reads use organization-qualified repository methods. New candidates, approved jobs, imported CV
documents, and evaluations receive the authenticated user's organization before persistence. Evaluation requests load
both the candidate and job through organization-qualified lookups, so an identifier owned by another tenant is returned
as not found. CV duplicate detection is also tenant-local: two organizations may import the same file, while a repeated
file within one organization remains a duplicate.

User email addresses remain globally unique because email is currently the sole login identifier. Supporting the same
email address in multiple organizations would require adding an organization selector, subdomain, or invitation context
to authentication.

## Historical tenant migration

Tenant isolation was introduced before Flyway adoption. The following controlled maintenance migration was applied to
the backed-up staging and production databases, replacing `existing-organization` with the assigned organization:

```sql
BEGIN;

ALTER TABLE candidates ADD COLUMN IF NOT EXISTS organization_id varchar(100);
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS organization_id varchar(100);
ALTER TABLE cv_documents ADD COLUMN IF NOT EXISTS organization_id varchar(100);
ALTER TABLE candidate_evaluations ADD COLUMN IF NOT EXISTS organization_id varchar(100);

UPDATE candidates SET organization_id = 'existing-organization' WHERE organization_id IS NULL;
UPDATE jobs SET organization_id = 'existing-organization' WHERE organization_id IS NULL;
UPDATE cv_documents SET organization_id = 'existing-organization' WHERE organization_id IS NULL;
UPDATE candidate_evaluations SET organization_id = 'existing-organization' WHERE organization_id IS NULL;

ALTER TABLE candidates ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE jobs ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE cv_documents ALTER COLUMN organization_id SET NOT NULL;
ALTER TABLE candidate_evaluations ALTER COLUMN organization_id SET NOT NULL;

DO $$
DECLARE existing_constraint text;
BEGIN
  FOR existing_constraint IN
    SELECT constraint_record.conname
    FROM pg_constraint constraint_record
    JOIN pg_class table_record ON table_record.oid = constraint_record.conrelid
    JOIN pg_namespace schema_record ON schema_record.oid = table_record.relnamespace
    WHERE schema_record.nspname = current_schema()
      AND table_record.relname = 'cv_documents'
      AND constraint_record.contype = 'u'
      AND pg_get_constraintdef(constraint_record.oid) = 'UNIQUE (sha256)'
  LOOP
    EXECUTE format('ALTER TABLE cv_documents DROP CONSTRAINT %I', existing_constraint);
  END LOOP;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_cv_documents_organization_sha256
  ON cv_documents (organization_id, sha256);

COMMIT;
```

Verify the actual name of any pre-existing single-column `sha256` unique constraint with `\d cv_documents` and drop it
before creating the composite index. Perform the staging migration first, verify that existing records appear for the
expected administrator organization, and only then repeat it in production.

Application-level tenant scoping is mandatory. PostgreSQL row-level security remains a recommended defense-in-depth
improvement before allowing untrusted organizations to onboard. Flyway now owns all subsequent schema evolution.

## Validated environment rollout

- Staging was backed up, migrated to organization `staging`, deployed, and functionally verified before production.
- Production was backed up with a custom-format PostgreSQL dump and migrated to organization `production` on
  16 August 2026.
- Production verification confirmed one existing candidate, job, CV document, and evaluation under `production`,
  `NOT NULL` ownership columns, and `UNIQUE (organization_id, sha256)` CV deduplication.
- Release `v0.2.0`, commit `33d8a04`, deployed the tenant-aware application successfully and passed authentication,
  tenant-data, and public smoke validation.

The one-time SQL above is retained as incident history, not as a procedure to rerun. Flyway is now the reviewed schema
evolution mechanism and Hibernate validates the migrated schema without changing it.
