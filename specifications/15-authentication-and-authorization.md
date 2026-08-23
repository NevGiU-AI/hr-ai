# Authentication and Authorization

## Decision

The first secured web release uses administrator-created email/password accounts, Spring Security server-side sessions, and CSRF protection. JWT access tokens are deferred until an external messaging or service-to-service integration needs them.

This choice keeps immediate logout, account disablement, and role changes under server control while the product has one Angular browser client and one Spring Boot backend.

## Implemented foundation

- `AppUser` persists a normalized email, bcrypt password hash, enabled state, organization identifier, and roles.
- Supported roles are `ADMIN`, `RECRUITER`, `REVIEWER`, and `READ_ONLY`.
- A bootstrap administrator can be created idempotently on the first secured startup.
- All `/api/**` business endpoints require an authenticated session.
- `POST /api/candidates/import/initial` additionally requires `ADMIN`.
- Candidate and job mutations require `ADMIN` or `RECRUITER`; evaluation creation also permits `REVIEWER`; `READ_ONLY` is limited to authenticated reads.
- Actuator health and info plus the CSRF bootstrap endpoint remain public.
- State-changing requests require the server-issued CSRF token.
- Angular sends credentials on every API request, applies the CSRF header to unsafe methods, protects business routes, and provides login/logout UI.
- The login password is hidden by default and has an accessible show/hide control for verification before submission.
- Logout state is synchronized across tabs, with session heartbeats plus focus and visibility revalidation handling
  browsers that throttle background tabs.
- Production cookies are `HttpOnly`, `Secure`, and `SameSite=Lax`; the default idle timeout is 30 minutes.

## Authentication API

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/auth/csrf` | Establish a session and return the CSRF token/header name. |
| `POST` | `/api/auth/login` | Authenticate email/password and rotate the session identifier. |
| `GET` | `/api/auth/me` | Return the authenticated user, organization, and roles. |
| `POST` | `/api/auth/logout` | Invalidate the session and security context. |

Login failures always return the generic message `Invalid email or password`; the API does not reveal whether an account exists or is disabled.

## Account administration API

Account administration exposes tenant-scoped lifecycle operations. Every endpoint requires `ADMIN`.
The organization is always copied from the authenticated administrator; request bodies cannot select it.

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/api/admin/users` | List accounts in the administrator's organization, ordered by email. |
| `POST` | `/api/admin/users` | Create an enabled account in that organization with one or more approved roles. |
| `PUT` | `/api/admin/users/{id}/roles` | Replace another account's roles and revoke its active sessions. |
| `PUT` | `/api/admin/users/{id}/status` | Enable or disable another account; disabling revokes active sessions. |
| `POST` | `/api/admin/users/{id}/sessions/revoke` | Sign another account out of every active session. |
| `POST` | `/api/admin/users/{id}/lockout/unlock` | Clear another account's temporary failed-login lock. |

Creation normalizes email, requires a 12–128 character initial password, hashes it with bcrypt, and returns `409` for a
globally unavailable email. Responses include account identity, organization, enabled state, and roles; they never
include password hashes or credentials. The Angular `/admin/users` UI lists the current organization's accounts and
creates enabled accounts with initial roles. Its route and navigation entry are administrator-only. Administrators can
also update another account's roles, enable or disable it, and revoke all its active sessions. Self-management is
rejected, tenant-scoped lookup prevents cross-organization mutation, and pessimistic locking prevents concurrent
changes from removing the organization's last enabled administrator.

Browser sessions are stored through Spring Session's indexed Redis repository under an environment-specific namespace.
The Spring Security principal email supplies the session index used by administrator revocation. Redis persistence keeps
sessions across backend restarts, and a shared Redis instance makes revocation effective across every backend replica.
Redis is reachable only on the private data network and requires an environment-specific password. Introducing this
store invalidates the servlet sessions created by earlier releases, so users must sign in once after deployment.

## Login throttling and temporary lockout

Failed logins are counted atomically in Redis by normalized-email hash and client-IP hash. Raw email addresses and IP
addresses are not included in Redis key names. By default, five account failures or twenty failures from one IP within
fifteen minutes create a fifteen-minute lock. A successful authentication clears that account's pending failure count;
it does not clear the shared IP counter. Account and IP locks expire automatically in Redis and work consistently across
backend restarts and replicas.

Locked requests return `429` with `Retry-After`; ordinary failures return `401`. Both use the same `Invalid email or
password` message used for unknown, disabled, and incorrect-password accounts. Authentication is not attempted while a
matching lock is active. The backend accepts forwarded client addresses because it is reachable through the trusted
Caddy edge network rather than directly from the public internet.

Administrators see temporary account lock state in the tenant-scoped account list and may clear another account's lock.
Unlock does not clear an IP-wide lock and cannot operate across organizations or on the administrator's own row.

## Security-event auditing

Authentication and account-administration outcomes are persisted in PostgreSQL. Known-account login events inherit the
account's organization; account-management events inherit the authenticated administrator's organization. The API never
accepts an organization identifier from the request. Unknown login emails and client IP addresses are stored only as
SHA-256 hashes, and unknown-account events are not exposed in any tenant history. Credentials, passwords, session IDs,
CSRF tokens, and raw client IP addresses are never recorded.

The audit covers successful and failed login, throttled login, account lockout, logout, account creation, role changes,
enable/disable, session revocation, administrator unlock, and denied administration operations. Audit writes use an
independent transaction and fail safely, so an unavailable audit store is logged without changing the authentication or
administration response. A scheduled cleanup removes records older than `APP_SECURITY_AUDIT_RETENTION` (365 days by
default). Every backend replica may run the idempotent cleanup.

`GET /api/admin/security-events?page=0&size=50` requires `ADMIN`, caps page size at 100, and returns only the caller's
organization. The Angular **Security events** page provides the same tenant-scoped, paginated history. Identifier hashes
remain internal and are not returned by the API.

The Angular administrator route redirects authenticated non-administrators to the job-offer page before an API request
is made. Backend authorization is validated separately by calling `GET /api/admin/security-events` directly: it returns
`403` and records `ADMIN_ACTION_DENIED`. Successful account-administration records persist both the actor's user ID and
email snapshot; the UI uses `User #<id>` only as a fallback for older rows whose email was not captured.

Staging validation must cover both independent limits. The account test uses one disposable recruiter: failures one
through four return `401`, failure five returns `429` with `Retry-After`, correct credentials remain blocked during the
lock, and administrator unlock restores access. The IP test uses a temporary Redis namespace, an isolated browser or
network, three distinct nonexistent email addresses, a three-attempt IP limit, and a one-minute lock. This proves the
IP limit without triggering the account limit or contaminating normal staging counters. The original `.env` settings
must be restored immediately afterward. The complete operator procedure is in
[the staging runbook](./09-staging-vps-provisioning-runbook.md#14a-validate-login-throttling-and-temporary-lockout).

## Bootstrap administrator

Before the first secured deployment, configure:

```dotenv
BOOTSTRAP_ADMIN_EMAIL=admin@example.com
BOOTSTRAP_ADMIN_PASSWORD=<random password of at least 12 characters>
BOOTSTRAP_ADMIN_ORGANIZATION=<environment organization, such as local, staging, or production>
```

The password is bcrypt-hashed with work factor 12 before persistence. After confirming login, remove `BOOTSTRAP_ADMIN_PASSWORD` from the deployment environment and recreate the backend container. Existing accounts remain in PostgreSQL and the initializer never replaces an existing account or password.

## Security boundaries

The organization identifier is resolved from the authenticated principal and must never be accepted from request bodies or model-controlled tool arguments. `Job`, `Candidate`, `CvDocument`, and `CandidateEvaluation` records carry organization ownership, and their business queries are scoped to the authenticated organization. Cross-organization candidate and job identifiers resolve as not found, and CV duplicate detection is isolated per organization.

## Remaining work

- Add authenticated password change and administrator-assisted reset; email self-service reset remains deferred until mail delivery is approved.
- Audit password change and reset outcomes as part of that implementation.
- Add maximum concurrent-session limits using the indexed Spring Session repository.
- Complete authorization, tenant-isolation, throttling-expiry, audit-log, password-operation, concurrent-request, and
  staging smoke tests before closing the authentication foundation.
- Add malware scanning, retention/deletion enforcement, and broader business-action audit logging.

## Validated rollout

- Staging organization backfill, tenant-aware uniqueness, authentication, and tenant-scoped workflows were validated
  before production promotion.
- Production was backed up and migrated to organization `production` before release `v0.2.0` (`33d8a04`) on
  16 August 2026.
- Production login and tenant-scoped application features were accepted after deployment. The one-time bootstrap
  password was removed and the backend recreated; repeat login against the persisted account remains the explicit final
  operator check for secret-removal closure.
- Staging account lifecycle administration from PR `#25` (`4984b82`) was accepted on 17 August 2026. Validation used
  separate administrator and recruiter browser sessions and confirmed role updates, explicit session revocation,
  automatic logout after disabling, rejected login while disabled, and successful login after re-enabling. The
  administrator's own row remained non-editable, preserving the self-management safeguard.
- Indexed Spring Session Redis was deployed and reported operational in staging and production on 17 August 2026.
  Redis authentication and application operation were accepted. Backend-restart persistence and distributed
  administrator revocation remain explicit smoke tests for every environment and future multi-replica deployment.
- Redis-backed account/IP login throttling, temporary lockout, and administrator unlock were deployed to staging and
  production in release `v0.4.0` on 17 August 2026. Deployment is complete; the documented account-limit and isolated
  IP-limit procedures remain the repeatable release-validation evidence for policy behavior.
- Tenant-scoped security-event auditing from PR `#32`, forbidden-admin-request auditing from PR `#33`, and actor-email
  snapshots from PR `#34` were deployed and accepted in staging and production on 23 August 2026. Validation confirmed
  authentication and administration events, direct backend `403` responses with `ADMIN_ACTION_DENIED`, administrator
  visibility within the same organization, Angular route-guard redirection, and administrator email display for new
  account-management events. `SECURITY_AUDIT_RETENTION=365d` is explicitly configured in both environments.

## Future external channels

Telegram or WhatsApp account linking will associate a provider-verified identity with an existing HR AI user through a short-lived, single-use code. Messaging adapters may use short-lived, audience- and scope-restricted OAuth/JWT access tokens for server-to-server API calls. Browser authentication remains session-based unless a later SSO migration is approved.

## Acceptance criteria

- Anonymous business API requests return `401`.
- Authenticated users can access authorized workflows through a server-side session.
- Non-administrators receive `403` for built-in CV import.
- Unsafe requests without a valid CSRF token receive `403`.
- Login rotates the session identifier and logout invalidates it.
- The configured account failure limit produces a temporary Redis lock, `429`, and `Retry-After`; expiry or an
  administrator unlock permits authentication again.
- Repeated failures across different accounts from one client reach the independent IP limit.
- Unknown, disabled, incorrect-password, and temporarily locked login attempts never disclose account existence.
- Password hashes, session identifiers, CSRF tokens, and credentials never appear in logs or API responses.
- Full security-foundation approval remains blocked until password management, concurrent-session
  policy, and their authorization and tenant-isolation tests are complete.
