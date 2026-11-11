# Spring AI Observability

## Objective

Provide privacy-safe metrics, traces, logs, dashboards, and alerts for AI and recruitment workflows so operators can diagnose latency, errors, concurrency, provider throttling, token usage, cost, retrieval behavior, tool execution, and quality regressions without exposing candidate data.

Observability answers what happened in a running system. AI evaluation testing separately determines whether generated content is relevant, grounded, safe, and acceptable. Production observations may identify scenarios for controlled offline evaluation, but production candidate content must not be copied automatically into evaluation fixtures.

## Current operational decision

The current single-host Docker Compose deployments continue to use targeted `docker compose logs` inspection. This is
appropriate while each environment has one backend host, service volume is manageable, and incidents can be diagnosed
manually. Container logs are nevertheless node-local, affected by rotation and replacement, difficult to search over
long periods, and insufficient for correlating work across future replicas.

Do **not** deploy Prometheus, Grafana, an OpenTelemetry Collector, Elastic/Kibana, or Dynatrace during the current
feature slice. Introduce centralized observability during the planned performance and observability milestone, before
or alongside horizontal or multi-host scaling. Adoption becomes justified when logs span multiple hosts or replicas,
manual incident investigation becomes ineffective, automatic alerting is required, or approved availability and
latency objectives must be measured continuously.

Prometheus plus Grafana is a reasonable open-source starting point for that future milestone. Expose only privacy-safe
aggregate metrics: never place CV content, candidate names, email addresses, recruiter questions, prompts, model
responses, filenames, API keys, session identifiers, or other personal or secret values in metric labels.

## Terminology and signal roles

**Telemetry** is the operational data emitted by a running application. The primary signals are:

- **Metrics:** numerical measurements over time, such as request count, error rate, active work, memory use, and p50,
  p95, or p99 latency.
- **Logs:** timestamped records of individual events, such as a privacy-safe provider timeout or indexing failure.
- **Traces:** the path and duration of one request across application stages and dependencies.

For example, a future candidate-comparison trace could show:

```text
POST /api/chat/candidates/compare        6.2 s
|- authorization                         8 ms
|- candidate lookup                     32 ms
|- semantic retrieval                  410 ms
|- question-policy model call          1.1 s
|- comparison model call               4.5 s
`- response serialization               9 ms
```

This reveals where time was spent without recording the question, CV evidence, model answer, or candidate identity.
OpenTelemetry defines vendor-neutral generation, collection, processing, and export of traces, metrics, and logs; it is
not itself the long-term storage or dashboard backend. An optional OpenTelemetry Collector can receive signals, apply
approved filtering or redaction, batch them, and export them to one or more approved systems. See the official
[OpenTelemetry overview](https://opentelemetry.io/docs/what-is-opentelemetry/) and
[signal definitions](https://opentelemetry.io/docs/concepts/signals/).

## Tool responsibilities and future options

| Component | Primary responsibility | Important boundary |
|---|---|---|
| Docker Compose logs | Direct inspection of recent logs on the current host | Manual, node-local, and unsuitable as the final multi-host history |
| Micrometer / Spring observations | Application-facing metrics and observation API | Instrumentation, not the final storage or dashboard |
| OpenTelemetry | Vendor-neutral instrumentation, context propagation, collection, processing, and export | Not an observability backend by itself |
| Prometheus | Scrape and store numerical time-series metrics and evaluate monitoring rules | Not a detailed log or security-audit store |
| Grafana | Query approved data sources and present dashboards, exploration, and alerts | Requires underlying data sources such as Prometheus, Loki, Tempo, or Elasticsearch |
| Elastic / Kibana | Centralize, search, correlate, visualize, and alert on ingested logs and other signals | Requires an ingestion pipeline, storage, retention, access, and redaction controls |
| Dynatrace | Integrated commercial application-performance monitoring, metrics, traces, logs, dependencies, dashboards, and alerts | Adds agent, licensing, governance, and vendor-dependency decisions |

Prometheus stores labeled metric samples as time series and normally scrapes instrumented endpoints; Grafana can query
Prometheus and other stores to visualize metrics, logs, and traces. See the official
[Prometheus overview](https://prometheus.io/docs/introduction/overview/) and
[Grafana data-source documentation](https://grafana.com/docs/grafana/latest/datasources/). If centralized log search is
selected, Kibana is the Elastic visualization and investigation interface, while Elasticsearch and an approved agent,
collector, or pipeline provide ingestion and storage. See the
[Elastic logs documentation](https://www.elastic.co/docs/solutions/observability/logs).

A possible open-source architecture is:

```text
Spring Boot
  |- Micrometer metrics ------------------------> Prometheus --+
  |- OpenTelemetry traces -> Collector -> Tempo/Jaeger --------+--> Grafana
  `- structured logs ------> Collector -> Loki/Elasticsearch --+
```

The instrumentation should remain backend-neutral so a later decision can select this stack, Elastic, Dynatrace, or
another approved platform without rewriting domain logic. Security audit events remain separate, access-controlled
application records; observability logs do not replace the audit trail.

## Framework coverage

Use version-matched Spring AI observations where supported for:

- `ChatClient` calls and streams.
- Chat advisors and orchestration stages.
- Chat and evaluation model calls.
- Embedding model calls.
- Spring AI tool execution.
- pgvector and other vector-store operations.
- Approved image-model operations.

Add application-owned observations for OCR, speech, messaging, video providers, background jobs, domain services, confirmation workflows, and evaluation-suite results.

## Privacy defaults

Keep every content-bearing observation disabled in production and production-like staging:

- Chat prompts and completions.
- Tool arguments and results.
- Vector queries, filters, and returned documents.
- Image prompts.
- CV text, OCR text, chunks, transcripts, messages, generated media, and evaluation evidence.
- Authentication tokens, provider credentials, database secrets, and signed URLs.

Do not use candidate IDs, job IDs, user IDs, organization IDs, conversation IDs, filenames, email addresses, error messages, prompts, or arbitrary URLs as metric labels. Use opaque correlation and trace identifiers for controlled diagnosis. Any break-glass content capture requires explicit approval, an isolated non-production reproduction, redaction, access logging, and defined deletion; do not enable global production prompt logging for troubleshooting.

## Metrics

Use low-cardinality labels such as environment, component, operation, model, provider, outcome, tool name, and error category.

### AI and model operations

- Invocation and active-request count.
- p50, p95, and p99 latency.
- Timeout, cancellation, provider, parse, and policy-error counts.
- Provider throttling and retry count.
- Input, output, cached, and total token usage where supported.
- Estimated cost derived from versioned pricing configuration.
- Requested model, response model, finish reason, and stream mode where privacy-safe.

### RAG, embeddings, and vector storage

- Embedding latency, errors, tokens, and batch size.
- Vector add, delete, and query count, active operations, latency, and errors.
- Bounded `topK`, similarity-threshold version, retrieved-document count, and no-result rate.
- Retrieval, reranking, generation, and citation-assembly stage latency.

Never export query text, vector response documents, candidate filters, CV chunks, or embedding input.

### Spring AI tools

- Tool name, invocation count, duration, outcome, denial, timeout, retry, and result count.
- Confirmation required, approved, rejected, expired, and idempotency-conflict counts.
- Tool-loop and call-limit violations.

Do not export tool arguments or results. Tool audits remain separate, access-controlled records with minimized structured metadata.

### Recruitment workflows

- Job generation success, failure, invalid response, parsing error, latency, model version, approval rate, and edit rate.
- CV upload, duplicate, rejection, native extraction, OCR fallback, `NEEDS_REVIEW`, reprocessing, and processing latency.
- Candidate evaluation success, invalid score, missing evidence, model/rubric version, manual review, and latency.
- CV-chat request, clarification, no-result, citation coverage, refusal, tool calls, latency, token usage, and cost.
- Speech transcription/playback latency, failure, correction, deletion, and provider-throttling outcomes without audio or transcript content.
- Telegram/WhatsApp webhook, authorization, delivery, retry, and failure outcomes without message content.
- Generated-media moderation, approval, publication, failure, duration, and cost outcomes without prompts or media payloads.

Business-quality aggregates require approved access and must not turn candidate attributes or scores into unrestricted operational telemetry.

## Tracing

Propagate a single opaque trace across:

```text
HTTP or messaging request
-> authentication and authorization
-> chat orchestration or domain service
-> advisor and retrieval
-> pgvector or JDBC
-> model and tool calls
-> confirmation and audit
-> response
```

Record stage names, duration, outcome, retry, model/tool version, and error category. Do not record candidate content or raw identifiers. Validate context propagation across virtual threads, asynchronous jobs, scheduled tasks, streaming operations, and provider callbacks.

## Logging

Use structured events with event code, environment, component, safe error category, trace ID, and operational outcome. Logs may describe provider timeout, tool denial, OCR failure, backup failure, deployment, certificate, or telemetry-export events, but must not contain CV text, prompts, completions, transcripts, tool payloads, candidate messages, credentials, or unnecessary personal data.

## Dashboards and alerts

Initial dashboards should show:

- HTTP, database, model, embedding, vector, tool, OCR, speech, messaging, and background-job health.
- Request volume, active work, latency percentiles, errors, retries, throttling, tokens, and estimated cost.
- PostgreSQL pool waits, queue or semaphore saturation, virtual-thread pinning signals, CPU, memory, disk, and container health.
- Backup completion, age of latest successful backup, restore-test status, and telemetry-export health.

Alert on sustained or repeated conditions rather than individual user-level events:

- AI or tool error-rate and p95 latency breaches.
- Token or estimated-cost anomaly.
- Provider throttling, timeout, or circuit-breaker opening.
- Tool denial, loop, confirmation-bypass, or unusual result-volume anomaly.
- Vector-query degradation or abnormal no-result rate.
- OCR, speech, messaging, or media-provider failure spike.
- Database-pool waits, disk pressure, container failure, backup failure, certificate failure, or telemetry-export interruption.

Alert payloads must not include candidate identifiers or content.

## Collection and access

- Keep Actuator operational endpoints private and never expose Prometheus, environment, configuration, loggers, heap dumps, or traces through public Caddy routes.
- Export metrics, traces, and logs through authenticated, encrypted channels to approved off-server systems.
- Separate staging and production projects, credentials, access policies, dashboards, retention, and alert routing.
- Document authorized operators, support access, incident export, redaction, retention, deletion, and review cadence.
- Monitor the observability pipeline itself and alert when telemetry unexpectedly stops.

## AI evaluation correlation

Attach privacy-safe versions and outcomes needed to investigate regressions:

- Generation and evaluator model version.
- Prompt and evaluator-prompt version.
- Retrieval, chunking, embedding, reranking, and citation version.
- Tool-set, policy, scoring, and weight version.
- OCR, speech, messaging, and media-provider version.
- Evaluation-dataset and threshold version in offline reports.

This supports analysis of whether a prompt, model, retrieval, tool, concurrency, or provider change correlates with latency, cost, error, groundedness, or citation regressions without storing production content in telemetry.

## Adoption sequence

1. Approve telemetry classification, redaction, access, retention, and incident-export policy.
2. Add Micrometer metrics and OpenTelemetry-compatible tracing in staging.
3. Verify all Spring AI and custom content-bearing observations remain disabled.
4. Add model, token, latency, error, active-request, database, and infrastructure dashboards.
5. Add RAG, vector, tool, OCR, speech, messaging, backup, and evaluation-run observations as those capabilities arrive.
6. Test trace propagation with virtual threads, streaming, background jobs, and callbacks.
7. Configure encrypted off-server collection with separate staging and production access.
8. Perform redaction and access-control validation using synthetic data.
9. Add alert routing and an incident runbook.
10. Promote production telemetry only after privacy and operational review.

## Spring AI version boundary

The repository currently pins Spring AI `2.0.2`. Verify observation names, supported components, Micrometer conventions,
configuration properties, and streaming context behavior against that exact version. Validate redaction, cardinality,
dashboard, and alert behavior independently before enabling additional production telemetry.

## Acceptance criteria

- AI, vector, tool, OCR, speech, messaging, background-job, and infrastructure operations have privacy-safe metrics and trace correlation.
- Prompts, completions, CV data, transcripts, tool content, vector content, credentials, and personal identifiers are absent from ordinary telemetry.
- Metric labels have bounded cardinality and do not contain record or user identifiers.
- Dashboards expose volume, active work, latency, errors, retries, throttling, tokens, cost, saturation, and backup state.
- Alerts are actionable, routed, tested, and contain no candidate data.
- Actuator and telemetry endpoints are private.
- Off-server collection is encrypted, access-controlled, separated by environment, retained according to policy, and monitored for failure.
- Virtual-thread, asynchronous, scheduled, streaming, and callback trace propagation is tested.
- Operators can diagnose a synthetic incident from trace and event identifiers without accessing candidate content.
