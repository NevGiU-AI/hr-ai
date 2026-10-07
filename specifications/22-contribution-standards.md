# Contribution standards

Status: **Approved project convention**

These rules keep branches, commits, reviews, and source code understandable as the project grows. They apply to human
and AI-assisted contributions.

## Branch names

Branch names follow the [Conventional Branch 1.1.0 specification](https://conventionalbranch.org/) using:

```text
<type>/<short-description>
```

Use these project branch types:

| Type | Purpose | Example |
| --- | --- | --- |
| `feature/` | New product or technical capability | `feature/recruitment-dashboard` |
| `fix/` | Non-urgent defect correction | `fix/cv-transaction-isolation` |
| `hotfix/` | Urgent production correction | `hotfix/login-lockout` |
| `release/` | Release preparation | `release/v1.4.0` |
| `chore/` | Documentation, maintenance, or dependency work | `chore/update-runbooks` |

Rules:

- Use lowercase letters and numbers, with hyphens between words.
- Keep the description short but specific; include an issue number when one exists.
- Do not use spaces, underscores, uppercase letters, consecutive separators, or leading/trailing separators.
- `main` remains unprefixed and protected.
- Do not use agent-identity prefixes such as `codex/`, `ai/`, or vendor names for this project. Name a branch for the
  purpose of the work, regardless of who or what assisted with it.
- Create a fresh branch from the latest remote `main` unless explicitly continuing an existing PR.
- Keep one cohesive concern per branch and remove merged branches when they are no longer needed.

The current malware-quarantine PR retains its existing branch name to avoid replacing an active reviewed PR. All new
branches created after this decision must follow the project convention above.

## Commit messages

Commits follow [Conventional Commits 1.0.0](https://www.conventionalcommits.org/en/v1.0.0/):

```text
<type>[optional scope]: <imperative description>

[optional body]

[optional footer]
```

Preferred types are:

- `feat`: add user-visible or technical capability;
- `fix`: correct defective behavior;
- `docs`: change documentation only;
- `test`: add or correct tests only;
- `refactor`: restructure code without changing intended behavior;
- `perf`: improve performance;
- `build`: change dependencies or build tooling;
- `ci`: change continuous-integration or deployment automation;
- `chore`: perform maintenance not covered above.

Use a short, stable scope when it adds meaning, such as `cv`, `security`, `dashboard`, `deploy`, or `docs`.

Good examples:

```text
feat(dashboard): add recruitment summary cards
fix(cv): isolate archive entry transactions
docs(deploy): document ClamAV health validation
test(security): cover cross-tenant administration denial
```

Commit rules:

- Write the description in the imperative mood, lowercase after the colon, without a trailing period.
- Describe the outcome, not the act of editing files. Avoid messages such as `update files`, `changes`, or `fix stuff`.
- Keep each commit cohesive and independently reviewable; do not mix unrelated documentation, formatting, and behavior.
- Use a body when the reason, risk, migration, or non-obvious trade-off matters.
- Mark incompatible changes with `!` and explain them in a `BREAKING CHANGE:` footer.
- Reference an issue or incident in a footer when applicable.
- Never put credentials, personal data, candidate content, or other secrets in commit messages.

## Code comments and documentation

Comments explain information the code cannot express clearly by itself. They must remain accurate as the code changes.

Add a comment or Javadoc when it explains:

- a business rule, policy, or intentionally rejected alternative;
- a security, privacy, tenant-isolation, or authorization boundary;
- transaction propagation, rollback compensation, idempotency, or interaction with external storage;
- a protocol, third-party integration constraint, unusual algorithm, or important performance trade-off;
- a public API contract whose inputs, outputs, errors, or side effects are not obvious;
- why a surprising-looking implementation is necessary.

Do not add comments that merely translate a statement into English, repeat method or variable names, preserve dead code,
or compensate for unclear naming. Prefer extracting a well-named method or type first. Comments must not contain secrets,
candidate personal data, or misleading promises about behavior that is not implemented.

Use `TODO` only for concrete, intentionally deferred work. Include an issue reference or enough context to make ownership
and the completion condition clear. Remove obsolete comments in the same change that makes them obsolete.

Tests should communicate behavior through descriptive names and assertions. Comment test setup only when the scenario or
fixture has a non-obvious constraint.

## Review and completion

Before requesting review:

1. Rebase or merge the latest remote `main` as agreed for the PR and resolve conflicts deliberately.
2. Review the diff for accidental files, secrets, generated output, and unrelated changes.
3. Run the tests and static checks appropriate to the risk of the change.
4. Update affected specifications, operational runbooks, examples, and environment templates.
5. State behavior changes, migrations, configuration, validation evidence, and remaining risks in the PR.

After the recruitment dashboard is delivered, perform a focused source-documentation review. Add missing comments to the
existing code only where they explain business rules or non-obvious technical boundaries under the rules above; do not
perform a mechanical comment-every-method exercise.
