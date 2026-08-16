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

- Add administrator APIs/UI for account creation, role changes, disabling, and session revocation.
- Add login throttling, temporary lockout, and security-event audit records.
- Add password change and administrator-assisted reset; email self-service reset remains deferred until mail delivery is approved.
- Add concurrent-session policy and a shared Spring Session store before horizontal backend scaling.
- Add malware scanning, retention/deletion enforcement, and broader business-action audit logging.

## Validated rollout

- Staging organization backfill, tenant-aware uniqueness, authentication, and tenant-scoped workflows were validated
  before production promotion.
- Production was backed up and migrated to organization `production` before release `v0.2.0` (`33d8a04`) on
  16 August 2026.
- Production login and tenant-scoped application features were accepted after deployment. The one-time bootstrap
  password was removed and the backend recreated; repeat login against the persisted account remains the explicit final
  operator check for secret-removal closure.

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
