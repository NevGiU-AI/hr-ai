# Tenant-scoped LOB read incident

## Summary

After organization isolation was deployed to staging, authenticated requests to `GET /api/candidates` and
`GET /api/jobs` returned HTTP 500. Existing data had been backed up, migrated, and correctly assigned to the `staging`
organization. Database checks confirmed 36 candidates, 4 jobs, 36 CV documents, and 5 evaluations were intact.

The backend error was:

```text
org.springframework.orm.jpa.JpaSystemException: Unable to access lob stream
org.postgresql.util.PSQLException: Large Objects may not be used in auto-commit mode.
```

## Root cause

Several entity properties use JPA `@Lob`, including candidate CV text, job description sections, CV extraction data,
and evaluation explanations. With PostgreSQL, Hibernate reads these values through the Large Object API, which requires
an active database transaction.

Before tenant isolation, list endpoints called inherited Spring Data methods such as `findAll()`. Spring Data's base
repository implementation already supplies transaction metadata for those inherited CRUD methods.

Tenant isolation replaced those calls with newly declared derived queries such as:

```java
findAllByOrganizationId(String organizationId)
```

Declared query methods do not automatically inherit the base implementation's transaction metadata. They therefore
ran in auto-commit mode. The organization predicate and migrated data were correct, but Hibernate failed while hydrating
the LOB fields, causing the API to return HTTP 500.

## Correction

Every tenant-scoped repository query that can hydrate an entity containing LOB-backed text now explicitly declares a
read-only transaction:

```java
@Transactional(readOnly = true)
List<Candidate> findAllByOrganizationId(String organizationId);
```

The same boundary is applied to tenant-qualified candidate and job lookups, CV document hash lookup, and evaluation
listing. `readOnly = true` keeps the intent explicit and allows Spring/Hibernate to optimize read handling while still
providing the transaction PostgreSQL requires.

A regression test reflects over all affected repository methods and fails if the transaction declaration is removed.
Existing persistence and tenant-isolation tests remain in place.

## Data and migration impact

- No records were lost or assigned to the wrong organization.
- The organization backfill and composite CV uniqueness index remain correct.
- The pre-migration staging backup remains valid and should be retained through production rollout.
- No corrective database update or restore is required for this incident.

## Staging verification

After deploying the correction:

1. Confirm the deployment workflow and smoke test succeed.
2. Sign in with the existing staging administrator.
3. Confirm the candidates endpoint returns the 36 migrated candidates without HTTP 500.
4. Confirm the jobs endpoint returns the 4 migrated jobs without HTTP 500.
5. Refresh the application and confirm the authenticated session remains active.
6. Import a CV, repeat the import, and confirm duplicate detection remains tenant-local.
7. Run an evaluation using a visible candidate and job.
8. Review recent backend logs and confirm no `Unable to access lob stream` or PostgreSQL large-object auto-commit errors.

## Prevention

When replacing inherited Spring Data CRUD operations with declared or derived repository queries, review transaction
semantics as part of the change. This is especially important for PostgreSQL entities containing `@Lob` fields. Future
integration testing should include PostgreSQL, because H2 does not reproduce PostgreSQL Large Object transaction rules.
