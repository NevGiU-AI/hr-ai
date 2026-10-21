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
- Fetch `origin` and create every fresh branch directly from the latest `origin/main` unless explicitly continuing an
  existing PR.
- Before adding or pushing new work to an existing branch, fetch `origin` and rebase that branch onto the latest
  `origin/main`.
- Do not merge `main` into a feature, fix, documentation, or other contribution branch. Keep contribution history linear.
- Keep one cohesive concern per branch and remove merged branches when they are no longer needed.

Historical branches created before this decision may retain their original names. All new branches must follow the
project convention above.

## Required branch synchronization

Before creating a branch, update the remote references and branch from the remote mainline rather than a potentially
stale local `main`:

```bash
git status --short
git fetch origin
git switch -c <type>/<short-description> origin/main
```

Before updating an existing branch or pushing additional commits to its pull request:

```bash
git status --short
git fetch origin
git rebase origin/main
```

Start only with a clean working tree, or deliberately preserve unfinished work before rebasing. Resolve every conflict
by reviewing both sides and the current specification, then run `git rebase --continue`. Use `git rebase --abort` when a
safe resolution is unclear; do not guess, discard unrelated changes, or create a merge commit to bypass the conflict.

Rebasing rewrites commit identifiers. If the branch already exists remotely, update it with:

```bash
git push --force-with-lease
```

Never use plain `--force`. `--force-with-lease` refuses to overwrite remote work that was added after the last fetch.
Coordinate before rebasing a branch another contributor is actively using. A branch that has already been merged should
normally be deleted; create a fresh purpose-named branch from `origin/main` for subsequent work instead of reviving it.

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

1. Fetch `origin`, rebase the branch onto the latest `origin/main`, resolve conflicts deliberately, and use
   `--force-with-lease` if the remote branch history must be updated. Do not merge `main` into the branch.
2. Review the diff for accidental files, secrets, generated output, and unrelated changes.
3. Run the tests and static checks appropriate to the risk of the change.
4. Update affected specifications, operational runbooks, examples, and environment templates.
5. State behavior changes, migrations, configuration, validation evidence, and remaining risks in the PR.

After the recruitment dashboard is delivered and before OpenAPI contracts are published, perform a focused,
codebase-wide source-documentation review. Add missing comments and Javadoc only where they explain public contracts,
business rules, or non-obvious technical boundaries under the rules above; do not perform a mechanical
comment-every-method exercise. The review must be completed and verified as its own roadmap milestone before Springdoc,
Swagger UI, or versioned OpenAPI publication begins.
