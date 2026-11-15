# Browser behavior-driven development testing

Status: **Approved design; implementation pending**

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
```

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
@cv-chat @destructive
Feature: CV-chat conversation deletion

  Scenario: A recruiter deletes a disposable conversation
    Given a recruiter owns a disposable conversation with persisted messages and citations
    When the recruiter deletes that conversation
    Then it is absent from recent conversations
    And its conversation and history endpoints return 404
```

## CI delivery sequence

1. Add the isolated `e2e` Maven project and pinned dependencies.
2. Install the pinned Chromium build in a dedicated CI job.
3. Implement scenario-scoped lifecycle, configuration validation, redaction, and cleanup.
4. Automate persisted conversation resume and citation viewport visibility first.
5. Add owner isolation and explicit deletion.
6. Upload private traces/screenshots only on failure.
7. Run deterministic smoke scenarios on pull requests once their runtime and reliability are measured.
8. Add Firefox/WebKit only for a documented compatibility risk; do not multiply every scenario by every browser by
   default.
9. Add further critical workflows incrementally rather than reproducing every unit test through the browser.

## Boundaries

BDD complements rather than replaces unit, component, integration, migration, security, AI evaluation, performance,
accessibility, usability, and manual acceptance testing. It should cover high-value cross-layer behaviors where a real
browser and deployed services materially change the risk. Provider-backed answer quality remains governed by the
versioned evaluation datasets, while Cucumber/Playwright verifies the surrounding user workflow and safety controls.
