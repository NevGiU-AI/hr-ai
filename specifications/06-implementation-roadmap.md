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

**Current delivery sequence:** candidate details and ingestion history -> correction/reprocessing -> OCR -> typed CV chat
-> speech input/playback -> final dashboard -> codebase-wide comments/Javadoc review -> publish stable OpenAPI contracts
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
- [ ] Version request, response, prompt, and persistence schemas.
- [ ] Add direct approval, job details, lifecycle maintenance, and production-grade failure handling.
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
- [x] Add the Flyway-owned pgvector chunk schema, bounded native/OCR chunking, Spring AI embeddings, composite tenant
  constraints, post-commit indexing, reprocessing replacement, deletion cascades, and bounded reconciliation backfill.
- [ ] Validate V6 and the opt-in indexing lifecycle in staging before enabling semantic retrieval in user-facing chat.
- [ ] Integrate the internal tenant-scoped similarity service into authorized chat retrieval.
- [ ] Implement retrieval, filtering, candidate detail, and comparison tools.
- [ ] Expose authorized candidate, job, evaluation-evidence, document-status, and dashboard services as request-scoped Spring AI query tools.
- [ ] Inject authenticated user and organization context outside model-controlled tool arguments.
- [ ] Enforce tool-call count, recursion, time, payload, result, concurrency, and provider limits.
- [ ] Add prompt-injection tests proving CV text and chat input cannot expand tool access or cross organization boundaries.
- [ ] Add preview-only command tools, then require explicit confirmation, idempotency, and audit records before any state change or external communication.
- [ ] Pin tool implementation to the current Spring AI version and evaluate the 2.x `ToolCallingAdvisor` migration separately with regression tests.
- [ ] Persist scoped conversation memory.
- [x] Build the bounded Angular candidate-chat interface with role visibility, loading/error states, safe responses,
  and document-linked citations.
- [x] Validate the bounded Angular candidate-chat interface and corrected same-page citation navigation in staging on
  28 October 2026.
- [x] Promote the accepted Angular candidate-chat interface to production and repeat focused role, answer, citation,
  navigation, failure, and privacy checks successfully on 28 October 2026.
- [ ] Build semantic multi-candidate search and structured result cards.
- [ ] Add citations, clarification, tool-use explanations, injection defenses, and evaluation tests.
- [ ] Measure retrieval recall at `k`, groundedness, citation correctness, completeness, refusal quality, tool selection, and cross-organization attack resistance.
- [ ] Add push-to-talk speech input with a visible, editable transcript before submission.
- [ ] Delete raw voice input after transcription by default and document any approved retention exception.
- [ ] Add optional text-to-speech playback with pause, speed, accessibility, and privacy controls.
- [ ] Measure transcription accuracy, speech latency, playback usage, and modality-specific cost.
- [ ] Prohibit voice identification, accent or fluency ranking, emotion inference, personality inference, and candidate scoring from audio.

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
