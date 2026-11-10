# HR AI Recruitment Manager Specifications

This directory translates `FD - Hackathon KLX 2025.docx` into implementation-oriented Markdown.

## Document map

1. [Project context and scope](./01-project-context.md)
2. [AI-generated job offers](./02-job-offer-generation.md)
3. [CV evaluation](./03-cv-evaluation.md)
4. [CV database chat](./04-cv-database-chat.md)
5. [Interactive dashboard](./05-interactive-dashboard.md)
6. [Implementation roadmap](./06-implementation-roadmap.md)
7. [Open decisions](./07-open-decisions.md)
8. [CI/CD and VPS deployment](./08-ci-cd-deployment.md)
9. [Staging VPS provisioning and operations](./09-staging-vps-provisioning-runbook.md)
10. [Production VPS provisioning and operations](./10-production-vps-provisioning-runbook.md)
11. [Backend concurrency and virtual threads](./11-backend-concurrency-and-virtual-threads.md)
12. [Spring AI tools and controlled agent actions](./12-spring-ai-tools-and-agent-actions.md)
13. [AI evaluation testing](./13-ai-evaluation-testing.md)
14. [Spring AI observability](./14-spring-ai-observability.md)
15. [Authentication and authorization](./15-authentication-and-authorization.md)
16. [Tenant-scoped LOB read incident](./16-tenant-lob-transaction-incident.md)
17. [Cross-tab session state incident](./17-cross-tab-session-state-incident.md)
18. [Organization and tenant isolation](./tenant-isolation.md)
19. [Final authentication and security validation](./19-final-security-validation.md)
20. [Governed original CV storage](./20-original-cv-storage.md)
21. [Candidate data governance decision record](./21-candidate-data-governance.md)
22. [Contribution standards](./22-contribution-standards.md)
23. [Encrypted paired backup and restore](./23-paired-backup-and-restore.md)
24. [Candidate correction and document reprocessing](./24-candidate-correction-and-reprocessing.md)
25. [CV OCR fallback](./25-cv-ocr.md)

## Recommended delivery order

1. Confirm the unresolved product and scoring decisions.
2. Build candidate details and ingestion history, then add correction/reprocessing followed by OCR.
3. Harden the implemented job-offer workflow and complete its production controls.
4. Extend CV ingestion/evaluation with evidence and reproducibility.
5. Complete the remaining CV-chat read tools, scoped memory, dedicated message-style page, broader evaluation, and
   controlled-response localization.
6. Extend the accepted typed CV chat with editable speech-to-text input and optional text-to-speech playback.
7. Build dashboard APIs and UI from persisted recruitment data.
8. Complete a focused codebase-wide comments and Javadoc review under the contribution standards.
9. Publish reviewed backend Swagger/OpenAPI documentation after the dashboard contracts and source documentation are stable.
10. Benchmark bounded backend concurrency and Java virtual threads in staging.
11. Install and validate encrypted paired backup/restore in staging, including an isolated restoration test.
12. Install and validate paired backup/restore in production after staging acceptance.
13. Validate security, privacy, AI quality, performance, accessibility, and usability before each release.
14. Move original files to private object storage before horizontal or multi-host backend scaling.
15. Consider horizontal or multi-host backend scaling.
16. Add the selected messaging integration after web chat, web speech controls, and documented backend APIs are stable.
17. Evaluate human-reviewed employer-branding image generation, then video generation, behind separate product and compliance gates.

## Current repository snapshot

| Capability | Status | Notes |
| --- | --- | --- |
| Job-offer generation | Implemented, needs hardening | Generate, edit, approve, persist, and list are available. Direct approval, schema-constrained output, lifecycle management, and production security remain. |
| CV ingestion | Implemented, needs hardening | PDF, ZIP, and built-in imports share a guarded pipeline with extraction, duplicate detection, private original-file storage, retention metadata, fail-closed malware quarantine, per-file transactions/results, and Angular UI. Tenant-authorized original operations, scheduled expiry, legal holds, confirmed full erasure, candidate details, document-ingestion history, auditable correction, safe reprocessing, and bounded local OCR passed staging and production acceptance. Paired-backup installation and restoration acceptance follow the dashboard, stable OpenAPI contracts, and concurrency benchmark. |
| Candidate evaluation | Implemented, needs hardening | Explicit job-specific AI evaluation, eight validated metrics, weighting, persistence, result UI, and tenant-scoped current/stale history are available. Metric-level evidence, reproducibility versioning, bias testing, and the full AI evaluation suite remain. |
| CV database chat | Picker- and language-first foundations implemented | Candidate-specific imperative chat and its Angular interface passed staging and production acceptance. The indexing foundation passed Flyway V6, immediate indexing, reconciliation, and deletion-cleanup acceptance in both environments. The separately switched retrieval path enforces authenticated organization and selected-candidate filters, current source versions, bounded similarity/results, merged citations, transaction suspension during vector I/O, and safe empty/failure behavior. Staging and production accepted a calibrated `0.35` minimum similarity on 30 October 2026. The original-plus-normalized multilingual retrieval correction passed staging and production on 1 November 2026 with privacy-safe diagnostics; staging established an observed 3–4 second latency baseline. Deterministic tenant-scoped exact-name resolution, bounded partial-name discovery, and the explicit manual Angular picker passed staging and production acceptance by 2 November 2026. The explicit two-to-five candidate comparison backend passed focused staging and production acceptance by 2 November 2026, including strict bracketed citations for every selected candidate, validation, tenant isolation, role enforcement, sensitive-question refusal, and privacy-safe logs. The reusable Angular multi-selector and its incomplete-citation fallback passed staging after PRs #124 and #125, then production in release `v0.28.0`. PRs #128–#130 established the accepted language-first candidate-card path. PR #144 and the PR #146–#154 stabilization series connect that path to deterministic one-pass tool discovery and confirmed-scope preflight. Focused staging acceptance passed on 10 November 2026 at main revision `22d56c5`, including optional tenant-reauthorized approved-job context, valid candidate-only comparison, sensitive/unrelated refusal, and stale language-scope clearing while picker-first scope remains independent. Production promotion remains pending. The current workspace is the validation surface for a later dedicated message-style page. Scoped conversation memory, that dedicated page, broader evaluation, localized controlled messages, editable speech input, and optional speech playback remain. |
| Interactive dashboard | Planned | Aggregate APIs and dashboard UI are not implemented. |
| Backend Swagger/OpenAPI | Planned after dashboard and source review | Springdoc, Swagger UI, versioned OpenAPI export, endpoint examples, and contract validation will be added after dashboard APIs are stable and the codebase-wide comments/Javadoc review is complete. |
| External messaging | Planned | Telegram/WhatsApp choice and integration remain open. |
| Generated media | Optional, gated | Human-reviewed employer-branding images may be evaluated after core workflows; video follows only if value is proven. Candidate imagery, synthetic interviewers, and multimodal candidate scoring are prohibited. |
| Backend concurrency | Planned validation | Java 21 and blocking Spring MVC/JPA/AI workflows are suitable for a virtual-thread experiment, but production enablement requires staging benchmarks, downstream limits, and pinned-thread monitoring. |
| Spring AI tools | Core read-tool orchestration accepted in staging | Request-scoped deterministic candidate discovery and confirmed candidate-summary tools include approved-job requirements only after application-owned tenant authorization and explicit job confirmation. Separate authenticated/CSRF-protected discovery and confirmed-scope endpoints return application-owned workflow state; discovery returns only the same minimal tenant-scoped cards produced by its deterministic searches. The one-pass Angular flow consumes those cards directly, preserves explicit confirmation, treats the approved job as optional, and keeps grounded comparison as a separate tenant-reauthorized evidence action. Focused staging acceptance passed on 10 November 2026; production promotion and the remaining evaluation-evidence, document-status, and dashboard tools remain. The previous scope endpoint stays temporarily for cached-client compatibility, and state-changing tools still require deterministic previews, explicit confirmation, idempotency, and audits. |
| AI evaluation testing | Foundation implemented; expansion pending | Deterministic structural assertions, versioned policy/retrieval/tool datasets, opt-in provider-backed gates, Spring AI relevancy/fact-checking evaluators, multilingual cases, adversarial cases, and calibrated human review are available. Broader task metrics, current live-provider reruns after prompt/model changes, evaluation-evidence coverage, OCR/speech evaluation, and release automation remain. |
| Spring AI observability | Planned production control | Current single-host operations use Docker logs. The later performance/observability milestone will add privacy-safe Micrometer and OpenTelemetry instrumentation and evaluate Prometheus/Grafana as an open-source starting point, without exporting candidate content or placing personal or secret values in metric labels. |
| Production security | Authentication foundation accepted | Email/password login, tenant isolation, bcrypt password management, Redis-backed sessions/revocation, a three-session account limit, account/IP throttling and lockout, and tenant-scoped auditing passed the final staging and production validation suite. Malware quarantine, scheduled original expiry, legal holds, and full candidate erasure passed staging and production acceptance; paired backup governance awaits restoration acceptance. |
| CI/CD and VPS deployment | Staging and production operational | CI, immutable GHCR publication, automatic staging deployment, protected production promotion, and production smoke validation are working. Encrypted paired backup/restore is implemented; staging/production installation and restoration acceptance are scheduled after the dashboard, stable OpenAPI contracts, and concurrency benchmark. Observability and further rollback hardening remain. |
| VPS provisioning | Staging and production deployed | Staging and production are independently hardened and deployed. Production has dedicated SSH access, firewall rules, Docker, DNS, TLS, logging, Premium VPS backup, and private Object Storage preparation. Paired database/original-file backup installation, isolated restoration acceptance, and timer activation are deferred until after the dashboard, OpenAPI, and concurrency milestones; separate runbooks prevent environment-specific credentials and procedures from being mixed. |

The application uses an Angular 19 frontend, a Spring Boot 3 / Java 21 backend, PostgreSQL with the pgvector image,
Flyway versioned schema migrations, Redis-backed Spring Session, and Spring AI with OpenAI.

> This snapshot is based on the source tree at the time these Markdown files were created. Keep it updated as features are delivered.
