# Browser BDD suite

This separate Maven project exercises a deployed HR AI frontend and backend as a black box. It does not start or import
the backend application and it does not call an AI provider in the default suite.

## Required disposable fixtures

Use a dedicated test organization with one recruiter, one administrator in the same organization, and one disposable
candidate that both accounts may access. The candidate must not contain real personal information because a failed
browser scenario can attach a screenshot to the local Cucumber report.

Set these variables without printing their values:

```text
BDD_BASE_URL
BDD_API_URL
BDD_RECRUITER_EMAIL
BDD_RECRUITER_PASSWORD
BDD_ADMIN_EMAIL
BDD_ADMIN_PASSWORD
BDD_CANDIDATE_QUERY
BDD_CANDIDATE_NAME
BDD_CANDIDATE_QUESTION
```

`BDD_CANDIDATE_QUERY` is the name fragment entered in search. `BDD_CANDIDATE_NAME` is the exact expected card name,
which prevents the test from silently selecting the wrong result. `BDD_CANDIDATE_QUESTION` must be a stable,
job-relevant question answered explicitly by that disposable CV so the citation scenario can require grounded evidence.
`BDD_HEADLESS=false` is optional for local debugging; headless execution is the default.

## Install Chromium

From `e2e/`, run:

```powershell
mvn test-compile exec:java "-Dexec.classpathScope=test" "-Dexec.mainClass=com.microsoft.playwright.CLI" "-Dexec.args=install chromium"
```

## Run

```powershell
mvn -Pbdd verify
```

The default command excludes scenarios tagged `@provider` or `@destructive`. To reproduce the protected staging run,
including the single controlled citation request, use:

```powershell
mvn -Pbdd "-Dcucumber.filter.tags=not @destructive" verify
```

The suite creates conversations through the recruiter UI and covers owner isolation, explicit resume after leaving the
active chat, reload restoration, grounded citation navigation, and permanent deletion. Cleanup deletes any disposable
conversation left by a scenario. It then attempts an explicit UI logout for each authenticated test identity before
closing its isolated browser context. Logout is best effort so an unavailable cleanup endpoint cannot hide the real
scenario result; context closure still prevents browser state from crossing scenario boundaries. Explicit logout
normally removes the server-side Spring Sessions immediately and prevents dedicated BDD accounts from accumulating
sessions or producing misleading concurrent-session audit events. The citation scenario makes one provider-backed
request; the configured question and fixture must remain synthetic and stable. It is excluded from the default local
run but included by protected staging CI. Reports are written under `target/` and are ignored by Git.

## Staging CI

`.github/workflows/bdd-staging.yml` runs this deterministic smoke suite after a successful `Deploy staging` workflow
and supports a manual rerun from `main`. It deliberately does not run untrusted pull-request code with staging
credentials.

Configure the protected GitHub `staging` environment with:

- variables `BDD_BASE_URL` and `BDD_API_URL`;
- secrets `BDD_RECRUITER_EMAIL`, `BDD_RECRUITER_PASSWORD`, `BDD_ADMIN_EMAIL`, and `BDD_ADMIN_PASSWORD`;
- secrets `BDD_CANDIDATE_QUERY`, `BDD_CANDIDATE_NAME`, and `BDD_CANDIDATE_QUESTION` for the approved disposable fixture.

Restrict that environment to `main` and authorized reviewers. On failure, the workflow stores the Cucumber report and
Failsafe output as a private artifact for one day. Those artifacts can contain the disposable candidate's visible card
and must not be copied to a public issue or chat.

The post-deployment `workflow_run` token has read-only cache access. The job intentionally does not enable Maven caching
instead of granting cache-write capability to a workflow that receives protected staging secrets.

## Opt-in organization discovery scenario

Use the usual staging BDD configuration, plus:

- `BDD_ORGANIZATION_DISCOVERY_QUESTION`: a professional search that returns at least three staging candidate cards.
- `BDD_ORGANIZATION_COMPARISON_QUESTION`: a question that both of the first two cards' CVs can answer with citations.

Use disposable staging data. The scenario confirms the first two cards and leaves the third unselected. It checks
that there is no conversation before confirmation and that the comparison cites exactly the two selected candidates.
Only the conversation created for the test is cleaned up. This scenario calls the live provider and is not yet part of
the protected staging smoke gate.

From `e2e`, run:

```powershell
mvn -B --no-transfer-progress -Pbdd "-Dcucumber.filter.tags=@organization-discovery and not @destructive" verify
```

## Opt-in named discovery scenario

Set `BDD_NAMED_DISCOVERY_QUESTION` in addition to the usual staging BDD configuration. Use a request such as
“Compare Alex and Morgan on Java experience” with disposable fixtures: the first reference must match at least two
cards, and the first card in each group must identify a different candidate with a distinct display name.

The scenario checks grouped results, no automatic selection, and the exact displayed scope after reload. It creates
and cleans up only its own conversation. The protected smoke gate does not require this scenario.

```powershell
mvn -B --no-transfer-progress -Pbdd "-Dcucumber.filter.tags=@named-discovery and not @destructive" verify
```
