# Final Authentication and Security Validation

## Purpose

Close the authentication foundation with one repeatable acceptance suite. Run the complete mutation-heavy suite in
staging with disposable accounts, then run the explicitly safe production subset after promoting the same immutable
images. Record release identifiers and outcomes without recording passwords, cookies, CSRF tokens, session IDs, raw IP
addresses, or Redis keys.

## Preconditions

- CI is green for the exact commit under test.
- PostgreSQL, Redis, backend, frontend, and Caddy are healthy.
- Flyway reports the expected schema version with no pending migration.
- `FLYWAY_BASELINE_ON_MIGRATE=false`.
- `SECURITY_MAXIMUM_SESSIONS=3`, environment-specific Redis namespaces, and the reviewed lockout values are effective.
- Staging has a disposable administrator, recruiter, reviewer, and read-only account. Use a second approved test
  organization only for tenant-boundary tests.
- Production tests use dedicated disposable accounts and do not mutate real business users.

## Automated gate

Run from the repository before deployment:

```bash
cd backend
mvn -B -ntp clean verify
```

CI must also run the Docker-backed Flyway tests. The automated suite must cover anonymous rejection, CSRF enforcement,
role checks, tenant-qualified repositories, cross-tenant administration rejection, password operations, lockout state,
session eviction/revocation, audit persistence, duplicate-account conflicts, and last-administrator protection.

## Staging acceptance matrix

### Authorization and CSRF

1. Confirm an anonymous business API request returns `401` JSON.
2. Confirm unsafe requests without the issued CSRF header return `403`.
3. Confirm `READ_ONLY` can read but cannot create jobs, import CVs, or create evaluations.
4. Confirm `REVIEWER` can read and evaluate but cannot create jobs or import CVs.
5. Confirm `RECRUITER` can create jobs, upload CVs, and evaluate, but cannot load built-in CVs or access `/api/admin/**`.
6. Confirm `ADMIN` can perform authorized business and administration operations.
7. Call an administrator endpoint directly as a non-administrator; confirm `403` and `ADMIN_ACTION_DENIED`.

### Tenant isolation

With two approved staging organizations, confirm each account sees only its own users, jobs, candidates, documents,
evaluations, and security events. Requests that use an identifier owned by the other organization must return `404`.
Attempt role, status, session, unlock, and password-reset operations with a foreign user ID; none may change data or
sessions. Do not create a second organization by relabelling production-like records.

### Login throttling and expiry

Run the documented account and isolated-IP limit procedures from the staging runbook. Confirm generic errors, `429`,
`Retry-After`, administrator account unlock, automatic Redis expiry, and restored normal configuration. Unknown-account
attempts must not disclose whether an email exists.

### Password and account lifecycle

Using disposable accounts, confirm authenticated password change, incorrect-current-password rejection,
administrator-assisted reset, role changes, disabling/re-enabling, and explicit session revocation. Every password or
authorization-changing action must revoke the intended sessions. The last enabled administrator and administrator
self-management safeguards must return `409` without changing the account.

### Session policy

Use four isolated browser profiles with one account. With a limit of three, the fourth login must succeed, expire the
oldest-created session, and leave the other three active. Confirm heartbeat-driven redirect, backend-restart persistence,
explicit revocation, disablement revocation, idle timeout, and a `SESSION_LIMIT_ENFORCED` event without session IDs.

### Concurrent requests

Submit two simultaneous account-creation requests with the same normalized email. Exactly one may succeed; the other
must return the generic `409` conflict. With two disposable administrators, submit simultaneous operations that would
otherwise remove the last enabled administrator. Database locking must preserve at least one enabled administrator.
Repeat simultaneous fourth/fifth login attempts and confirm Redis converges to no more than the configured number of
indexed sessions.

### Audit verification

Confirm the tenant-scoped history contains the expected successful, failed, denied, lockout, password, account,
revocation, and session-limit events with correct actor/target identities. Confirm secrets, raw IP addresses, session
identifiers, and unknown-account emails are absent. Query PostgreSQL to verify persistence and organization boundaries;
do not print identifier hashes into validation evidence.

## Production acceptance subset

After staging acceptance and promotion of the same immutable images:

1. Confirm public health, login, logout, refresh persistence, and protected-route behavior.
2. Repeat role denial, CSRF rejection, password change/reset, three-session enforcement, explicit revocation, and
   disable/re-enable using production disposable accounts only.
3. Confirm security events and backend logs contain the expected outcomes and no persistence errors.
4. Confirm normal jobs, candidates, CV documents, and evaluations remain visible to the production organization.
5. Do not intentionally trigger broad IP lockout, cross-tenant mutations, or concurrent last-administrator changes in
   production; accept those from automated tests and staging evidence.

## Exit criteria

The authentication foundation is complete only when automated tests, the full staging matrix, and the production subset
pass for recorded immutable revisions. Any failed control keeps the milestone open. Document the incident and correction,
repeat the affected test, and retain evidence according to the security and personal-data policy.

## Validation record

**Accepted 19 September 2026:** the complete staging matrix and the safe production subset passed against the
production-promoted `v0.8.0` application images. Validation covered role and CSRF enforcement, tenant boundaries,
login throttling and expiry, password and account lifecycle operations, Redis-backed session limits and revocation,
concurrent-request safeguards, audit persistence, protected-route behavior, and continued visibility of existing
business data. Production-only destructive scenarios remained covered by automated and staging evidence as required.
