# Implementation Roadmap

## Phase 0 - Resolve foundations

- [ ] Decide Telegram, WhatsApp, or both.
- [ ] Finalize the composite scoring formula and individual weights.
- [x] Define session-based email/password authentication, initial roles, organization boundary, and audit requirements.
- [x] Implement the authentication foundation with bcrypt, server sessions, CSRF, protected Angular routes, and admin-only built-in CV import.
- [x] Add organization ownership to existing business records and enforce tenant-scoped repository access.
- [x] Back up and migrate staging and production data to explicit organizations, deploy release `v0.2.0`, and validate
  authentication plus tenant-scoped workflows.
- [x] Implement tenant-scoped account administration for listing, creation, role updates, enabling/disabling, and session revocation.
- [x] Move browser sessions and administrator revocation to indexed Spring Session Redis so sessions survive backend
  restarts and revocation works across replicas; validate the Redis deployment in staging and production.
- [x] Add Redis-backed login throttling, IP/account failure limits, temporary lockout, generic errors, and administrator
  unlock; deploy release `v0.4.0` to staging and production.
- [x] Persist tenant-scoped security events for login outcomes, lockout/unlock, logout, account lifecycle changes,
  denied administration attempts, and session revocation; expose an administrator-only history with retention cleanup.
- [x] Deploy and validate security-event auditing in staging and production, including backend `403` auditing and
  administrator email snapshots for account-management actors.
- [x] Add authenticated password change and administrator-assisted reset with session revocation and password audit events;
  defer email self-service reset until a secure transactional-email capability is approved.
- [x] Validate password change, administrator-assisted reset, session revocation, audit persistence, and sign-in with the
  replacement password in staging and production.
- [x] Adopt Flyway with an existing-schema baseline, move the audit constraint correction into versioned migration
  history, and make Hibernate validate rather than modify the schema.
- [x] Baseline the existing staging database with Flyway, disable one-time baselining, and complete authentication,
  tenant-data, password-management, and security-audit smoke tests.
- [x] Rehearse Flyway against a restored production backup, preserve business-data counts, then baseline production and
  validate the baseline-disabled restart for release `v0.7.0`.
- [x] Enforce a three-session account limit through indexed Spring Session Redis, expire the oldest-created session,
  audit enforcement, and validate release `v0.8.0` in staging and production.
- [x] Complete the [final authentication and security validation suite](./19-final-security-validation.md), covering
  authorization, tenant isolation, lockout, audit logs, passwords, concurrent requests, and session policy.
- [x] Document the current shared-schema multi-tenant model, the absence of organization-management API/UI, and the
  controlled bootstrap-only path for an initial administrator.
- [ ] Implement platform-authorized organization lifecycle management with a managed `organizations` table, stable
  company identities, first-administrator provisioning, status transitions, platform auditing, and cross-tenant tests;
  replace legacy environment-derived organization identifiers before onboarding independent organizations.
- [x] Define candidate notice, retention, deletion, legal-hold, and data-residency policies.
- [x] Define the currently supported CV formats and upload limits (PDF, ZIP, 20 MB per file, 100 MB per request).
- [x] Establish the initial typed API error format and environment configuration.
- [x] Implement CI, immutable container publication, automatic staging deployment, and staging validation aliases.
- [x] Provision and harden separate staging and production VPSs with environment-specific keys and credentials.
- [x] Configure production Premium VPS backup and prepare private cross-region Object Storage.
- [ ] Complete production observability, off-server log collection, redaction, and alerting standards.
- [ ] Approve telemetry classification, content exclusions, low-cardinality labels, access, retention, incident export, and alert-routing policy.
- [ ] After the dashboard, stable OpenAPI contracts, and concurrency benchmark, install and validate the implemented
  encrypted paired PostgreSQL/original-file backup in staging and production, including an isolated restoration test.
- [x] Validate production release promotion with GitHub environment protection, approval, and image rollback configured.
- [ ] Confirm all development, staging, production, and backup credentials are isolated, rotated when needed, and absent from Git.

**Exit condition:** Product, security, and scoring decisions are documented and testable.

**Current delivery sequence:** complete typed CV-chat read tools, scoped memory, dedicated chat page, evaluation, and
controlled-message localization -> speech input/playback -> final dashboard -> codebase-wide comments/Javadoc review -> publish stable OpenAPI contracts
-> benchmark bounded concurrency and virtual threads -> validate paired backup/restore in staging -> validate it in
production -> persist evaluation reproducibility metadata -> cross-cutting release validation -> private object storage
-> consider horizontal or multi-host backend scaling. External messaging follows stable web chat, speech controls, and
documented APIs; optional image/video generation remains last and requires separate value, privacy, and compliance
approval.

The source-documentation review is a completion milestone, not a mechanical comment-every-method exercise. It must cover
public contracts and the non-obvious tenant, authorization, privacy, transaction, locking, rollback, scheduling,
external-integration, and Angular state-management decisions described in
[the contribution standards](./22-contribution-standards.md#code-comments-and-documentation). Redundant comments on
self-explanatory code are explicitly out of scope. OpenAPI publication begins only after this review is complete.

## Phase 1 - Stabilize job-offer generation

- [x] Review the backend and frontend job-offer flows against the feature specification.
- [x] Implement generation, edit, regeneration, approval, persistence, and listing behavior.
- [x] Add prompt-level missing-information and inclusive-language guidance.
- [x] Add initial frontend and backend unit/component coverage.
- [x] Map AI provider and malformed-response failures to privacy-safe typed responses, prevent raw `500 OK` browser
  text, render the job-generation error accessibly, and validate the behavior in staging and production through PRs
  #135 and #136 on 4 November 2026.
- [ ] Version request, response, prompt, and persistence schemas.
- [ ] Add direct approval, job details, lifecycle maintenance, timeout/retry policy, and approval-error handling.
- [ ] Add controller integration and full end-to-end coverage.
- [ ] Create a privacy-safe, human-reviewed job-generation evaluation dataset and regression gates for grounding, constraints, inclusivity, and prohibited inventions.
- [ ] Measure latency and draft acceptance/edit rates.

**Exit condition:** A recruiter can generate, review, edit, approve, save, and reopen a job offer reliably.

## Phase 2 - Complete CV ingestion and evaluation

- [x] Implement guarded PDF, ZIP, and built-in CV upload pipelines.
- [x] Extract PDF text and conservatively infer candidate name and email.
- [x] Implement all eight validated metrics and an AI explanation.
- [x] Implement validated per-request weight configuration in the backend API.
- [x] Build candidate/job selection and current evaluation-result UI.
- [x] Store new original CV binaries behind a tenant-scoped storage abstraction with retention metadata.
- [x] Validate the initial original-CV storage slice in staging and production.
- [x] Approve candidate notice/consent, retention, deletion, legal-hold, and data-residency policies.
- [x] Add fail-closed malware quarantine; validate clean, infected, unavailable, and recovery paths in staging, then
  deploy and smoke-test clean and infected paths in production.
- [x] Deploy and validate scheduled original-file expiry and administrator-managed legal-hold enforcement in staging
  and production through release `v0.13.0`.
- [x] Add administrator-confirmed, tenant-scoped full candidate erasure across originals, documents, extracted text,
  candidate records, and evaluations, with legal-hold enforcement and auditing.
- [ ] After the dashboard, stable OpenAPI contracts, and concurrency benchmark, install the automated encrypted paired
  original-file/database backup service in staging and production and complete isolated-restore acceptance.
- [x] Add tenant-authorized original-file read/delete APIs with generic cross-tenant responses, transaction-safe
  filesystem deletion, and security auditing; validate the operations in staging and production.
- [x] Add candidate-details and ingestion-history UI/API access to the existing authorized original-file operations.
- [x] Add auditable, tenant-scoped metadata correction with immutable revision snapshots and safe document
  reprocessing that rescans the original, preserves human edits, and invalidates prior evaluations.
- [x] Validate metadata correction and document reprocessing in staging and production, including no-op submissions,
  role and tenant boundaries, immutable history, evaluation invalidation, and fail-closed malware scanning.
- [x] Add native-text quality detection and bounded local OCR fallback for scanned or low-text PDFs.
- [x] Preserve OCR page references, extraction provenance, and confidence for human review.
- [x] Reprocess the existing candidate/document without creating duplicate records or modifying the submitted original.
- [ ] Add metric-level evidence and separate reliability confidence from candidate fit.
- [ ] Expose approved weight configuration in the frontend.
- [x] Build candidate details and document-ingestion history UI/API.
- [x] Validate candidate details, ingestion history, sensitive-field exclusion, role-aware original-file controls, and
  tenant-safe not-found behavior in staging.
- [x] Validate the candidate details and ingestion-history slice in production, including legal-hold erasure guidance.
- [x] Reorder the Angular candidate workspace around recruiter tasks and validate the final import -> search -> details
  -> ingestion history -> CV chat -> evaluation -> evaluation history -> grounded comparison flow in staging and
  production on 4 November 2026 after PRs #139 and #140.
- [x] Add tenant-scoped evaluation-history retrieval and presentation with candidate/job context and current/stale state;
  staging and production acceptance passed on 23 October 2026 in release `v0.17.0`.
- [x] Validate native extraction, image-only OCR provenance, duplicate-safe reprocessing, stale/current evaluation
  history, tenant boundaries, and logs in staging.
- [x] Validate the accepted OCR and evaluation-history smoke paths in production after a matched backup and protected
  release `v0.17.0`.
- [ ] Validate scoring consistency, bias controls, and adversarial inputs.
- [ ] Add golden CV, OCR, evidence, counterfactual, and ranking-stability evaluation datasets with deterministic and calibrated semantic metrics.

**Exit condition:** Uploaded candidates receive explainable, reproducible, human-reviewable evaluations.

## Phase 3 - Add semantic indexing and multimodal web chat

- [x] Add an imperative, candidate-specific chat backend with tenant-scoped evidence loading, bounded OCR/native
  citations, role and CSRF enforcement, unsupported-citation rejection, and stable provider failures.
- [x] Validate the candidate-specific backend, grounded native/OCR citations, safe insufficient-evidence and
  out-of-scope responses, authorization boundaries, and privacy-safe logs in staging on 27 October 2026.
- [x] Promote the accepted candidate-specific backend to production and repeat the focused smoke and privacy checks
  successfully on 27 October 2026.
- [x] Prefer neutral candidate references in grounded generated answers rather than inferring personal pronouns from
  names or CV evidence. Single-candidate answers use the translated equivalent of “the candidate”; comparisons use
  supplied candidate labels, confirmed display names, or that neutral reference. Focused production acceptance passed
  on 10 November 2026 at main revision `f759b88` without environment, schema, migration, Compose, or frontend changes.
- [x] Add the Flyway-owned Spring AI `PgVectorStore` schema, bounded native/OCR chunking, portable tenant metadata,
  composite tenant constraints, post-commit indexing, reprocessing replacement, deletion cascades, and bounded
  reconciliation backfill.
- [x] Validate V6 and the opt-in indexing lifecycle in staging before enabling semantic retrieval in user-facing chat.
  Immediate indexing, scheduled recovery, zero pending work, tenant-scoped chunks, and privacy-safe logs passed on
  29 October 2026 after correcting relational replacement deletion and the Spring AI pgvector property hierarchy.
- [x] Promote the staging-accepted semantic-indexing foundation to production and repeat the focused lifecycle checks.
  Immediate three-chunk indexing, zero pending reconciliation work, privacy-safe error review, and complete
  candidate/document/chunk cleanup passed on 29 October 2026 at main revision `64a7a00`.
- [x] Integrate the internal tenant-scoped similarity service into authorized single-candidate chat retrieval behind a
  separate dark-launch switch, with verified organization/candidate filters, current-source-version checks, bounded
  similarity policy, merged reviewer-visible citations, transaction suspension during vector I/O, and safe empty/failure
  behavior.
- [x] Validate tenant-scoped single-candidate semantic retrieval in staging. Exact facts, paraphrased experience, and
  education evidence returned citations at the calibrated `0.35` threshold, while absent restaurant and salary facts
  retained the controlled insufficient-evidence response on 30 October 2026.
- [x] Promote the calibrated single-candidate retrieval path to production and repeat the focused configuration,
  grounded-answer, citation, insufficient-evidence, tenant-scope, and privacy-safe log checks successfully on
  30 October 2026 in release `v0.21.0`.
- [x] Add a strict pre-retrieval question-policy boundary that separates prohibited/unrelated questions from permitted
  questions lacking CV evidence, rejects malformed policy output, avoids candidate retrieval for refused questions,
  and retains answer-model refusal as defense in depth. The corrected boundary passed staging acceptance on
  30 October 2026 at main revision `87821a4`, then focused production acceptance in release `v0.22.0` at main revision
  `6d96d2b`.
- [x] Add the first versioned, privacy-safe question-policy evaluation dataset and an opt-in provider-backed regression
  gate covering English, French, Portuguese, sensitive/unrelated intent, professional boundary cases, adversarial
  wording, and the observed terse Java-engineer false refusal. The live `gpt-4o-mini` run passed all 21 cases before
  staging acceptance. Broader retrieval, multilingual consistency, and answer evaluation remain pending.
- [x] Add the first synthetic multilingual retrieval/generated-answer dataset and opt-in Spring AI evaluation harness.
  It checks EN/FR/PT expected-document recall at the accepted threshold, required evidence, tenant/candidate filtering,
  citations, response language, repeated answers, relevancy, and fact checking. Live-provider and environment
  acceptance are separated deliberately: the focused live-provider gate and scoped staging acceptance passed on
  30 October 2026, with staging running main revision `d46f435`; focused production acceptance followed in release
  `v0.23.0` at main revision `083b9c4`. At that revision the known French KLx paraphrase remained a safe recall miss;
  the subsequent dual-query correction below resolved it in staging and production.
- [x] Implement the measured multilingual-recall correction without lowering the global threshold: normalize each
  policy-approved question into one constrained English retrieval query, search both original and normalized wording,
  deduplicate and rank the union, retain mandatory tenant/candidate/current-document/source-version controls, cap final
  evidence at six chunks, fall back to the original question on normalization failure, and emit privacy-safe score/count
  diagnostics. The realistic noisy-chunk fixture and deterministic regressions are included in the same slice.
- [x] Run the opt-in provider-backed multilingual suite for the dual-query path and record privacy-safe
  original-versus-normalized measurements. All seven cases passed on 30 October 2026; direct French KLx improved from
  `0.4561` to `0.5451`, alternate French KLx from `0.4621` to `0.5887`, and cases where original wording scored better
  confirmed the need to merge rather than replace queries.
- [~] Correct short picker-first comparison-query recall without lowering the global threshold. Staging showed that
  `Compare their Java experience` produced `0.5554` with six accepted matches for candidate `70`, but only `0.2871`
  and zero accepted matches for candidate `71`. The normalizer now expands concise professional criteria into neutral
  evidence categories, and multilingual retrieval dataset `1.1.0` adds the synthetic regression. Deterministic tests
  pass, and the fresh eight-case provider gate passed on 12 November 2026. Focused staging acceptance then passed after
  PR #173 at revision `ceded13`; production repeated the focused check in release `v0.37.0` at revision `657207f`.
- [x] Validate the dual-query multilingual-recall correction in staging before production promotion. On 1 November
  2026, merged PR #109 at main revision `26d59e8` preserved grounded EN/FR/PT answers, citations, tenant/candidate/current-
  document isolation, missing-evidence behavior, and pre-retrieval sensitive/unrelated refusals. Privacy-safe logs
  reported `queryCount=2`, 12 raw matches, 4–6 accepted matches, and top similarities from `0.4693` to `0.5535` without
  recording questions or CV content. End-to-end responses took approximately 3–4 seconds, establishing a staging
  baseline for later p50/p95 concurrency measurement. No environment, Flyway, database, Compose, or frontend change
  was required. The same implementation passed focused production acceptance on 1 November 2026 in release `v0.24.0`
  at main revision `7576611`; production repeated the grounded EN/FR/PT, citation, tenant isolation, missing-evidence,
  refusal, and privacy-safe log checks successfully. A production latency percentile was not recorded and remains part
  of the later performance benchmark.
- [~] Implement retrieval, filtering, candidate-detail query tools, and comparison orchestration. Request-scoped candidate search,
  confirmed candidate summaries, optional approved-job requirements, and a bounded evaluation-history read-tool
  foundation are implemented; the first three capabilities are staging- and production-accepted in the current
  orchestration flow. The final grounded comparison reuses the tenant-reauthorized evidence service rather than
  delegating authorization to a tool. User-visible evaluation-aware tool consumption, document-status tools, and
  dashboard aggregate tools remain.
- [x] Add deterministic exact-name candidate resolution before multi-candidate retrieval. The
  `POST /api/chat/candidates/resolve` endpoint takes only a validated name and obtains the organization from the
  authenticated principal, performs case-insensitive exact matching without fuzzy or partial widening, and returns a
  minimal projection of ID,
  name, and current title. One match is `RESOLVED`; duplicate names are `AMBIGUOUS` and require explicit selection;
  missing and cross-tenant names share the generic `404` contract. Results are capped at 20 with a `truncated` marker,
  and email, CV text, and organization identifiers are never loaded into the resolution response.
- [x] Add bounded tenant-scoped partial-name discovery through `POST /api/chat/candidates/search`. It requires at least
  two characters, treats wildcard characters literally, ranks exact names first, returns stable minimal choices, and
  never automatically resolves a partial match. Empty and cross-tenant searches share an empty-list response. Exact
  resolution and partial discovery passed focused staging role, isolation, ambiguity, boundary, and selection checks
  on 2 November 2026 for revision `ddb6eaa`. Production repeated the focused contract, role, isolation, minimal-data,
  and selected-candidate chat checks successfully after PR #114 at main revision `2831d2d`.
- [x] Consume partial discovery through an Angular candidate picker with explicit selection, loading/error/empty and
  truncation states, and reuse of the selected ID for the existing details, evaluation, and single-candidate chat flow.
  The intentionally manual button/Enter flow passed focused staging acceptance on 2 November 2026 after PR #116 at
  main revision `cd075b3`, then passed the equivalent production checks. Autocomplete remains optional.
  Multi-candidate comparison is a separate backend/UI slice.
- [x] Add the explicit multi-candidate comparison backend contract for two to five distinct IDs. Verify the entire set
  in the authenticated organization before policy/evidence work, require bounded evidence and citations for every
  selected candidate, and reject duplicate, missing, cross-tenant, unsupported-citation, and uncited outcomes safely.
  Focused staging acceptance completed on 2 November 2026 after PR #120 corrected the provider prompt to supply the
  same bracketed citation syntax enforced by application validation. Grounded two-candidate comparison, citations for
  every selected candidate, validation boundaries, sensitive-question refusal, role enforcement, tenant isolation,
  and privacy-safe logging all passed without a schema or environment change. Production repeated the focused checks
  successfully on 2 November 2026 after PR #121 at main revision `ff6b283`; the backend contract is accepted in both
  environments.
- [x] Extend the Angular picker to maintain an explicit two-to-five-candidate selection, show removable active-scope
  chips/cards, expose change/clear actions, and render the cited comparison. Build this state as a reusable component
  for both picker-first and future language-first conversation flows. PR #124 implemented the reusable scope,
  comparison request, candidate-labelled citations, citation navigation, stale-response protection, retry behavior,
  and role-aware UI. Focused staging acceptance completed on 2 November 2026. PR #125 then converted incomplete
  every-candidate citation coverage into the controlled insufficient-evidence response rather than a provider-facing
  `502`; the full staging comparison checks passed without a schema or environment change. Production repeated the
  focused scope, grounded-comparison, citation-navigation, controlled-response, incomplete-citation, and authorization
  checks successfully on 2 November 2026 in release `v0.28.0`. The picker-first comparison flow is accepted in both
  environments.
- [x] Add the language-first flow: identify candidate references in a natural-language message, resolve them only
  through tenant-scoped deterministic services, display bounded candidate cards, and require recruiter confirmation
  before any CV, evaluation, or job-fit evidence retrieval. Both entry paths must converge on the same verified-ID
  comparison contract. PR #128 implemented explicit candidate-card confirmation, PR #129 made partial multi-token
  name matching independent of stored token order and contained provider control tokens, and PR #130 reused one
  criteria-focused retrieval-query set across every confirmed candidate. Repeated comparisons passed focused staging
  and production acceptance on 3 November 2026 in release `v0.29.0`, with accepted evidence and citations for every
  selected candidate and no citation-coverage, normalization, provider, or raw-control-token failure.
- [~] Expose authorized candidate, job, evaluation-evidence, document-status, and dashboard services as request-scoped
  Spring AI query tools. Candidate discovery, confirmed candidate summaries, optional approved-job requirements, the
  reviewed orchestration API, and Angular activation are implemented through PR #144 and the PR #146–#154 stabilization
  series. Discovery is deterministic and one-pass: the same extracted references and tenant-authorized search results
  decide the workflow and populate confirmation cards, while the former candidate-scope endpoint remains temporarily
  for cached clients. Candidate-only and approved-job flows, ambiguity, refusal, required tool reads, citations, and
  stale language-scope clearing passed focused staging acceptance on 10 November 2026 at revision `22d56c5`.
  Production repeated those focused checks successfully the same day in release `v0.32.0` at revision `1a5f247`.
  The foundational `get_evaluation_evidence` read tool now returns at most the five most recent persisted evaluations
  for one tenant-authorized, explicitly confirmed candidate. It minimizes the result to job context, persisted scores,
  and current/stale timestamps. Generated rationale is intentionally omitted because it can contain candidate personal
  information or quoted CV content, and metric-level source evidence is not yet persisted. Its dedicated callback
  subset is deliberately excluded from the current comparison preflight because that preflight discards tool output.
  A later evaluation-aware conversational path must opt in and consume it explicitly. Focused staging acceptance
  completed on 10 November 2026 at main revision `5455b32`: backend health, tenant-scoped persisted evaluation
  history, current/stale state, missing/cross-tenant protection, and existing chat regressions passed. The acceptance
  covers the deployed foundation and supporting contract, not user-visible evaluation-aware answers. Production
  repeated the focused deployment-health, tenant-isolation, persisted-history, current/stale-state, and existing-chat
  regression checks successfully on 10 November 2026; no environment, schema, migration, Compose, or frontend change
  was required. The foundational `get_document_status` and `get_ingestion_warnings` tools expose the newest
  confirmed candidate document's minimized processing state and application-owned warning codes through a dedicated
  callback subset. They repeat tenant authorization and omit raw errors, filenames, CV text, tenant/storage/hash data,
  and original-file metadata. PR #214 connected them to the persisted conversational path through a dedicated
  operational router, exact per-candidate tool-call postconditions, and deterministic safe rendering that bypasses
  semantic CV retrieval and returns no CV citations. PR #215 corrected the presentation to use each authorized display
  name together with its stable `CANDIDATE-<id>` reference. The opt-in provider evaluation, focused staging checks,
  protected BDD gate, and focused production checks passed on 22 November 2026 in release `v0.44.0` at revision
  `2256171`; no schema, environment, Compose, or frontend contract change was required. User-visible evaluation
  evidence, dashboard tools, and complete evaluation reproducibility/evidence persistence remain pending.
- [x] Inject authenticated user and organization context outside model-controlled tool arguments for the implemented
  read-tool/orchestration slice; repeat this invariant for every future tool.
- [~] Enforce tool-call count, recursion, time, payload, result, concurrency, and provider limits. The current read-tool
  slice has request-local call budgets, timeouts, bounded DTOs, a closed callback allow list, and exact confirmed-ID/job
  postconditions; broader concurrency/provider budgets and future tools remain.
- [~] Add prompt-injection tests proving CV text and chat input cannot expand tool access or cross organization
  boundaries. Direct workflow-override, role/organization injection, tool-result injection, scope-widening, and
  confirmation-bypass regressions exist; extend the suite for future evidence/document tools and indirect CV content.
- [ ] Add preview-only command tools, then require explicit confirmation, idempotency, and audit records before any state change or external communication.
- [x] Upgrade the maintained platform baseline to Spring Boot `3.5.16` and Spring AI `1.1.8`. Release `v0.34.0` at
  main revision `0ea2642` passed staging and production acceptance on 11 November 2026 without an environment,
  Flyway, database, Compose, or frontend change.
- [x] Migrate separately to stable Spring Boot `4.1.2` and Spring AI `2.0.2`. Release `v0.35.0` at main revision
  `f690fb4` passed deterministic, authorization, tenant-isolation, Flyway, pgvector, container, opt-in provider,
  staging, and production gates on 11 November 2026. Both environments explicitly pin the reviewed
  `gpt-4o-mini` runtime model instead of inheriting Spring AI 2's changing provider default.
- [~] Persist and consume scoped conversation memory. Flyway V7, owner/tenant-authorized scope CRUD, bounded Spring AI JDBC
  memory, optimistic scope replacement, and expiry cleanup are implemented. Focused staging acceptance passed on
  11 November 2026 at main revision `ec4e500`; production repeated Flyway V7, CRUD, owner/tenant isolation,
  stale-version conflict, database-state, deletion-cleanup, and existing-chat regression checks successfully in
  release `v0.36.0` at revision `10a4db1`. The follow-up endpoint now reauthorizes stored scope before reading bounded
  history, clears history on every scope replacement, runs provider I/O outside a transaction, and persists a
  completed turn only after a locked version check. A staging false refusal showed that policy and retrieval also need
  bounded recruiter-only reference context. That correction is implemented, and the dataset `1.2.0` 26-case
  live-provider gate passed on 12 November 2026. Focused staging acceptance then passed after PR #172 at revision
  `038c9bc`; production repeated the focused contextual-answer and sensitive-refusal checks in release `v0.37.0` at
  revision `657207f`. Dataset `1.3.0` adds the professional evidence-ranking case `Who is the best AI dev?`; its fresh
  27-case live-provider gate passed on 12 November 2026 while preserving sensitive-characteristic refusal. Both
  follow-up corrections are accepted before the dedicated conversational page; the flexible discovery extension still
  requires environment acceptance. Flyway V8 adds an application-owned visible timeline and minimized citation
  navigation metadata without duplicating CV excerpts. Owner/tenant-scoped recent-conversation listing, explicit
  resume, and historical citation-card reconstruction are implemented. Scope replacement, expiry, explicit deletion,
  and candidate erasure clear the applicable visible history and Spring memory. The combined PR #184 history slice
  and PR #185 post-render citation-scroll correction passed focused staging and production acceptance on 15 November
  2026, with production using release `v0.39.0` at revision `592b11b`.
- [~] Add the dedicated conversational CV-chat page after orchestration and scoped memory are stable. The first `/chat`
  slice provides explicit candidate search/selection, optional approved-job scope, an application-owned welcome
  bubble, one natural-language composer, a bounded message timeline, current-turn citations, active-conversation
  restoration, and explicit deletion. The candidate workspace remains available during migration. Conversation
  turns with one confirmed candidate and one approved job now reauthorize and supply bounded job requirements to both
  semantic retrieval and grounded answer generation; CV-only conversations retain their existing contract.
  listing and persisted citation metadata for reloaded turns are implemented through Flyway V8 and accepted in both
  environments through release `v0.39.0`. Language-first ambiguity confirmation and optimistic editable scope
  replacement are implemented. The pre-confirmation presentation now uses one timeline for recruiter requests,
  assistant clarification, and bounded candidate cards, while explicit scope confirmation remains required before any
  CV evidence request. Manual picker-first search remains an optional fallback. The flexible-scope, organization-discovery,
  owning-candidate citation navigation, and readable `Display Name (CANDIDATE-ID)` output passed focused staging
  acceptance on 12 November 2026 at main revision `9002238`. Production repeated the focused checks successfully in
  release `v0.38.0` at revision `eea0a3a`. The single-candidate approved-job path and qualified broad job-fit contract
  passed focused staging and production acceptance on 19 November 2026 through PRs #202–#204, with production release
  `v0.42.0` at revision `c0bff09`. PR #206 then completed the conversational pre-confirmation presentation with
  recruiter and assistant timeline messages, bounded proposal cards, explicit candidate/job confirmation, automatic
  pending-question submission, stale-proposal clearing, retry restoration, and picker-first fallback. Focused staging
  acceptance passed at revision `746609e`. PR #207 corrected only the BDD viewport synchronization with a bounded
  condition wait; all four scenarios passed, and production accepted release `v0.43.0` at revision `184e52a` on
  19 November 2026 without API, schema, model, retrieval, authorization, or environment changes.
- [x] Establish the browser behavior-driven development suite described in
  [browser BDD testing](./26-browser-bdd-testing.md). Use Cucumber-JVM from the first slice, JUnit Platform,
  PicoContainer scenario state, and Playwright for Java. Automate persisted conversation resume, historical citation
  viewport navigation, same-organization owner isolation, and explicit deletion before expanding to other critical
  recruitment workflows. The four-scenario protected staging suite and exact-revision production gate are operational.
  PR #204 updated picker-first setup for the conversational UI; the complete suite passed before release `v0.42.0` at
  revision `c0bff09`. PR #207 then replaced the citation target's race-prone immediate viewport read with a bounded
  wait for Angular's deferred scroll; the non-destructive suite passed all four scenarios and 23 steps before the
  BDD-gated `v0.43.0` production promotion. Deterministic browser BDD remains separate from paid provider evaluations
  and destructive production execution.
- [x] Build the bounded Angular candidate-chat interface with role visibility, loading/error states, safe responses,
  and document-linked citations.
- [x] Validate the bounded Angular candidate-chat interface and corrected same-page citation navigation in staging on
  28 October 2026.
- [x] Promote the accepted Angular candidate-chat interface to production and repeat focused role, answer, citation,
  navigation, failure, and privacy checks successfully on 28 October 2026.
- [~] Build semantic multi-candidate discovery and structured result cards. Manual name search and named-candidate
  language-first comparison remain available. The dedicated page now also provides organization-wide natural-language
  criteria discovery through the tenant-scoped `search_candidates_by_evidence` Spring AI read tool. It searches only
  current authorized semantic evidence, ranks a bounded shortlist of at most ten minimal cards, and still requires the
  recruiter to confirm no more than five candidates before conversation creation. Focused staging acceptance passed
  on 12 November 2026 at revision `9002238`, followed by production acceptance in release `v0.38.0` at revision
  `eea0a3a`; broader recall evaluation and persisted language-first confirmation history remain before completion.
- [~] Prove the model cannot resolve names globally, invent candidates, silently widen the selected set, or retrieve
  evidence outside the authenticated organization; require citations for every factual comparison. Current deterministic
  resolution, tenant authorization, visible confirmation, bounded per-candidate retrieval, and complete-citation tests
  cover the implemented flows; repeat these invariants for scoped memory and every new tool.
- [~] Add citations, clarification, tool-use explanations, injection defenses, and evaluation tests. Citations,
  clarification, refusal, privacy-safe tool diagnostics, versioned datasets, deterministic regressions, and opt-in
  provider gates exist; user-facing tool explanations and broader capability evaluation remain.
- [~] Measure retrieval recall at `k`, groundedness, citation correctness, completeness, refusal quality, tool selection,
  and cross-organization attack resistance. Candidate-discovery dataset `1.0.0` now runs deterministic recall and
  precision gates at `k = 3`, `5`, and `10` through the real ranking/current-row reauthorization service. It covers
  explicit-technology rescue, cross-organization and stale-source rejection, exact ordering, the ten-card bound, and
  truncation. A separate opt-in provider harness now supplies synthetic EN/FR/PT discovery cases, real constrained
  query normalization, real embeddings, recall/precision floors, a cross-organization decoy, privacy-safe latency, and
  ordinary-CI structural validation. The live discovery-provider gate passed on 20 November 2026. The next test-only
  slice adds a separately versioned synthetic answer-quality dataset, ordinary-CI schema review, exact controlled-
  outcome checks, application citation validation, required-reference completeness, and opt-in Spring AI relevancy
  and fact-checking judges. Its live provider gate also passed on 20 November 2026. Tool-orchestration dataset `1.3.0`
  now extends exact candidate/job call auditing with confirmed-scope candidate-result and job-result injection cases;
  its seven-case confirmed-scope provider run passed on 21 November 2026. Dataset `1.4.0` adds a Unicode
  compatibility-form workflow-override regression plus fake-structured-response and secret-request tool-result
  injections. Its expanded ten-case confirmed-scope provider run passed on 24 November 2026.

### Ordered pre-dashboard completion sequence

Complete the following slices in order. Each implementation slice requires deterministic tests, applicable opt-in
provider evaluation, focused staging acceptance, the protected exact-revision browser BDD gate, production promotion,
focused production checks, and recorded acceptance before the next dependent slice is treated as complete.

1. **[Completed] Connect document-processing tools to conversational chat.** Consume `get_document_status` and
   `get_ingestion_warnings` for natural questions about import state, OCR, reprocessing, manual review, and processing
   warnings. Require an application-confirmed candidate and authenticated tenant. Return structured operational facts,
   not CV evidence, and never expose filenames, raw parser/provider errors, storage keys, hashes, or CV content. PRs
   #214 and #215 passed staging and production acceptance in release `v0.44.0` at revision `2256171` on 22 November
   2026.
2. **[Completed] Connect persisted evaluation evidence to conversational chat.**
   Consume `get_evaluation_evidence` for questions
   about recent evaluation scores, approved-job context, timestamps, and current/stale state. Keep generated rationale
   and personal data excluded, distinguish evaluation history from CV-source evidence, and never present scores as
   objective truth or an autonomous hiring decision. The implementation installs only the three reviewed
   document/evaluation tools, requires exactly one applicable read per confirmed candidate, records successful results
   in request-local state, and renders bounded persisted rows deterministically with no CV citations. Initial staging
   validation found provider variation on repeated explicit evaluation wording and incomplete iteration for a
   five-candidate OCR request. The follow-up correction makes narrow explicit operational intent and per-candidate
   iteration application-owned while retaining the provider route for ambiguous language and reusing the same guarded,
   tenant-scoped `@Tool` methods. Deterministic backend coverage and the expanded live-provider operational-tool
   selection gate passed before deployment. PR #218 then passed corrected staging acceptance, the protected browser BDD
   gate, and focused production acceptance in release `v0.45.0` at revision `415c210` on 22 November 2026. The accepted
   checks covered repeatable one- and five-candidate OCR/status reads, warning-only and combined reads, evaluation
   history and persisted-job questions, exact per-candidate counts, professional-question RAG fallback, sensitive and
   workflow-override refusal, timeline reload, and existing grounded citations. No API, frontend, schema, Flyway,
   environment, Compose, or provider-configuration change was required. Step 3 is now the active implementation slice.
3. **[~ Active — first four slices completed, fifth implemented] Harden every user-visible tool path.** Test exact tool selection, bounded and
   malformed arguments, call-count and recursion limits, timeouts, result-size limits, tenant and confirmed-scope isolation,
   prompt/tool-result injection, conversation-memory isolation, deterministic postconditions, and provider-backed
   result faithfulness. Apply the existing privacy-safe logging boundary to every new diagnostic. The first hardening
   slice adds an application-owned validator for all model-generated read-tool arguments: query text is normalized and
   limited to 2–2,000 characters, record IDs must be positive, invalid confirmed candidate scopes are rejected, and
   rejected values are never echoed. Direct unit tests and real Spring AI callback tests prove malformed arguments stop
   before execution guards, repositories, embeddings, providers, or request-local result state.
   **Completed first slice:** application-owned tool-argument validation and oversized conversation-input rejection.
   PR #220 passed focused staging and
   production acceptance on 23 November 2026. Both environments rejected a 2,001-character conversation message with
   HTTP 400 at the documented 2,000-character boundary, preserved the existing conversation version and timeline, and
   performed no downstream tool or provider execution. The same acceptance retained valid authenticated conversation
   access and owner-scoped state. No schema, Flyway, frontend, Compose, or environment change was required.
   **Completed second slice:** capability-specific request budgets now complement the
   ten-call global ceiling. Candidate search, summary, evaluation, status, and warning capabilities allow no more than
   five calls; organization discovery and approved-job requirements allow one. Repeated calls are rejected before a
   second downstream read. Atomic preinitialized counters preserve the boundary under concurrent callback attempts.
   The model-facing organization-discovery boundary also independently caps results at ten cards and three evidence
   references per card. Existing three-second default and twenty-second semantic-discovery timeouts remain enforced.
   Focused tests, real Spring AI callback integration, and the complete 405-test backend suite passed before merge.
   PR #222 then passed focused staging and production acceptance in release `v0.47.0` at revision `c7aa1bd` on
   23 November 2026. Acceptance covered five distinct candidate searches, five-candidate document-status, warning,
   and evaluation reads, request-local budget reset across repeated requests, the valid ten-call combined
   status-and-warning boundary, the ten-card/three-reference discovery limits, and five candidates plus an optional
   approved job. When the advisory provider made no preflight calls and returned clarification, the application-owned
   fallback safely reauthorized the same scope and returned `CONFIRMED_SCOPE_READY`. No erroneous call-limit or timeout
   response occurred, and grounded RAG, citations, controlled refusal, tenant scope, and confirmation remained intact.
   PR #223 in the same release added paragraph separation between candidates in operational and evaluation-history
   answers; focused staging and production acceptance confirmed readable multi-candidate output without changing the
   tool data, API, persistence, authorization, or citation contracts. Neither PR required schema, Flyway, frontend,
   Compose, environment, or provider-configuration changes.
   **Completed third slice:** reviewed tool failures now retain their application-owned
   status and privacy-safe message across both deterministic calls and Spring AI callbacks. In particular, a required
   tool timeout reaches the conversation HTTP boundary as `504 CV chat tool execution timed out` instead of being
   flattened into a generic `502` or converted to model-readable tool-result text. Spring AI continues its default
   exception-to-tool-result behavior for unknown callback failures; only reviewed `CvChatException` outcomes are
   rethrown. The optional confirmed-scope advisory preflight remains best-effort and may still use the independently
   authorized application fallback because its result is neither a required operational answer nor an authorization
   decision. Unit, callback-processor, service, and controller tests cover both propagation paths and the HTTP contract.
   PR #230 passed focused staging and production acceptance in release `v0.49.0` at revision `64f8fa8` on
   24 November 2026. Normal document-status, warning, evaluation-history, grounded RAG, citation, scope, and refusal
   behavior remained healthy, while deterministic fault injection verified that required timeouts retain HTTP `504`
   rather than becoming generic `502` responses. PR #231 added the previously missing caller-interruption regression:
   the test blocks virtual-thread work with a one-use latch, interrupts the caller, verifies cancellation and restoration
   of the interrupt flag, carries the reviewed failure through Spring AI, and asserts HTTP `503` with the safe
   `CV chat tool execution interrupted` message. Its focused 14-test set and complete 413-test backend suite passed;
   eight opt-in provider tests remained skipped. PR #231 changed tests and comments only. Neither PR required schema,
   Flyway, frontend, Compose, environment, provider-configuration, or semantic-index changes.
   **Completed fourth slice:** expanded deterministic isolation coverage proves another owner cannot
   load an execution scope, candidate/job scope, memory, messages, or citations; an ownership failure stops before
   history, operational tools, RAG, comparison, or provider work; and malicious prior history cannot replace the
   persisted tenant, candidate, or approved-job scope. Real Spring AI callbacks for candidate summary, document status,
   ingestion warnings, evaluation evidence, and job requirements now all reject unconfirmed IDs before repository
   access. Existing schema tests continue to prove that model-controlled arguments contain no tenant, organization,
   role, or authorization fields, while deterministic/provider datasets retain candidate/job tool-result injection,
   confirmation-bypass, and scope-expansion cases. The focused isolation suite passed 50 tests and the complete backend
   suite passed 418 tests with eight existing opt-in provider tests skipped. The separately enabled
   `CvChatToolOrchestrationProviderEvaluationTest` and `CvChatDocumentToolProviderEvaluationTest` live-provider gate
   also passed before merge, confirming bounded scope and tool-result faithfulness against the configured provider.
   **Completed fifth slice:** the singleton virtual-thread executor now applies a fair ten-permit
   bulkhead before any guarded tool action reaches a repository or provider. A virtual thread waits at most 100 ms for
   capacity, then returns HTTP `503` with the privacy-safe `CV chat tool execution capacity is unavailable` response;
   the rejected action never runs. A caller timeout requests cancellation but does not release the permit early: work
   that ignores interruption continues holding capacity until it actually exits. Deterministic latch-based tests prove
   saturation, zero rejected downstream work, permit recovery, Spring AI rethrow, and the HTTP contract without timing
   sleeps. The conservative limit aligns with the default ten-connection JDBC pool pending the later measured
   concurrency benchmark; no tenant, request, prompt, or candidate state is stored in the singleton executor. The
   existing `CvChatToolOrchestrationProviderEvaluationTest` and `CvChatDocumentToolProviderEvaluationTest` live-provider
   gate also passed, confirming normal-capacity tool selection and bounded result faithfulness. PR #235 then passed
   focused staging acceptance, controlled staging saturation/recovery validation, and the protected non-destructive BDD
   gate before the exact accepted revision was promoted. Release `v0.50.0` at revision `d654eb6` passed focused
   production acceptance on 24 November 2026 without running the intentional saturation load against production.
   Accepted normal paths covered organization discovery, one- and five-candidate operational/evaluation questions,
   grounded comparison and citations, conversation resume/navigation, and post-test backend health. No schema, Flyway,
   frontend, Compose, environment, provider-configuration, or semantic-index change was required.
   Broader prompt/tool-result adversarial calibration is now complete for the current callback set: Unicode
   compatibility forms are normalized before the narrow application-owned override guard, and synthetic candidate/job
   results test fake structured outcomes and secret requests while preserving exact confirmed calls. Dataset `1.4.0`'s
   ten-case provider gate passed on 24 November 2026.
   Deterministic postconditions now cover every registered read-tool path. Name discovery requires one search for each
   extracted reference; confirmed-scope planning requires exact candidate/job IDs and exactly one completed call per
   ID; document/evaluation routing retains exact per-candidate and per-category maps; organization discovery is a
   single Java-owned invocation with independent callback result bounds. Malformed, duplicate-ID, and identity-
   mismatched results fail before successful completion is recorded.
   PR #238 passed focused staging checks, protected staging BDD, and production smoke tests after promotion.
   Provider-result faithfulness checks now distinguish exact tool execution from advisory prose. The deterministic
   service regression checks that unsupported provider claims never replace the application-owned ready message,
   even when the provider returns the expected kind and exact coverage. The opt-in orchestration gate also checks
   that synthetic candidate-summary and job-requirement markers are not repeated in advisory prose. This is a
   targeted check, not a general factuality judge. Operational facts remain rendered from validated Java DTOs.
   The updated opt-in orchestration provider evaluation passed, including the synthetic result-marker checks.
   **Step 3 is complete.**
4. **Complete the dedicated conversational CV-chat experience.** Keep language-first discovery, ambiguity cards,
   picker-first fallback, organization-wide discovery, candidate/job scope editing, single- and multi-candidate turns,
   clarification, citations, retry/error states, conversation resume, new-chat, and deletion inside one coherent
   message timeline. The UI must not require recruiters to understand internal endpoint or tool phases.
   PR #241 passed staging acceptance, protected staging BDD, and production smoke tests for saved-chat recovery.
   PR #242 passed production smoke tests for grouped named discovery.
   PR #243 passed production smoke tests for scope-edit clarity and history-reset warning.
   PR #244 passed protected staging BDD and production smoke tests for failed-send recovery. An uncertain send
   keeps the draft and offers a read-only history check; it never automatically resends the message.
   The final deployed walkthrough passed: named discovery, scope confirmation and editing, a grounded answer with
   citation navigation, and saved-chat resume. The full local Angular suite also passed (151 tests).
   **Step 4 is complete.**
5. **Add structured chat outcomes.** Return explicit application-owned metadata for answer, insufficient evidence,
   out-of-scope refusal, clarification, candidate confirmation, tool status, and provider unavailability. Angular must
   render the structured outcome rather than infer state by comparing English message text. The `kind` field is an
   application API contract, not a Spring AI convention.
   PR #246 passed staging acceptance, protected staging BDD, and production smoke tests for live outcome metadata.
   Flyway V9 adds a nullable outcome for saved assistant messages. New turns retain their label on resume; user
   turns and older messages have no outcome, and are not backfilled from wording or citation count.
   PR #247 passed staging acceptance, protected staging BDD, and production tests for persisted outcomes.
   Clarification and candidate confirmation use the existing discovery workflow kinds. Provider and tool failures
   remain HTTP errors, not successful answer kinds. Angular uses HTTP status for recovery; if a write may have
   succeeded, it checks history before allowing a manual resend. No provider-specific error code is needed for
   the current UI because it would not change that recovery action.
   **Step 5 is complete.**
6. **Add response-language metadata and localization.** Preserve the recruiter question language and localize
   application-owned controlled responses, errors, clarification labels, and Angular presentation for English, French,
   and Portuguese. Retain the corrected French KLx paraphrase as a regression case.
   First slice: application-owned insufficient-evidence and out-of-scope answers use the HTTP request locale
   (English fallback) and expose the rendered language as `responseLanguage`. This metadata does not claim the
   language of provider-generated answers or operational text. Question-language handling, remaining controlled
   copy, Angular localization, and localized history metadata are separate follow-ups.
   PR #249 passed staging acceptance, protected staging BDD, and production smoke tests.
   Dedicated CV chat can override the browser locale for new controlled replies with a page-level selector;
   it does not change saved wording, grounded answers, operational replies, or the Angular UI language.
   PR #250 passed staging acceptance, protected staging BDD, and production smoke tests.
   Dedicated CV-chat outcome labels and recovery controls use the browser's preferred language (English, French,
   or Portuguese, with English fallback). This is independent of the reply-language selector and does not
   translate saved answers or backend error details.
   PR #252 passed staging acceptance, protected staging BDD, and production smoke tests.

   **Dedicated CV-chat reply-language precedence:** use an explicit supported reply
   selection first, then the current question's language when it can be identified reliably as English, French,
   or Portuguese, then the browser locale, and finally English. For example, a clear French question in an
   English browser receives a French controlled reply in automatic mode; an explicit Portuguese selection wins.
   Automatic mode uses a conservative frontend heuristic for English, French, and Portuguese. Short or ambiguous
   questions leave the request language to the browser; the backend falls back to English for unsupported locales.
   This affects only new controlled replies in dedicated chat. Interface language, saved wording, provider-generated
   answers, and workspace/API behavior are unchanged.
   PR #254 passed staging acceptance, protected staging BDD, and production smoke tests.
   Flyway V10 preserves known reply-language metadata on new assistant turns. Live and resumed replies carry
   the same language tag for assistive technology; legacy and unknown languages remain unset without backfill.
   PR #255 passed staging acceptance, protected staging BDD, and production smoke tests.
   The dedicated CV-chat page translates the remaining welcome, discovery, confirmation, scope, composer, and
   error copy. Discovery endpoints translate reviewed guidance by workflow kind using the request locale. The UI
   shows localized operation-specific errors rather than raw backend messages; reply-language selection is unchanged.
   PR #256 passed multilingual staging acceptance, protected staging BDD, and production smoke tests.
   **Step 6 is complete.**
7. **Evaluate the completed conversational surface.** Expand reviewed deterministic/provider datasets across
   multilingual follow-ups, approved-job assessments, comparisons, organization-wide discovery, processing-status
   answers, evaluation-history answers, groundedness, citation correctness/completeness, refusal quality, tool
   selection/faithfulness, repeated-run stability, latency, and cost. Keep paid provider gates separate from ordinary CI.
   First slice: opt-in language-switch follow-ups check English-to-French and French-to-Portuguese against fixed
   synthetic English evidence. The answer must resolve the prior project reference, use only the current citation,
   and follow the current question language. This isolates answer generation with bounded history; the existing
   multilingual retrieval evaluation remains separate. Both language-switch cases passed the opt-in live-provider run.
   Approved-job follow-up slice: the opt-in answer-model regression supplies a confirmed job, current synthetic CV
   evidence, and history with an unrelated role and old citation. It checks that the answer identifies supported
   Java 21 experience and an evidence gap for Kubernetes, without borrowing the earlier role or citation. The
   deterministic conversation tests cover passing the approved job and history into the answer path. The new
   approved-job follow-up case passed the opt-in live-provider run.
   PR #259's exact single-candidate follow-up returned a grounded-answer outcome in staging instead of the
   pre-retrieval refusal. The separate job-fit grounding issue is deferred until after the concurrency benchmark.
   The next opt-in comparison case uses two confirmed candidates and one approved job. It checks that both current
   CVs are cited and that an unconfirmed candidate mentioned in history stays out of the answer. The opt-in
   live-provider run passed.
   Organization-wide discovery handoff: deterministic Angular coverage checks that a shortlist does not create
   scope until explicit confirmation, that only selected IDs are submitted, and that the first message uses the
   saved conversation rather than the shortlist. It renders a controlled insufficient-evidence comparison without
   citations; the backend comparison regression separately verifies that missing evidence prevents a ranking.
   Tool-backed follow-up coverage switches from a grounded CV answer to OCR status, ingestion warnings, and
   persisted evaluation history, then back to grounded chat in the same modeled conversation. The regression uses
   real operational routing and rendering with synthetic tool results, verifies current/stale evaluation facts,
   and checks that only grounded turns carry CV citations. No database or live provider is needed for this test.
   Cross-cutting answer-quality coverage exercises the comparison service's conversation-history path with a
   French follow-up after English history. With synthetic retrieval and model outputs, it checks valid citation
   ownership, rejects a stale citation with the reviewed provider error, and distinguishes incomplete citation
   coverage from the answer model's controlled refusal. The opt-in multilingual suite covers actual provider wording.
   The opt-in answer-model baseline runs a grounded question, a French follow-up, and an approved-job question
   three times each. It records quality pass counts, median/max latency, and provider-reported input/output tokens
   where available. These are sequential synthetic answer-generation measurements, not HTTP latency, load-test
   results, or a billing estimate. After fixing the wrapper to preserve OpenAI-specific options, the live run
   with `gpt-4o-mini` passed all nine attempts and reported token usage for every attempt.

   | Case | Passed | Median (ms) | Max (ms) | Input tokens | Output tokens |
   | --- | ---: | ---: | ---: | ---: | ---: |
   | Grounded | 3/3 | 953 | 3,618 | 1,911 | 48 |
   | French follow-up | 3/3 | 946 | 969 | 1,986 | 57 |
   | Approved job | 3/3 | 1,296 | 1,977 | 2,034 | 123 |

   Token counts are totals across the three attempts per case. **Step 7 is complete.**
8. **Expand browser BDD.** Cover language-first discovery/confirmation, picker fallback, editable scope, single- and
   multi-candidate turns, organization-wide discovery, tool-backed status and evaluation questions, localized
   controlled outcomes, resume/deletion, citation navigation, and owner/tenant isolation. Successful protected staging
   BDD remains mandatory for production promotion.
   First scenario: organization-wide discovery proposes at least three cards; the recruiter confirms two and
   verifies that the comparison cites exactly those two candidates. This provider-backed browser scenario stays
   opt-in and passed its first live staging run. The existing protected smoke gate is unchanged.
   Named-discovery coverage checks ambiguous name groups, explicit selection, and the selected scope after
   reload. The opt-in scenario passed its first live staging run (one scenario, no failures).
   Editable-scope coverage uses a deterministic OCR-status turn, then adds an approved job. It checks that
   the same conversation retains the edited scope and does not restore the previous timeline after reload.
   This opt-in scenario requires an approved staging job and passed its first live staging run (14.05 seconds,
   no failures or skips for the scenario).
   Operational follow-up coverage checks OCR status, ingestion warnings, and stored evaluation scores in one
   confirmed conversation without CV citations. The opt-in scenario requires a staging candidate with a current
   CV and persisted evaluation. The corrected scenario passed in live staging (10.764 seconds, no failures or skips).
   Localized controlled-reply coverage checks insufficient evidence and policy refusals in English, French, and
   Portuguese, with English interface badges and no CV citations. This provider-backed scenario is opt-in;
   all three language examples passed in live staging with no failures or skips for those examples.
9. **Complete the performance and observability milestone.** Measure policy classification, normalization, embedding,
   retrieval, tool, and answer latency; define and validate p50/p95/p99 targets before optimization. Add privacy-safe
   Micrometer/OpenTelemetry instrumentation and evaluate Prometheus/Grafana for aggregate monitoring. Never export CV
   content, names, questions, prompts, or credentials, and never place them in metric labels.
10. **Add editable speech-to-text.** Provide push-to-talk, visible recording state, cancellation/retry, microphone
    permission and accessibility handling, and an editable transcript before submission. Delete raw audio after
    transcription by default unless a separately approved retention exception is documented.
11. **Add optional text-to-speech.** Provide explicit play, pause, stop, and speed controls with no automatic playback,
    accessible interaction, privacy-safe handling, and graceful provider failure.
12. **Evaluate speech and add BDD coverage.** Measure transcription accuracy, recruiter correction rate, transcription
    and playback latency, failures, usage, and cost. Prohibit voice identification, accent/fluency ranking, emotion or
    personality inference, protected-characteristic inference, and candidate scoring from audio.
13. **Run final pre-dashboard acceptance.** Run complete backend/frontend suites, deterministic AI datasets, relevant
    paid provider gates, protected staging BDD, focused staging checks, exact-revision production promotion, and focused
    production checks. Record staging and production acceptance, environment/migration impact, final architecture, and
    the updated roadmap before starting dashboard metric semantics or aggregate APIs.

**Exit condition:** Authorized users can reliably find and compare candidates through typed or spoken queries, with editable transcripts, evidence-backed answers, and optional accessible playback.

## Phase 4 - Build the dashboard

- [ ] Define metric semantics and freshness targets.
- [ ] Implement aggregate APIs and database indexes.
- [ ] Build filterable dashboard widgets and drill-down navigation.
- [ ] Add near-real-time updates and resilient UI states.
- [ ] Add evidence-backed AI insights.
- [ ] Validate accessibility, usability, and the 3-second target.

**Exit condition:** Recruiters can monitor roles and candidate evaluations and reach source records from every aggregate view.

## Phase 5 - Complete the codebase-wide source-documentation review

- [ ] Inventory backend and frontend production code by business capability and integration boundary.
- [ ] Add missing Javadoc to public contracts whose inputs, outputs, errors, side effects, or security rules are not
  evident from types and names.
- [ ] Document tenant isolation, authorization, privacy, transaction, locking, rollback compensation, idempotency,
  scheduling, external storage, Redis, ClamAV, AI-provider, and future OCR boundaries where the reason is non-obvious.
- [ ] Document complex Angular state synchronization and security-sensitive presentation rules.
- [ ] Remove inaccurate or redundant comments discovered during the review; do not comment trivial accessors or
  self-explanatory implementation details.
- [ ] Run the complete backend and frontend regression suites after the review.

**Exit condition:** Non-obvious public contracts and technical or business boundaries are documented accurately across
the codebase, redundant commentary is absent, and regression tests pass.

## Phase 6 - Publish backend Swagger/OpenAPI documentation

This phase starts after the dashboard and source-documentation review are complete so the documented API includes the
stable job-offer, CV, evaluation, chat, and dashboard contracts rather than an incomplete surface.

- [ ] Add and configure Springdoc OpenAPI for the Spring Boot backend.
- [ ] Document every supported endpoint, request model, response model, validation rule, and HTTP status.
- [ ] Document the shared API error contract and provide safe example payloads without candidate personal data or credentials.
- [ ] Describe authentication and authorization requirements after the production security model is finalized.
- [ ] Group APIs by business capability and add operation summaries suitable for frontend and integration developers.
- [ ] Expose Swagger UI in local development and approved non-production environments; make production exposure an explicit security decision.
- [ ] Add automated validation that the OpenAPI document is generated and that critical endpoints remain represented.
- [ ] Publish or export the versioned OpenAPI document for consumers and keep it synchronized with releases.

**Exit condition:** Backend consumers can discover and test the stable API through reviewed, versioned, security-conscious Swagger/OpenAPI documentation.

### Post-OpenAPI frontend design refresh (before Phase 7)

Redacted design references: [sign-in page](assets/frontend-refresh/sign-in-redacted.png) and
[signed-in desktop menu](assets/frontend-refresh/desktop-menu-redacted.png). These references illustrate the
existing branding and two-row navigation issue; they are not redesign mockups.

- [ ] Use the NevGiU logo and reference screenshots to define a coordinated color palette for the Angular app.
  Check contrast for text, controls, and focus indicators before adopting the palette.
- [ ] Keep the signed-in desktop header on one row, including when admin links are visible. Keep core recruiter
  links visible, group account actions and admin links in compact menus, and avoid putting the email and sign-out
  controls on a second row. When space is tight, collapse navigation instead of letting it wrap or overflow.
  Preserve active-page indicators, role-based visibility, and existing routes. Verify keyboard navigation.
- [ ] Review the proposed palette and menu against the reference screenshots before implementation.

### Post-OpenAPI provider test review (before Phase 7)

- [ ] Inventory all live-provider tests, their enablement flags, required configuration, models, and expected case counts.
- [ ] Audit existing pass claims against saved run output. Mark missing evidence as unverified; a skipped test or
  Maven `BUILD SUCCESS` alone does not prove a live run passed.
- [ ] Rerun unverified provider tests explicitly with synthetic fixtures and reviewed cost bounds. Verify that the
  targeted tests and expected cases actually executed, with no skips before recording a pass.
- [ ] Record run date, revision, model, executed/passed/failed/skipped counts, and the report location. Keep paid
  runs opt-in and exclude credentials and personal data from evidence.

## Phase 7 - Benchmark concurrency and virtual threads

- [ ] Establish platform-thread load-test baselines for AI, JDBC, ingestion, chat, and dashboard workloads.
- [ ] Evaluate Spring Boot virtual threads in staging for blocking orchestration, with `spring.main.keep-alive` and a
  reversible environment setting.
- [ ] Bound PostgreSQL, OpenAI, OCR, and speech concurrency independently with timeouts, rate limits, bulkheads, or
  semaphores.
- [ ] Keep PDF parsing, ZIP decompression, local OCR, and media encoding on bounded CPU-oriented workers.
- [ ] Monitor pinned virtual threads, carrier starvation, database-pool waits, tail latency, provider throttling,
  cancellation, and graceful shutdown.
- [ ] Keep virtual threads disabled unless repeatable staging evidence demonstrates a worthwhile improvement without
  violating downstream budgets.

**Exit condition:** the selected thread model is supported by repeatable staging measurements and explicit resource
limits, and can be reverted through configuration.

### Deferred CV-chat job-fit grounding correction (after Phase 7)

- [ ] Revisit inconsistent evidence coverage in broad approved-job assessments. The same staging CV surfaced Java 21
  and production Kubernetes/ArgoCD in some turns but not others. Compare broad and targeted retrieval before
  deciding whether the fix belongs in retrieval, answer generation, or both.
- [ ] Add regressions for treating missing retrieved evidence as proof that a candidate lacks experience, listing one
  requirement as both supported and unsupported, and overlooking deployment tools named in a cited passage.
  Keep this separate from the approved-job follow-up classifier fix and Step 7 evaluation coverage.

## Phase 8 - Install and validate paired backup/restore

- [ ] Provision a staging-only destination, upload-only credentials, public `age` recipient, Object Lock, and lifecycle
  policy without reusing production credentials or recovery identities.
- [ ] Install the paired service in staging, run a manual recovery set, and confirm backend health recovery.
- [ ] Complete an isolated staging restore and validate business counts, tenant boundaries, original-file references,
  representative downloads, and deletion replay.
- [ ] Install the same service in production only after staging acceptance, using production-only configuration.
- [ ] Repeat the manual upload, Object Lock, isolated restore, and application-health checks in production.
- [ ] After production paired backup/restore acceptance, persist versioned evaluation prompts, requested model and
  configuration, exact weights, source-document/text fingerprints, and privacy-safe history metadata. Preserve legacy
  evaluations without inventing unavailable values, and treat CV-bearing prompt snapshots as personal data.
- [ ] Enable each timer only after that environment passes its complete acceptance sequence.

**Exit condition:** staging and production can each produce and restore a complete encrypted PostgreSQL/original-file
recovery set, with environment-isolated credentials and recorded evidence.

## Phase 9 - Cross-cutting release readiness

- [ ] Run full unit, integration, end-to-end, performance, accessibility, usability, privacy, and security test suites.
- [ ] Run deterministic AI invariants on every pull request and a stable provider-backed evaluation smoke set where cost and availability permit.
- [ ] Run the complete versioned AI evaluation suite nightly, before model or prompt promotion, and before production release.
- [ ] Calibrate Spring AI and custom evaluator judgments against human labels; track false passes, false failures, and drift.
- [ ] Version generation and evaluator models, prompts, retrieval settings, tools, weights, datasets, and thresholds together.
- [ ] Verify backup, restore, retention, deletion, and incident procedures.
- [ ] Add model, token, latency, error, retrieval-quality, transcription-quality, media, and cost monitoring.
- [ ] Add privacy-safe Spring AI observations for chat, advisors, models, embeddings, tools, and vector stores with all content-bearing telemetry disabled.
- [ ] Add custom OCR, speech, messaging, backup, evaluation-run, confirmation, and background-job observations.
- [ ] Evaluate Prometheus plus Grafana as the initial open-source metrics/dashboard stack; keep Docker log inspection as
  the current single-host practice and do not deploy the observability stack before this milestone.
- [ ] Export metrics, traces, and logs through encrypted, authenticated, environment-separated off-server collection without exposing Actuator publicly.
- [ ] Validate trace propagation across virtual threads, streaming, scheduled work, background jobs, and provider callbacks.
- [ ] Build and test actionable dashboards, alerts, telemetry-pipeline monitoring, operator access, redaction, and incident-export procedures.
- [ ] Conduct recruiter acceptance testing with representative workflows.
- [ ] Document deployment, rollback, support, and model-change procedures.

**Exit condition:** Operational, security, privacy, AI-quality, performance, accessibility, usability, and product owners
approve release.

## Phase 10 - Move originals to private object storage and consider horizontal scaling

This phase follows release readiness. Private object storage is required before backend processing can be distributed
across multiple hosts. The current private Docker volume remains supported while the application runs on one backend
host.

- [ ] Replace the local original-CV provider with private object storage.
- [ ] Preserve opaque tenant-scoped keys, encryption, quarantine, retention, deletion, legal-hold, and audit controls.
- [ ] Migrate existing originals with integrity verification and without exposing filenames or candidate content.
- [ ] Validate paired database/object restoration and access from every intended backend or worker replica.
- [ ] Re-run capacity, failure, session, scheduling, and storage-consistency tests before deciding whether horizontal or
  multi-host scaling is justified.

**Exit condition:** original files are private, durable, and consistently available across intended hosts, and any
horizontal-scaling decision is supported by measured demand and failure testing.

## Phase 11 - Add external messaging

- [ ] Implement the selected provider behind a channel-neutral messaging interface.
- [ ] Link messaging identities to authenticated application users securely.
- [ ] Reuse the same authorization, retrieval, and conversation services as web chat.
- [ ] Minimize sensitive candidate data in messages and links.
- [ ] Add provider signature validation, rate limiting, retry, and audit logging.
- [ ] Add consent-based voice-note transcription only after web speech controls and retention behavior are validated.

**Exit condition:** External chat has security and answer quality equivalent to the web channel.

## Phase 12 - Add optional generated media

- [ ] Validate a measurable employer-branding or communication need before implementing generated media.
- [ ] Add image generation only for human-reviewed job-post, employer-branding, or interface assets.
- [ ] Require accessibility text, moderation, brand controls, provenance, and clear AI-generated-content labeling.
- [ ] Prohibit candidate-photo generation or alteration and prohibit candidate assessment from images or appearance.
- [ ] Keep video generation behind a separate product and compliance gate after image-generation value is proven.
- [ ] Limit approved video use to human-reviewed job explainers, employer branding, recruiter training, or onboarding.
- [ ] Require captions, transcripts, provenance, moderation, disclosure, retention limits, and cost controls for generated video.
- [ ] Prohibit synthetic interviewers and candidate scoring from face, gesture, attention, emotion, appearance, or behavior.

**Exit condition:** Optional generated media has a validated communication benefit, is clearly disclosed, and remains completely outside candidate evaluation and ranking.

## Definition of done for every feature

- [ ] Requirements and acceptance criteria are agreed.
- [ ] API and data contracts are documented.
- [ ] Authorization and privacy behavior are tested.
- [ ] AI output is explainable and low-confidence behavior is explicit.
- [ ] Unit and integration tests pass.
- [ ] Relevant end-to-end flow passes.
- [ ] Error, empty, timeout, and retry states are handled.
- [ ] Logs and metrics support diagnosis without exposing candidate data.
- [ ] User documentation is updated.
