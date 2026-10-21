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

## Current multi-tenant database model

The application currently uses a **shared database, shared schema, tenant-discriminator** model. Organizations share the
same PostgreSQL tables, while each tenant-owned row carries an `organization_id`. The authenticated user's stored
organization becomes part of every tenant-sensitive lookup; a numeric record ID alone is never sufficient. For example,
a candidate is loaded by both `candidateId` and `organizationId`, and a missing or cross-tenant identifier returns the
same `404` response. This avoids revealing whether another organization owns that identifier.

Isolation is enforced in the Spring service/repository layer today. PostgreSQL row-level security is not enabled, so a
missed organization predicate would be a security defect. Repository tests and authorization tests therefore remain
mandatory, and PostgreSQL row-level security should be evaluated as defense in depth before untrusted customer tenants
are onboarded.

## Current organization creation limitation

There is no supported organization-management API or UI. `POST /api/admin/users` always copies the authenticated
administrator's `organization_id`; its request body deliberately cannot select another organization. A tenant
administrator can therefore create users only inside their own organization and cannot establish another tenant.

`AdminAccountInitializer` can technically create an initial administrator with a custom identifier supplied through
`BOOTSTRAP_ADMIN_ORGANIZATION`. That startup mechanism is intended for controlled bootstrap, requires a globally unique
administrator email, and must not become the normal customer-onboarding workflow. After a successful bootstrap login,
the password must be removed from deployment configuration as documented in
[Authentication and Authorization](./15-authentication-and-authorization.md#bootstrap-administrator).

## Planned organization lifecycle management

Before independent companies are onboarded, add a managed `organizations` table and a platform-operator boundary that
is separate from tenant `ADMIN`. A protected organization-creation operation should atomically create the organization
and its first administrator, then write a platform audit record. At minimum, the future model needs:

- an immutable internal identifier, validated unique slug, display name, status, and lifecycle timestamps;
- platform-authorized creation, activation, suspension, and retirement;
- first-administrator provisioning or invitation without exposing credentials in logs;
- a reviewed Flyway migration that replaces legacy `staging` and `production` labels across users, jobs, candidates,
  CV documents, evaluations, and security events as one coordinated change;
- tests proving tenant administrators cannot list, create, mutate, or infer other organizations; and
- an explicit authentication design before allowing one email address to participate in multiple organizations.

Normal candidate, job, CV, evaluation, chat, and AI-tool requests must continue deriving the organization from the
authenticated principal. They must never accept an organization identifier from user or model-controlled payloads.

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
