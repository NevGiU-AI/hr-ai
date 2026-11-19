# Browser behavior-driven development testing

Status: **Initial executable suite accepted in staging; expansion in progress**

Behavior-driven development (BDD) expresses expected product behavior as executable, business-readable scenarios.

## Purpose

The project has strong backend unit/integration tests, Flyway/Testcontainers schema checks, Angular component tests,
deterministic AI invariants, opt-in provider evaluations, and manual staging/production acceptance. Those layers do not
fully exercise a recruiter's behavior across the real browser, Angular rendering, HTTP/session security, backend,
database, and asynchronous navigation boundaries.

The PR #184 conversation-history acceptance exposed that gap. Unit tests proved that citation metadata and the target
fragment were correct, but a real browser showed that the one-shot scroll ran before Angular rendered the cited
document. PR #185 corrected the timing defect. A browser-level scenario should detect that class of regression before
staging acceptance.

This document records the selected browser BDD stack, the alternatives considered, component responsibilities,
security rules, execution model, and initial delivery scope.

## Final decision

Use:

```text
Gherkin .feature files
        |
        v
Cucumber-JVM
        |
        v
JUnit Platform
        |
        v
Playwright for Java
        |
        v
Chromium initially; Firefox and WebKit where risk justifies them
```

Cucumber is required from the first BDD slice. It supplies executable business-language scenarios and maps each
`Given`, `When`, and `Then` step to Java glue. Playwright for Java owns browser and HTTP automation. JUnit Platform
discovers and reports the Cucumber scenarios. The tests remain in the Java/Maven ecosystem without using Selenium.

## Alternatives considered

### Selenium WebDriver with JUnit, then Cucumber later

Selenium is mature, widely understood, and integrates naturally with Java, JUnit, Maven, and Cucumber-JVM. It remains
a reasonable choice when an organization already has WebDriver infrastructure or specialists.

It was not selected because this project is establishing a new suite and its most recent browser defect involved
asynchronous rendering. Selenium commonly needs more explicit wait management, driver/browser compatibility
management, and supporting diagnostics. A disciplined Selenium implementation could work, but it would add no project-
specific advantage over Playwright for Java.

### Playwright with TypeScript and optional Cucumber-JS

The Angular team could use Playwright's TypeScript runner and its first-class fixtures. This would be a good choice for
a frontend-owned suite.

It was not selected because the repository's cross-layer security, API, Flyway, and AI testing practices are already
centered on Java, JUnit, and Maven. Using Playwright for Java keeps browser tests close to that ecosystem while retaining
Playwright's isolation, auto-waiting, request inspection, tracing, screenshots, and cross-browser APIs.

### Playwright for Java with JUnit only

Descriptive JUnit test names can follow Given/When/Then semantics with less setup than Cucumber. This option would
provide most technical browser coverage.

It was not selected as the final stack because the project explicitly requires executable Gherkin. Cucumber makes
critical recruitment workflows reviewable as business behavior rather than only test implementation. The feature
files must remain concise and must not duplicate low-level UI mechanics.

## Why Playwright for Java

Playwright's Java API provides the required browser lifecycle and testing surfaces:

- `Playwright.create()` owns the driver process;
- `BrowserType` launches Chromium, Firefox, or WebKit;
- `Browser` is reusable for a suite;
- a fresh `BrowserContext` isolates cookies, local storage, permissions, and sessions per scenario;
- `Page` drives the UI;
- `APIRequestContext` can prepare or verify state without turning every setup action into a slow UI flow;
- locator and page assertions wait for observable browser conditions;
- tracing, screenshots, and video provide failure evidence.

Primary sources:

- [Playwright Java API](https://playwright.dev/java/docs/api/class-playwright)
- [Playwright Java installation](https://playwright.dev/java/docs/intro)
- [Playwright Java test runners](https://playwright.dev/java/docs/test-runners)
- [Playwright locators](https://playwright.dev/java/docs/locators)
- [Playwright auto-waiting](https://playwright.dev/java/docs/actionability)
- [Playwright assertions](https://playwright.dev/java/docs/test-assertions)
- [Playwright authentication](https://playwright.dev/java/docs/auth)
- [Playwright tracing](https://playwright.dev/java/docs/trace-viewer)

## Why Cucumber-JVM

Cucumber turns Gherkin steps into executable Java methods and supports JUnit Platform execution. All Cucumber
dependencies must use one aligned version. Cucumber does not supply an assertion library; the suite will use the
project's established assertion style.

Scenarios must be independent. Cucumber creates fresh glue instances for each scenario, and no static candidate,
conversation, cookie, page, or organization state may leak across scenarios. PicoContainer is the selected lightweight
dependency-injection module because the browser suite targets an externally running application and does not need to
start the Spring application context. Shared step state lives in a scenario-scoped context object.

Primary sources:

- [Cucumber-JVM installation and JUnit Platform](https://cucumber.io/docs/installation/java/)
- [Cucumber step definitions](https://cucumber.io/docs/cucumber/step-definitions/)
- [Cucumber scenario state and dependency injection](https://cucumber.io/docs/cucumber/state/)
- [Cucumber configuration](https://cucumber.io/docs/cucumber/configuration/)
- [Cucumber Java tools](https://cucumber.io/docs/tools/java/)

## Component responsibilities

```text
Feature file
  Business behavior only; no selectors, credentials, database SQL, or timing details
       |
       v
Step definitions
  Translate Given/When/Then into reusable domain actions and assertions
       |
       v
Scenario context (PicoContainer)
  Scenario-only IDs, users, responses, pages, and cleanup registrations
       |
       +---------------------------+
       |                           |
       v                           v
Page objects                  API clients
Stable accessible UI         Setup, verification, and cleanup through
actions and assertions       authenticated application contracts
       |                           |
       +-------------+-------------+
                     v
            Playwright browser contexts
            Independent recruiter/admin sessions
                     |
                     v
          Deployed Angular + Spring application
                     |
                     v
             PostgreSQL / Redis / providers
```

### Feature files

Feature files describe externally observable behavior. They must use recruitment-domain language and avoid CSS
selectors, endpoint implementation details, arbitrary sleeps, secrets, or long form-filling recipes.

### Step definitions

Step definitions bind Gherkin expressions to Java. They coordinate page objects and API clients but do not own browser
lifecycle or store static state. Steps should express one reusable behavior and must not call other step definitions.

### Scenario context

One scenario context stores only the current scenario's disposable candidate IDs, conversation ID, authenticated
contexts, last response, and cleanup actions. PicoContainer injects the same context into the scenario's hooks and step
classes. It is discarded after the scenario.

### Browser lifecycle

One `Playwright` instance and browser may be shared by a test process. Every scenario receives a new browser context.
Security scenarios use two or more contexts, for example an owning recruiter and a same-organization administrator.
Closing the context after every scenario clears cookies and browser storage even when a scenario fails.

### Page objects and locators

Page objects expose domain actions such as `resumeConversation()` or `followCitation()`. Prefer accessible role, label,
and visible-text locators. Use stable application test identifiers only when no accessible locator can identify the
element reliably. Do not encode arbitrary delays in page objects.

### API clients

API setup is allowed when the UI action is not the behavior under test. API clients can create disposable fixtures,
authenticate isolated users, verify opaque authorization responses, and clean up. They must use public application
contracts rather than direct production database writes.

## Planned project layout

Create a separate top-level Maven project so browser dependencies and Failsafe execution do not alter the backend unit-
test lifecycle:

```text
e2e/
  pom.xml
  src/test/java/com/nevgiu/hrai/bdd/
    RunCucumberIT.java
    context/ScenarioContext.java
    hooks/BrowserHooks.java
    pages/LoginPage.java
    pages/CvChatPage.java
    pages/CandidateWorkspacePage.java
    steps/AuthenticationSteps.java
    steps/ConversationSteps.java
    steps/CitationSteps.java
  src/test/resources/
    features/
      cv_chat_conversation_history.feature
      cv_chat_owner_isolation.feature
      cv_chat_citation_navigation.feature
    junit-platform.properties
```

The Maven project will use test-scoped `cucumber-java`, `cucumber-junit-platform-engine`, `cucumber-picocontainer`,
Playwright, JUnit Platform Suite, and the established assertion library. Cucumber artifacts use one version property.
Dependency versions must be reviewed and pinned when the implementation PR begins; do not copy an unreviewed version
from an example indefinitely.

### Why this is a separate Maven project

Neither Cucumber nor Playwright requires a separate Maven project. This is an architectural boundary chosen because
the BDD suite tests the deployed product across both applications rather than testing Spring backend classes:

```text
                Browser BDD
                     |
          +----------+----------+
          |                     |
   Angular frontend       Spring backend
          |                     |
          +----------+----------+
                     |
             Deployed services
```

A browser scenario opens Angular, authenticates over HTTP, observes session/CSRF behavior, drives backend contracts
through the UI or an authenticated API client, and asserts final browser behavior. It should not import backend
services, repositories, or application internals. Its natural boundary is therefore above `frontend` and `backend`,
not inside either project.

Keeping the suite under `e2e/` provides these controls:

- ordinary `backend/mvn test` remains fast and never installs or launches a browser;
- Cucumber, PicoContainer, Playwright, browser binaries, Failsafe configuration, traces, screenshots, and reports do
  not affect the backend dependency graph or packaged application;
- the browser suite has an independent CI job, timeout, cache, retry policy, secrets, target environment, and artifact
  retention policy;
- local, staging, or another approved environment can be selected without changing backend test configuration;
- browser failures are reported separately from unit, repository, migration, and Testcontainers failures;
- disposable fixture creation and cleanup belong to the cross-application suite rather than a backend test class;
- the suite can grow across authentication, CV ingestion, chat, evaluation, accessibility, and dashboard workflows
  without turning the backend module into the owner of frontend behavior.

The separate project remains in this repository. It is not a separate Git repository, deployable service, container,
or production runtime. It produces test reports and optional private failure diagnostics only.

### Alternative considered: backend Maven profile

An acceptable smaller alternative would place feature files and Java glue under the backend test tree and isolate them
with a dedicated profile:

```text
backend/src/test/resources/features/
backend/src/test/java/com/nevgiu/hrai/bdd/
```

```bash
cd backend
mvn -Pbdd verify
```

That layout reduces initial project setup, but it mixes tests of an externally deployed Angular/Spring system with
backend implementation tests. It also requires careful Surefire/Failsafe exclusions to ensure an ordinary backend
build never downloads browsers, requires environment credentials, or waits for a deployed frontend.

The backend-profile alternative would be reasonable for one or two temporary browser checks. It was rejected because
the approved BDD layer is permanent and will cover multiple product areas. The clearer long-term ownership and CI
isolation of `e2e/` outweigh the small cost of maintaining one additional `pom.xml`.

## Execution profiles

The suite is opt-in and separate from fast unit tests:

```bash
cd e2e
mvn -Pbdd verify
```

Required configuration is supplied through environment variables or CI secrets:

```text
BDD_BASE_URL
BDD_API_URL
BDD_RECRUITER_EMAIL
BDD_RECRUITER_PASSWORD
BDD_ADMIN_EMAIL
BDD_ADMIN_PASSWORD
BDD_CANDIDATE_QUERY
BDD_CANDIDATE_NAME
```

The candidate variables identify a disposable, same-tenant fixture. The query is the search fragment entered in the
browser, while the exact name prevents a broad search from silently selecting the wrong candidate. The first executable
scenario creates the conversation through Angular, verifies owner isolation with a second authenticated browser
context, and deletes the conversation through Angular during scenario cleanup. It does not call an AI provider.

No secret may appear in a feature file, source file, command log, screenshot name, trace name, assertion message, or
test report. The default target is local or staging. Production execution requires an explicit protected workflow and
must exclude destructive scenarios.

## Tags and suites

Initial tags:

| Tag | Purpose |
| --- | --- |
| `@smoke` | Small deterministic deployment gate. |
| `@cv-chat` | CV-chat behavior. |
| `@security` | Tenant, owner, role, CSRF, or opaque-response boundaries. |
| `@destructive` | Disposable-data mutation or erasure; staging/local only by default. |
| `@provider` | Calls a paid/nondeterministic AI provider; separate opt-in gate. |
| `@accessibility` | Browser accessibility behavior and accessible locators. |

The ordinary pull-request BDD job runs deterministic `@smoke and not @provider and not @destructive` scenarios. A
protected staging workflow can run the broader suite. Provider evaluation remains separate from browser BDD so a
model outage or nondeterministic result cannot obscure deterministic UI, authorization, or persistence failures.

The initial CI integration runs after a successful staging deployment rather than on pull requests. The scenario
targets the deployed frontend and backend and requires protected staging accounts, so running arbitrary pull-request
code with those credentials would cross a trust boundary. Manual reruns are accepted only from `main`. Pull-request
execution can be added later if the application and disposable fixtures are provisioned ephemerally without staging
secrets.

GitHub grants the post-deployment `workflow_run` a read-only cache token. The initial job therefore leaves Maven
caching disabled. It must not request cache-write access merely for speed while also receiving protected staging
secrets; a separate trusted cache-seeding design can be added later if dependency download time becomes material.

The lifecycle suite extends the owner-isolation smoke test with three user-visible contracts:

- leaving and explicitly resuming a conversation restores the same server-owned candidate scope, and a browser reload
  retains that same conversation pointer;
- one configured, job-relevant question against the synthetic candidate must return a grounded citation whose link
  loads and scrolls to the cited CV document;
- explicit deletion clears the browser pointer and makes its identifier return the same opaque `404` used for
  unavailable conversations. The backend result is authoritative even when the owner already has other recent chats.

Only the citation scenario calls the configured AI provider. The default local runner continues to exclude `@provider`
and `@destructive` scenarios; protected staging CI explicitly includes the controlled provider scenario while still
excluding destructive tests. `BDD_CANDIDATE_QUESTION` must describe a stable fact explicitly present in the disposable
CV. It is protected alongside the other fixture values so a public workflow cannot silently replace the controlled
evaluation input or spend provider capacity through arbitrary questions.

## Production promotion gate

The protected staging BDD workflow is part of release authorization, not merely reporting. Staging deployment publishes
only immutable commit-SHA images and verifies public health. After all required non-destructive browser scenarios pass,
the BDD workflow promotes those already-built images to `bdd-validated-<commit-sha>` aliases. It never rebuilds them.

The production release workflow resolves the release tag to a commit and requires both backend and frontend aliases for
that exact commit before it can access the production environment. Consequently, none of the following is sufficient:

- CI success without deployment to staging;
- staging health checks without browser acceptance;
- a failed or missing BDD run;
- a successful BDD run for an older or newer revision.

The trusted BDD workflow receives `packages: write` solely to publish these GHCR aliases. Pull-request code cannot invoke
that protected path with staging credentials, and the workflow receives no general repository-content write access.
This permission does not enable Maven cache writes; dependency caching remains disabled for the post-deployment job.

Only a BDD run triggered by the successful `Deploy staging` workflow may publish aliases. A manual dispatch can rerun
the browser tests for diagnosis, but it cannot reliably prove that its checked-out revision is the revision currently
serving on staging; it therefore never changes the production gate.

## Staging acceptance

The initial lifecycle suite passed protected staging acceptance on 16 November 2026 after PR #193 at main revision
`90019b7`. The accepted run exercised all four deployed-browser scenarios:

- a same-organization administrator received an opaque `404` for a recruiter-owned conversation;
- the recruiter left, explicitly resumed, and reloaded the same confirmed candidate scope;
- the controlled provider-backed question produced a citation that navigated to the rendered CV document and scrolled
  the target into the viewport;
- the recruiter deleted the disposable conversation, the browser pointer cleared, and the deleted identifier returned
  `404`.

Scenario cleanup also completed without leaving disposable conversations. This acceptance validates PR #193's browser
suite; the exact-revision production-alias gate is the separate follow-up implemented by the current CI/CD change.

The first BDD-gated production promotion passed on 16 November 2026 after PR #194 in release `v0.40.0` at main
revision `845df46`. The production workflow resolved the release to that exact commit, found both backend and frontend
exact-revision `bdd-validated-*` aliases published only after protected staging browser acceptance, deployed the
immutable images, and passed production health and focused functional checks without rollback. This confirms that a
successful exact-revision browser run is now an enforced release prerequisite rather than an informational follow-up.

On 19 November 2026, the suite correctly blocked promotion after the conversational UI made natural-language discovery
primary and moved manual picker-first search into a collapsed `<details>` fallback. All four scenarios failed in their
shared Given step because the page object found the candidate-search input but attempted to fill it while hidden. PR
#204 corrected the automation contract: it enters fresh-chat state, expands manual search explicitly, and uses the
current `Confirm scope and continue` and `Ask NevGiU` labels. Java compilation and the Maven test build passed, then
the complete protected staging BDD suite passed all four scenarios. Release `v0.42.0` at main revision `c0bff09`
subsequently passed the BDD-gated production promotion and focused production checks. No application runtime,
authorization, schema, or environment change was required for the page-object correction.

## Test-data and cleanup rules

- Use a dedicated test organization and accounts; never assume production candidate IDs.
- Prefer unique run-scoped names and identifiers.
- Register cleanup immediately after creating disposable state.
- Cleanup runs in an `After` hook even after failure.
- Scenarios cannot depend on execution order or another scenario's data.
- Do not create cross-tenant personal data solely to test isolation; use approved fixtures.
- Do not persist real CVs, candidate names, recruiter questions, credentials, or provider payloads in artifacts.
- A failed cleanup is a test failure and must be visible in the report.

## Waiting and stability rules

- Never use `Thread.sleep` to wait for application state.
- Rely on Playwright locator assertions and observable UI/network conditions.
- Use bounded explicit polling only for a genuine background process whose completion has no UI signal.
- Assert behavior, not internal Angular implementation.
- For citation navigation, assert that the owning candidate is selected, the document anchor exists, and the target is
  inside the viewport—not merely that the URL fragment changed.
- Keep each scenario focused on one failure reason.

## Diagnostics and privacy

On failure, collect a Playwright trace and a screenshot using an opaque scenario/run identifier. Retain artifacts only
for the shortest useful CI period. Video is disabled initially and enabled only for a demonstrated debugging need.

Traces, screenshots, videos, reports, and console/network logs can contain personal information. Before artifacts are
enabled, the implementation must verify that disposable fixtures are used and that authorization headers, cookies,
CSRF tokens, passwords, CV text, candidate names, recruiter questions, prompts, and API keys are not exposed. Artifacts
must not be public.

## Initial executable features

### Persisted conversation and citation navigation

```gherkin
@cv-chat @smoke
Feature: Persisted CV-chat conversation history

  Scenario: A recruiter resumes a conversation and follows a restored citation
    Given a recruiter owns a conversation with a completed cited turn
    When the recruiter reloads the chat page
    Then the active conversation is restored
    And its user and assistant messages appear in order
    And its historical citation card is present
    When the recruiter follows the historical citation
    Then the owning candidate is loaded
    And the cited document is visible in the viewport
```

### Owner isolation

```gherkin
@cv-chat @security
Feature: CV-chat conversation ownership

  Scenario: An administrator cannot read a recruiter's conversation
    Given a recruiter owns an active CV-chat conversation
    And an administrator belongs to the same organization
    When the administrator requests the recruiter's conversation
    Then the response status is 404
    And no conversation metadata is disclosed
```

### Explicit deletion

```gherkin
@cv-chat @conversation @smoke
Feature: CV-chat conversation deletion

  Scenario: A recruiter permanently deletes a conversation
    Given a recruiter owns an active CV-chat conversation
    When the recruiter deletes that conversation
    Then the deleted conversation is cleared from the active browser state
    And the recruiter can no longer retrieve the deleted conversation
```

This scenario is not tagged `@destructive`: it creates and removes only its own disposable conversation. Any scenario
that erases shared candidates, CVs, jobs, accounts, or retention/legal-hold state must use `@destructive` and remains
excluded from the automatic release gate.

## CI delivery sequence

1. Add the isolated `e2e` Maven project and pinned dependencies.
2. Install the pinned Chromium build and Linux dependencies in a dedicated CI job.
3. Implement scenario-scoped lifecycle, configuration validation, redaction, and cleanup.
4. Automate persisted conversation resume and citation viewport visibility first.
5. Add owner isolation and explicit deletion.
6. Upload private traces/screenshots only on failure.
7. Run required non-destructive scenarios after successful staging deployments, including the one controlled
   provider-backed citation journey; consider pull-request execution only with an ephemeral environment that does not
   expose protected staging credentials.
8. Add Firefox/WebKit only for a documented compatibility risk; do not multiply every scenario by every browser by
   default.
9. Add further critical workflows incrementally rather than reproducing every unit test through the browser.

## Boundaries

BDD complements rather than replaces unit, component, integration, migration, security, AI evaluation, performance,
accessibility, usability, and manual acceptance testing. It should cover high-value cross-layer behaviors where a real
browser and deployed services materially change the risk. Provider-backed answer quality remains governed by the
versioned evaluation datasets, while Cucumber/Playwright verifies the surrounding user workflow and safety controls.
