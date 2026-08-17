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

Creation normalizes email, requires a 12–128 character initial password, hashes it with bcrypt, and returns `409` for a
globally unavailable email. Responses include account identity, organization, enabled state, and roles; they never
include password hashes or credentials. The Angular `/admin/users` UI lists the current organization's accounts and
creates enabled accounts with initial roles. Its route and navigation entry are administrator-only. Administrators can
also update another account's roles, enable or disable it, and revoke all its active sessions. Self-management is
rejected, tenant-scoped lookup prevents cross-organization mutation, and pessimistic locking prevents concurrent
changes from removing the organization's last enabled administrator.

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

- Tenant-scoped administrator APIs and UI now cover account listing, creation, role changes, enabling/disabling, and
  session revocation while excluding password hashes and preventing removal of the last enabled administrator.
- Add login throttling, temporary lockout, and security-event audit records.
- Add password change and administrator-assisted reset; email self-service reset remains deferred until mail delivery is approved.
- Add concurrent-session limits and replace the in-process registry with a shared Spring Session store before
  horizontal backend scaling.
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

## Future external channels

Telegram or WhatsApp account linking will associate a provider-verified identity with an existing HR AI user through a short-lived, single-use code. Messaging adapters may use short-lived, audience- and scope-restricted OAuth/JWT access tokens for server-to-server API calls. Browser authentication remains session-based unless a later SSO migration is approved.

## Acceptance criteria

- Anonymous business API requests return `401`.
- Authenticated users can access authorized workflows through a server-side session.
- Non-administrators receive `403` for built-in CV import.
- Unsafe requests without a valid CSRF token receive `403`.
- Login rotates the session identifier and logout invalidates it.
- Password hashes, session identifiers, CSRF tokens, and credentials never appear in logs or API responses.
- Full production approval remains blocked until tenant row isolation, account administration, login throttling, and security auditing are complete.
