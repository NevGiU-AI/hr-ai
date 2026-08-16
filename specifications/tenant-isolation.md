# Organization and tenant isolation

Every authenticated user belongs to one organization. The backend obtains the organization identifier from the
authenticated `AppUserPrincipal`; API request bodies are never trusted to select a tenant.

Tenant-owned records are:

- users;
- jobs;
- candidates;
- CV documents; and
- candidate evaluations.

Candidate and job reads use organization-qualified repository methods. New candidates, approved jobs, imported CV
documents, and evaluations receive the authenticated user's organization before persistence. Evaluation requests load
both the candidate and job through organization-qualified lookups, so an identifier owned by another tenant is returned
as not found. CV duplicate detection is also tenant-local: two organizations may import the same file, while a repeated
file within one organization remains a duplicate.

User email addresses remain globally unique because email is currently the sole login identifier. Supporting the same
email address in multiple organizations would require adding an organization selector, subdomain, or invitation context
to authentication.

## Existing database migration

The application currently uses Hibernate schema updates rather than a versioned migration tool. Before deploying this
change to a database containing jobs, candidates, documents, or evaluations, take a backup and run a controlled
maintenance migration. Replace `existing-organization` below with the organization assigned to the existing data:

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

Application-level tenant scoping is mandatory in this milestone. PostgreSQL row-level security and versioned Flyway or
Liquibase migrations are recommended defense-in-depth improvements before allowing untrusted organizations to onboard.

## Validated environment rollout

- Staging was backed up, migrated to organization `staging`, deployed, and functionally verified before production.
- Production was backed up with a custom-format PostgreSQL dump and migrated to organization `production` on
  16 August 2026.
- Production verification confirmed one existing candidate, job, CV document, and evaluation under `production`,
  `NOT NULL` ownership columns, and `UNIQUE (organization_id, sha256)` CV deduplication.
- Release `v0.2.0`, commit `33d8a04`, deployed the tenant-aware application successfully and passed authentication,
  tenant-data, and public smoke validation.

The current migration procedure is a controlled operational workaround while Hibernate schema update remains enabled.
All future schema evolution should move to reviewed Flyway or Liquibase migrations before onboarding untrusted tenants.
