# Cross-tab session state incident

## Summary

During staging validation, the application was opened in two browser tabs. Signing out in the first tab invalidated the
server-side session and redirected that tab to login. The second tab continued to display the cached user, protected
navigation, and the **Sign out** button. Requests from the second tab correctly returned HTTP 401 with
`Authentication required`, proving that server authorization remained effective, but the visible client state was stale.

## Root cause

Each Angular tab had an independent in-memory `BehaviorSubject` containing the authenticated user. Logout cleared only
the subject in the tab that initiated the request. There was no cross-tab notification mechanism.

The HTTP interceptor added cookies and CSRF headers but did not react to HTTP 401 responses. In addition,
`ensureAuthenticated()` returned immediately whenever a cached user existed, so guarded navigation could trust stale
memory without revalidating the server session.

## Security impact

This was not an authorization bypass. The server-side session was invalid and protected APIs rejected the second tab.
No candidate, job, CV, or evaluation data could be retrieved after logout. The defect was a misleading authentication UI
and allowed protected controls to remain visible until their requests failed.

## Correction

Authentication state now lives in a dedicated `AuthSessionState` service:

- successful logout broadcasts a content-free `{ type: "logout" }` event through `BroadcastChannel` and writes a
  unique logout event ID to `localStorage` as a cross-browser fallback;
- receiving tabs clear their cached user and CSRF token and redirect to login;
- while a user is cached as signed in, each tab independently revalidates `/api/auth/me` every five seconds, so a
  suppressed browser event cannot leave an idle tab stale indefinitely;
- the HTTP interceptor expires local state and redirects whenever a protected request returns HTTP 401;
- login HTTP 401 responses remain with the login form so it can show the generic credential error; and
- guarded navigation rechecks `/api/auth/me` instead of trusting a cached user indefinitely.

Neither cross-tab mechanism contains the user, session cookie, CSRF token, credentials, candidate information, or
other sensitive data. The `localStorage` value is only a unique event ID. The secure `HttpOnly` session cookie remains
controlled by the browser and server.

## Regression coverage

Frontend tests verify that:

- local expiration clears user and CSRF state;
- a logout event received from another tab clears state and redirects;
- the `localStorage` fallback immediately clears state and redirects;
- the session heartbeat calls `/api/auth/me` without navigation or user interaction;
- protected-request HTTP 401 responses expire the session; and
- login failures do not trigger the protected-request redirect behavior.

## Staging verification

1. Sign in and duplicate the application tab.
2. Sign out in tab 1.
3. Confirm tab 1 redirects to login.
4. Confirm tab 2 immediately redirects to login without clicking or refreshing.
5. Sign in again, invalidate or expire the session, and call a protected API.
6. Confirm the first HTTP 401 clears the header, removes protected controls, and redirects to login.
7. Confirm an incorrect password still shows the generic login error without a redirect loop.
8. Confirm backend logs show no access to protected data after logout.
