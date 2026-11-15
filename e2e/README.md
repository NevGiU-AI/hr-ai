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
```

`BDD_CANDIDATE_QUERY` is the name fragment entered in search. `BDD_CANDIDATE_NAME` is the exact expected card name,
which prevents the test from silently selecting the wrong result. `BDD_HEADLESS=false` is optional for local debugging;
headless execution is the default.

## Install Chromium

From `e2e/`, run:

```powershell
mvn test-compile exec:java "-Dexec.classpathScope=test" "-Dexec.mainClass=com.microsoft.playwright.CLI" "-Dexec.args=install chromium"
```

## Run

```powershell
mvn -Pbdd verify
```

The initial scenario creates a conversation through the recruiter UI, verifies through a separate administrator
browser context that the non-owner receives an opaque `404`, and then deletes the disposable conversation through the
recruiter UI. Reports are written under `target/` and are ignored by Git.
