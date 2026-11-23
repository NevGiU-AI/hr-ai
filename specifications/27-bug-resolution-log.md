# Bug resolution log

This living document records important defects discovered during local, staging, or production validation and explains
how they were diagnosed and corrected. It complements code comments, pull-request descriptions, and focused incident
documents by preserving the cross-component reasoning that would otherwise be lost after a fix is merged.

Each entry should record observable symptoms, evidence, root cause, user/security impact, correction, regression
coverage, rollout caveats, and environment acceptance. Do not include credentials, cookies, session identifiers,
candidate content, personal data, complete HAR files, or other secrets.

## BUG-001 — Transient deployment failure appeared as a logout

**Discovered:** 23 November 2026  
**Affected surface:** Angular authentication revalidation during staging deployment  
**Correction:** PR #226, commit `6c06829`  
**Status:** corrected and accepted in staging

### Symptom

An administrator who had signed in seven minutes earlier was redirected to the login page while the staging deployment
and post-deployment browser BDD workflow were running. Security events showed successful login and logout activity only
for the two dedicated BDD identities. They showed no logout, concurrent-session-limit enforcement, or revocation for
the administrator who was disconnected.

The absence of a logout event was important: the browser had presented an apparent logout, but the application had not
called the logout endpoint for that administrator.

### Evidence and diagnosis

A privacy-minimized HAR review inspected request timestamps, methods, paths, status codes, and loaded JavaScript bundle
names without exposing request headers, cookies, bodies, or user data. The trace showed:

```text
18:47:08  GET /api/auth/me  502
18:47:13  GET /api/auth/me  502
18:47:18  GET /api/auth/me  502
18:47:18  GET /api/auth/me  502
18:47:20  GET /api/auth/me  502
```

The browser had loaded `main-DXJF3NIS.js` immediately before deployment. After deployment, staging served
`main-RH33N3PR.js`. This proved the affected tab was still executing the previous Angular bundle while the backend was
briefly unavailable.

Several initial explanations were rejected:

- normal session expiry was impossible because only seven minutes had passed and staging uses a thirty-minute timeout;
- concurrent-session enforcement was not recorded for the affected identity;
- the BDD logout hook logged out only its dedicated recruiter and administrator sessions;
- GitHub's Playwright browser cannot send a `BroadcastChannel` or `localStorage` logout event to a browser on another
  machine; and
- backend logout invalidates only the requesting Spring Session, not every session with the same role or organization.

### Root cause

Angular route guards call `AuthService.ensureAuthenticated()` before protected navigation. That method requests a CSRF
token when needed and then calls `GET /api/auth/me`. Its former error handler treated every failure as proof that the
session was invalid:

```text
network error, 5xx, or 401
-> clear cached authentication
-> return unauthenticated
-> route guard redirects to login
```

During deployment, the backend replacement briefly produced HTTP 502. The old frontend therefore converted temporary
infrastructure unavailability into a misleading logout even though no logout operation or session-limit action had
occurred.

The five-second session heartbeat was not the source of the redirect. It already ignored temporary `/auth/me` failures.
The defect was specific to guarded navigation through `ensureAuthenticated()`.

### Correction

`AuthService.ensureAuthenticated()` now distinguishes authentication rejection from temporary unavailability:

- HTTP 401 confirms that the session is invalid, so cached authentication is cleared and the guard redirects to login;
- a network, gateway, or other non-401 failure preserves an already confirmed in-memory identity and allows the current
  SPA navigation to continue; and
- when no identity has been confirmed, such as on a cold browser start, a transient failure is propagated rather than
  inventing an authenticated user.

Backend authorization remains authoritative. Preserving a cached SPA identity during a temporary failure does not make
protected data available: every API request still requires a valid server-side Spring Session and role checks.

### Regression coverage

Angular tests now verify that:

- an authenticated user remains cached when `/api/auth/me` returns HTTP 503;
- HTTP 401 clears the cached user and returns an unauthenticated result; and
- HTTP 502 with no cached identity propagates the failure instead of fabricating authentication.

The focused authentication suite passed five tests, the complete Angular suite passed 147 tests, and the production
frontend build completed successfully. Existing unrelated Angular template, Sass deprecation, and bundle-budget
warnings remained unchanged.

### Rollout caveat

An Angular application already open in a browser continues executing the JavaScript bundle it originally loaded. A
deployment cannot replace code inside that running tab. Consequently, the deployment that first installs this fix can
still disconnect a tab running the old bundle if that tab navigates during the backend restart.

After deployment, a hard refresh loads the corrected bundle. The meaningful acceptance test is therefore the *next*
deployment:

1. hard-refresh staging and confirm the browser loaded the current main bundle;
2. sign in and keep a protected page open;
3. perform another staging deployment;
4. navigate between already available protected routes while `/api/auth/me` temporarily returns a network or 5xx
   response;
5. confirm the application does not redirect to login;
6. confirm a genuine HTTP 401 still clears the session and redirects; and
7. confirm no unexpected logout or session-limit audit event is recorded for the human test identity.

### Staging acceptance

The next staging deployment completed the meaningful rollout test on 23 November 2026. The browser was already running
the corrected `main-RH33N3PR.js` bundle and remained on the protected CV ingestion and evaluation page throughout the
backend replacement. A privacy-minimized HAR review recorded this sequence:

```text
19:24:31  GET /api/auth/me  200
19:24:32  GET /api/auth/me  502
19:24:36  GET /api/auth/me  502
19:24:37  GET /api/auth/me  502
19:24:41  GET /api/auth/me  502
19:24:46  GET /api/auth/me  502
19:24:51  GET /api/auth/me  200
19:24:56  GET /api/auth/me  200
```

Candidate and job requests also returned HTTP 502 during the same short outage. Angular displayed its temporary service
unavailability message while preserving the signed-in header and protected page. When the backend recovered,
`/api/auth/me` returned repeated HTTP 200 responses without a new login. No cookies, headers, session identifiers,
request bodies, account addresses, or candidate data were copied from the HAR into this record.

This acceptance confirms the intended separation: transient infrastructure failure may temporarily prevent business
data from loading, but it no longer destroys the browser's confirmed authentication state or masquerades as logout.

### Prevention rule

Client authentication code must distinguish **invalid authentication** from **authentication service unavailability**.
Only an authoritative rejection such as HTTP 401 may be interpreted as logout. Network and 5xx failures require retry
or temporary-unavailability behavior and must not silently destroy locally cached session state.

