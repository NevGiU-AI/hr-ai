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

The default command excludes scenarios tagged `@provider`, `@destructive`, `@editable-scope`, or
`@operational-follow-ups`, or `@tenant-isolation`. To reproduce the protected staging run,
including the single controlled citation request, use:

```powershell
mvn -Pbdd "-Dcucumber.filter.tags=not @destructive and not @organization-discovery and not @named-discovery and not @editable-scope and not @operational-follow-ups and not @localized-outcomes and not @tenant-isolation" verify
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

## Opt-in editable scope scenario

Use the usual staging BDD configuration and an organization with at least one approved test job. No extra
environment variables are needed. The test starts with one candidate and no job, creates history using a
deterministic OCR-status question, and then adds an approved job. It checks that the original conversation ID,
candidate, and new job survive reload, while the old timeline stays cleared. Cleanup deletes only the conversation
created by the scenario. The protected smoke gate does not require this scenario.

```powershell
mvn -B --no-transfer-progress -Pbdd "-Dcucumber.filter.tags=@editable-scope and not @destructive" verify
```

## Opt-in operational follow-ups scenario

Use a disposable staging candidate with a current CV extracted by NATIVE or OCR and at least one persisted
evaluation (current or stale). Point the existing `BDD_CANDIDATE_QUERY` and `BDD_CANDIDATE_NAME` at that
candidate; no new environment variables are required. The scenario asks about extraction, ingestion warnings,
and stored evaluation scores in order. It accepts an empty warning list, but requires evaluation scores and
state to be shown as stored decision-support information. All three replies must stay in the same confirmed
conversation without CV citations. The questions use deterministic operational routing, with no paid provider
calls. Cleanup deletes only the scenario's disposable conversation, not the candidate or evaluation.

```powershell
mvn -B --no-transfer-progress -Pbdd "-Dcucumber.filter.tags=@operational-follow-ups and not @destructive" verify
```

## Opt-in localized controlled replies

Use the usual staging BDD configuration with a disposable candidate whose CV does not document an exact p99
latency measurement for a production REST API. No additional environment variables are needed. Each example
creates its own conversation and asks one question about the missing measurement and one about a sensitive
attribute. It verifies the English outcome badge, localized reply text and `lang` attribute, and no CV citations
for English, French, and Portuguese. This scenario uses live provider calls and is excluded from the protected smoke gate.

```powershell
mvn -B --no-transfer-progress -Pbdd "-Dcucumber.filter.tags=@localized-outcomes and not @destructive" verify
```

## Opt-in tenant isolation scenario

Use the usual staging BDD configuration and a processed test CV. Set `BDD_OTHER_TENANT_EMAIL` and
`BDD_OTHER_TENANT_PASSWORD` locally for a dedicated staging recruiter in a different organization. The test
verifies the organizations from the authenticated sessions, then checks that the owner can read the disposable
conversation and its history while the second account receives an opaque 404 for both. It makes no paid provider
calls and is not part of the protected smoke gate. Cleanup logs out both identities and deletes only the
scenario-owned conversation.

```powershell
mvn -B --no-transfer-progress -Pbdd "-Dcucumber.filter.tags=@tenant-isolation and not @destructive" verify
```

### Running with two local test organizations

If staging has only one organization, use a fresh local deployment with its own empty PostgreSQL and Redis
storage and unused host ports. A different Compose project name alone does not isolate the root Compose
file's PostgreSQL and CV bind mounts. Use a separate checkout or override those mounts to dedicated test
storage. Do not reuse or relabel existing environment records.

1. Start the backend with a unique bootstrap administrator and `BOOTSTRAP_ADMIN_ORGANIZATION=tenant-bdd-a`
   (or the corresponding `APP_SECURITY_BOOTSTRAP_ADMIN_*` backend environment variables).
2. Sign in as that administrator. In Users, create a dedicated recruiter and finish any required password
   change. Import a synthetic CV under the first organization and verify its extraction completed.
3. Recreate only the test backend with a different bootstrap administrator email and
   `BOOTSTRAP_ADMIN_ORGANIZATION=tenant-bdd-b`, keeping the same isolated database. Sign in as the second
   administrator and create a second dedicated recruiter. Remove bootstrap credentials from the test
   backend configuration after provisioning.
4. Set the usual BDD URLs to the local frontend and API. Set the regular recruiter and candidate variables
   for the first organization and `BDD_OTHER_TENANT_*` for the second. Run the tagged command above.
   The test verifies the organization IDs from the authenticated sessions before checking access.

Two accounts created by the same administrator belong to the same organization and will fail the fixture
check. Never weaken that check to make a single-organization run pass.
