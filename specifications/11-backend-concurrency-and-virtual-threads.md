# Backend Concurrency and Virtual Threads

## Objective

Use Java virtual threads to increase concurrent throughput for blocking backend workflows while keeping CPU-heavy processing and scarce downstream resources explicitly bounded.

Virtual threads are a scalability tool, not a guarantee that an individual request becomes faster. Adoption must be justified by repeatable staging measurements against the existing platform-thread baseline.

## Suitable workloads

The Spring Boot backend may use virtual threads for blocking orchestration around:

- Spring AI job generation, candidate evaluation, embeddings, and CV chat requests.
- JDBC/JPA access for jobs, candidates, evaluations, conversation memory, dashboards, and audit records.
- Object Storage, malware-scanning, managed OCR, speech-to-text, text-to-speech, image, and video API calls.
- Telegram or WhatsApp webhooks, delivery callbacks, retries, and voice-note transcription.
- Independent dashboard queries when bounded concurrency demonstrates a latency benefit.
- Per-document archive-ingestion tasks after ZIP entries have been safely read and isolated.

## Workloads that require bounded workers

Do not rely on virtual threads to accelerate prolonged CPU- or memory-intensive work such as:

- PDF parsing and ZIP decompression.
- Local OCR execution.
- Local image or video encoding.
- Large transformations or local embedding computation.

Run these operations through bounded platform-thread workers, isolated processes, or external job workers. Protect every limited downstream dependency with timeouts, rate limits, bulkheads or semaphores, and back-pressure. Virtual threads must not create unbounded access to PostgreSQL connections, OpenAI quotas, OCR capacity, messaging providers, or media-generation quotas.

## Transaction and ingestion boundaries

- Never share a `ZipInputStream`, JPA transaction, Hibernate session, mutable entity, or request-scoped mutable object between concurrent tasks.
- Read and validate archive entries before scheduling independent document work.
- Give each document its own transaction and deterministic result record.
- Preserve archive entry, expanded-size, compression-ratio, file-count, and request-size protections.
- Apply an explicit concurrency limit to OCR, persistence, embeddings, and other downstream calls.
- Make cancellation, timeout, partial failure, retry, and idempotency behavior observable.

## Spring Boot adoption

Evaluate the following configuration in a staging-only profile first:

```yaml
spring:
  threads:
    virtual:
      enabled: true
  main:
    keep-alive: true
```

Do not assume conventional executor pool-size properties still provide concurrency control when virtual threads are enabled. Use explicit resource limits for scarce dependencies.

## Validation procedure

1. Establish a platform-thread baseline for representative job generation, evaluation, ingestion, chat, dashboard, and messaging workloads.
2. Enable virtual threads in staging without changing the workload or downstream limits.
3. Compare throughput, p50/p95/p99 latency, errors, timeouts, CPU, memory, database-pool waits, downstream rate limits, and shutdown behavior.
4. Use Java Flight Recorder and JVM diagnostics to detect pinned virtual threads and carrier-thread starvation.
5. Exercise cancellation, deployment shutdown, scheduled work, and partial downstream failure.
6. Tune database connections, HTTP timeouts, semaphores, and provider concurrency independently of virtual-thread scheduling.
7. Promote the configuration only when staging shows a repeatable benefit without resource exhaustion or degraded tail latency.

## Acceptance criteria

- Staging load tests show a documented throughput or resource-efficiency improvement over the platform-thread baseline.
- Tail latency, errors, database waits, and provider throttling remain within approved limits.
- No shared transaction, stream, or mutable request state crosses concurrent task boundaries.
- CPU-heavy tasks remain bounded and cannot starve HTTP request processing.
- Pinned-thread monitoring and concurrency metrics are available to operators.
- Graceful shutdown, cancellation, timeout, retry, and partial-failure behavior are tested.
- Production enablement is reversible through environment configuration.
