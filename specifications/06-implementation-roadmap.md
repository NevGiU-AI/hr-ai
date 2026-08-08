# Implementation Roadmap

## Phase 0 - Resolve foundations

- [ ] Decide Telegram, WhatsApp, or both.
- [ ] Finalize the composite scoring formula and individual weights.
- [ ] Define authentication, roles, organization boundaries, and audit requirements.
- [ ] Define candidate consent, retention, deletion, and data-residency policies.
- [x] Define the currently supported CV formats and upload limits (PDF, ZIP, 20 MB per file, 100 MB per request).
- [x] Establish the initial typed API error format and environment configuration.
- [x] Implement CI, immutable container publication, automatic staging deployment, and staging validation aliases.
- [x] Provision and harden separate staging and production VPSs with environment-specific keys and credentials.
- [x] Configure production Premium VPS backup and prepare private cross-region Object Storage.
- [ ] Complete production observability, off-server log collection, redaction, and alerting standards.
- [ ] Complete encrypted PostgreSQL backup automation and an isolated restoration test.
- [x] Validate production release promotion with GitHub environment protection, approval, and image rollback configured.
- [ ] Confirm all development, staging, production, and backup credentials are isolated, rotated when needed, and absent from Git.

**Exit condition:** Product, security, and scoring decisions are documented and testable.

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
- [ ] Store original CV binaries behind a governed storage abstraction.
- [ ] Add native-text quality detection and OCR fallback for scanned or low-text PDFs.
- [ ] Preserve page references, extraction provenance, and OCR confidence for human review.
- [ ] Add metadata correction and document reprocessing without duplicating candidates.
- [ ] Add metric-level evidence and separate reliability confidence from candidate fit.
- [ ] Expose approved weight configuration in the frontend.
- [ ] Persist prompt, model, weights, source-document version, and evaluation audit history.
- [ ] Build candidate details, ingestion history, and evaluation history UI/API.
- [ ] Validate scoring consistency, bias controls, and adversarial inputs.
- [ ] Add golden CV, OCR, evidence, counterfactual, and ranking-stability evaluation datasets with deterministic and calibrated semantic metrics.

**Exit condition:** Uploaded candidates receive explainable, reproducible, human-reviewable evaluations.

## Phase 3 - Add semantic indexing and multimodal web chat

- [ ] Add the backend vector-store integration; the PostgreSQL image supports pgvector, but CV chunking, embeddings, indexing, and retrieval are not implemented.
- [ ] Create embeddings and index CV chunks with authorization metadata.
- [ ] Implement retrieval, filtering, candidate detail, and comparison tools.
- [ ] Expose authorized candidate, job, evaluation-evidence, document-status, and dashboard services as request-scoped Spring AI query tools.
- [ ] Inject authenticated user and organization context outside model-controlled tool arguments.
- [ ] Enforce tool-call count, recursion, time, payload, result, concurrency, and provider limits.
- [ ] Add prompt-injection tests proving CV text and chat input cannot expand tool access or cross organization boundaries.
- [ ] Add preview-only command tools, then require explicit confirmation, idempotency, and audit records before any state change or external communication.
- [ ] Pin tool implementation to the current Spring AI version and evaluate the 2.x `ToolCallingAdvisor` migration separately with regression tests.
- [ ] Persist scoped conversation memory.
- [ ] Build the Angular chat interface and structured result cards.
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

## Phase 5 - Publish backend Swagger/OpenAPI documentation

This phase starts after the dashboard is complete so the documented API includes the stable job-offer, CV, evaluation, chat, and dashboard contracts rather than an incomplete surface.

- [ ] Add and configure Springdoc OpenAPI for the Spring Boot backend.
- [ ] Document every supported endpoint, request model, response model, validation rule, and HTTP status.
- [ ] Document the shared API error contract and provide safe example payloads without candidate personal data or credentials.
- [ ] Describe authentication and authorization requirements after the production security model is finalized.
- [ ] Group APIs by business capability and add operation summaries suitable for frontend and integration developers.
- [ ] Expose Swagger UI in local development and approved non-production environments; make production exposure an explicit security decision.
- [ ] Add automated validation that the OpenAPI document is generated and that critical endpoints remain represented.
- [ ] Publish or export the versioned OpenAPI document for consumers and keep it synchronized with releases.

**Exit condition:** Backend consumers can discover and test the stable API through reviewed, versioned, security-conscious Swagger/OpenAPI documentation.

## Phase 6 - Add external messaging

- [ ] Implement the selected provider behind a channel-neutral messaging interface.
- [ ] Link messaging identities to authenticated application users securely.
- [ ] Reuse the same authorization, retrieval, and conversation services as web chat.
- [ ] Minimize sensitive candidate data in messages and links.
- [ ] Add provider signature validation, rate limiting, retry, and audit logging.
- [ ] Add consent-based voice-note transcription only after web speech controls and retention behavior are validated.

**Exit condition:** External chat has security and answer quality equivalent to the web channel.

## Phase 7 - Add optional generated media

- [ ] Validate a measurable employer-branding or communication need before implementing generated media.
- [ ] Add image generation only for human-reviewed job-post, employer-branding, or interface assets.
- [ ] Require accessibility text, moderation, brand controls, provenance, and clear AI-generated-content labeling.
- [ ] Prohibit candidate-photo generation or alteration and prohibit candidate assessment from images or appearance.
- [ ] Keep video generation behind a separate product and compliance gate after image-generation value is proven.
- [ ] Limit approved video use to human-reviewed job explainers, employer branding, recruiter training, or onboarding.
- [ ] Require captions, transcripts, provenance, moderation, disclosure, retention limits, and cost controls for generated video.
- [ ] Prohibit synthetic interviewers and candidate scoring from face, gesture, attention, emotion, appearance, or behavior.

**Exit condition:** Optional generated media has a validated communication benefit, is clearly disclosed, and remains completely outside candidate evaluation and ranking.

## Phase 8 - Release readiness

- [ ] Run full unit, integration, end-to-end, performance, and security test suites.
- [ ] Run deterministic AI invariants on every pull request and a stable provider-backed evaluation smoke set where cost and availability permit.
- [ ] Run the complete versioned AI evaluation suite nightly, before model or prompt promotion, and before production release.
- [ ] Calibrate Spring AI and custom evaluator judgments against human labels; track false passes, false failures, and drift.
- [ ] Version generation and evaluator models, prompts, retrieval settings, tools, weights, datasets, and thresholds together.
- [ ] Establish platform-thread load-test baselines for AI, JDBC, ingestion, chat, dashboard, and messaging workloads.
- [ ] Evaluate Spring Boot virtual threads in staging for blocking orchestration, with `spring.main.keep-alive` and a reversible environment setting.
- [ ] Bound PostgreSQL, OpenAI, OCR, speech, messaging, image, and video concurrency independently with timeouts, rate limits, bulkheads or semaphores.
- [ ] Keep PDF parsing, ZIP decompression, local OCR, and media encoding on bounded CPU-oriented workers.
- [ ] Monitor pinned virtual threads, carrier starvation, database-pool waits, tail latency, provider throttling, cancellation, and shutdown behavior.
- [ ] Verify backup, restore, retention, deletion, and incident procedures.
- [ ] Add model, token, latency, error, retrieval-quality, transcription-quality, media, and cost monitoring.
- [ ] Conduct recruiter acceptance testing with representative workflows.
- [ ] Document deployment, rollback, support, and model-change procedures.

**Exit condition:** Operational, security, AI-quality, and product owners approve release.

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
