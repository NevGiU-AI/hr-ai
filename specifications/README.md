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

## Recommended delivery order

1. Confirm the unresolved product and scoring decisions.
2. Harden the implemented job-offer workflow and complete its production controls.
3. Extend the implemented CV ingestion and evaluation workflow with review, governance, and reproducibility features.
4. Add governed OCR fallback, vector indexing, and evidence-backed candidate search.
5. Extend secure CV chat with editable speech-to-text input and optional text-to-speech playback.
6. Build dashboard APIs and UI from persisted recruitment data.
7. Publish reviewed backend Swagger/OpenAPI documentation after the dashboard contracts are stable.
8. Add the selected messaging integration after the web chat and documented backend APIs are stable.
9. Evaluate human-reviewed employer-branding image generation, then video generation, behind separate product and compliance gates.
10. Benchmark bounded backend concurrency and Java virtual threads in staging.
11. Validate security, privacy, AI quality, performance, accessibility, and usability before release.

## Current repository snapshot

| Capability | Status | Notes |
| --- | --- | --- |
| Job-offer generation | Implemented, needs hardening | Generate, edit, approve, persist, and list are available. Direct approval, schema-constrained output, lifecycle management, and production security remain. |
| CV ingestion | Implemented, needs hardening | PDF, ZIP, and built-in imports share a guarded pipeline with extraction, duplicate detection, per-file results, and Angular UI. OCR, original-file storage, correction, and governance remain. |
| Candidate evaluation | Implemented, needs hardening | Explicit job-specific AI evaluation, eight validated metrics, weighting, persistence, and result UI are available. Evidence, versioning, retrieval/history, and bias testing remain. |
| CV database chat | Planned | pgvector is available, but indexing, retrieval, memory, citations, editable speech input, and optional speech playback remain. |
| Interactive dashboard | Planned | Aggregate APIs and dashboard UI are not implemented. |
| Backend Swagger/OpenAPI | Planned after dashboard | Springdoc, Swagger UI, versioned OpenAPI export, endpoint examples, and contract validation will be added after dashboard APIs are stable. |
| External messaging | Planned | Telegram/WhatsApp choice and integration remain open. |
| Generated media | Optional, gated | Human-reviewed employer-branding images may be evaluated after core workflows; video follows only if value is proven. Candidate imagery, synthetic interviewers, and multimodal candidate scoring are prohibited. |
| Backend concurrency | Planned validation | Java 21 and blocking Spring MVC/JPA/AI workflows are suitable for a virtual-thread experiment, but production enablement requires staging benchmarks, downstream limits, and pinned-thread monitoring. |
| Spring AI tools | Planned with CV chat | Request-scoped read tools will support authorized retrieval, evidence, comparison, and dashboard queries. State-changing tools require deterministic previews, explicit confirmation, idempotency, and audits. |
| AI evaluation testing | Planned cross-cutting capability | Deterministic assertions, versioned golden datasets, Spring AI evaluators, task metrics, adversarial tests, and calibrated human review will gate model, prompt, retrieval, tool, OCR, and speech changes. |
| Spring AI observability | Planned production control | Privacy-safe Micrometer metrics, traces, dashboards, and alerts will cover models, tokens, cost, RAG, tools, OCR, speech, messaging, backups, and infrastructure without exporting candidate content. |
| Production security | Planned | Authentication, authorization, tenant isolation, restricted CORS, audit logging, malware scanning, and retention controls remain. |
| CI/CD and VPS deployment | Staging implemented, production in progress | CI, immutable GHCR publication, automatic staging deployment, and staging validation are implemented. Production VPS hardening and Premium backup are complete; PostgreSQL backup restoration, production GitHub protection, rollback hardening, and release validation remain. |
| VPS provisioning | Staging complete, production in progress | Staging is hardened and deployed. Production has independent SSH, firewall, Docker, DNS, logging, Premium VPS backup, and private Object Storage preparation; database backup restoration and production release controls remain. Separate runbooks prevent environment-specific credentials and procedures from being mixed. |

The application uses an Angular 19 frontend, a Spring Boot 3 / Java 21 backend, PostgreSQL with the pgvector image, and Spring AI with OpenAI.

> This snapshot is based on the source tree at the time these Markdown files were created. Keep it updated as features are delivered.
