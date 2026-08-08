# Spring AI Tools and Controlled Agent Actions

## Objective

Use Spring AI tool calling to let the CV-chat model retrieve current authorized application data and request narrowly defined recruitment operations without granting the model direct access to repositories, databases, provider credentials, or authorization decisions.

The model may select a tool and propose structured arguments. The application remains responsible for authorization, validation, execution, confirmation, auditing, rate limits, and result minimization.

## Initial read-only tools

Implement a small, request-scoped tool set after the underlying domain services and authorization model exist:

- `searchCandidates` for bounded organization-scoped candidate discovery.
- `getCandidateProfile` for an authorized, minimized candidate view.
- `getJobRequirements` for approved comparison criteria.
- `compareCandidates` for structured comparison inputs and evidence.
- `getEvaluationEvidence` for metric-level source references and evaluation history.
- `getDocumentStatus` and `getIngestionWarnings` for CV processing state.
- `getOpenJobMetrics`, `getCandidatePipelineSummary`, and other governed dashboard aggregates.

Tools must expose domain services rather than arbitrary SQL, repository access, filesystem access, HTTP access, or generic code execution.

## Trusted context and authorization

- Resolve user identity, organization, roles, locale, and request correlation data from trusted application context, not model-generated tool arguments.
- Apply organization and record authorization inside every tool invocation even when upstream retrieval already applied a filter.
- Make only the tools permitted for the current user and conversation purpose available to each `ChatClient` request.
- Avoid globally shared default tools for privileged or context-specific operations.
- Reject unknown, cross-organization, deleted, expired, or unauthorized identifiers without revealing whether protected records exist.
- Return the minimum candidate data required and prefer identifiers, structured summaries, evidence references, and authenticated links over full CV text.

## Query and command separation

Classify tools into two categories:

1. Query tools are read-only, bounded, and may run automatically when authorized.
2. Command tools change state or communicate externally and require an explicit approval workflow.

Potential command tools include requesting OCR reprocessing, preparing a shortlist, saving a recruiter note, requesting reevaluation, preparing a candidate message, or scheduling an interview. Use the sequence:

```text
model proposes action
-> application creates a deterministic preview
-> user reviews the exact effect
-> user confirms
-> domain service executes with an idempotency key
-> application writes an audit record
```

Never expose tools that directly approve jobs, reject or hire candidates, change scoring rules, bypass consent, weaken retention, select an organization, or send messages without confirmation.

## Prompt-injection and misuse controls

CVs, chat messages, OCR text, transcripts, and external messages are untrusted inputs. A document instruction must never change tool permissions or application policy.

- Keep command tools unavailable during untrusted document-analysis prompts unless the workflow explicitly requires one.
- Enforce authorization and business invariants in deterministic services, not prompts or tool descriptions.
- Limit tool-call count, recursion depth, result count, execution time, payload size, and returned fields.
- Apply timeouts, circuit breakers, provider rate limits, bulkheads or semaphores, cancellation, and safe retry policies.
- Prevent arbitrary queries, unrestricted exports, generic URLs, shell commands, code execution, and filesystem paths.
- Test malicious inputs that attempt cross-tenant retrieval, data export, tool substitution, hidden actions, or confirmation bypass.

## Observability and privacy

- Record tool name, authenticated actor, organization, outcome, duration, result count, correlation ID, confirmation reference, and policy decision.
- Do not place CV text, prompts, transcripts, tool arguments, tool results, credentials, or unnecessary candidate data in ordinary logs or traces.
- Keep content-bearing tool telemetry disabled by default.
- Monitor tool errors, timeouts, denied calls, repeated invocation, provider throttling, and unusual result volumes.
- Apply chat, candidate, audio, and audit retention policies to corresponding tool-call metadata.

## Multichannel reuse

Typed CV chat, speech-enabled CV chat, Telegram, and WhatsApp must use the same authorized tool layer. Channel adapters may reduce the available tool set and returned detail, but they must not reimplement candidate retrieval or weaken security. External messaging should return minimized summaries and authenticated web links rather than full CV records.

## Concurrency

Tool calls that wait on JDBC, Spring AI, Object Storage, OCR, speech, or messaging providers may benefit from virtual-thread orchestration. Every scarce dependency still requires independent concurrency limits. CPU-intensive PDF, OCR, image, and video work remains on bounded workers or isolated jobs.

## Spring AI version boundary

The repository currently uses Spring AI `1.1.0`. Implement against documentation and APIs matching the pinned version. Evaluate a Spring AI 2.x upgrade separately because its preferred tool-execution lifecycle uses `ChatClient` with `ToolCallingAdvisor`, and framework behavior must not be changed implicitly during the first tool rollout.

Before an upgrade:

- Review the official migration and tool-calling notes.
- Pin compatible model and Spring AI versions.
- Run unit, integration, authorization, prompt-injection, and tool-loop regression tests.
- Verify tool resolution, schema generation, result conversion, observability, and retry behavior.

## Acceptance criteria

- Every tool delegates to an authorized, validated domain service.
- Organization and user context cannot be supplied or overridden by the model.
- Read-only tools return bounded, minimized, evidence-linked results.
- Every state-changing or external action requires an exact preview and explicit user confirmation.
- Command execution is idempotent and audited.
- Malicious CVs, transcripts, and messages cannot expand tool access or cross organization boundaries.
- Tool-call count, time, payload, recursion, concurrency, and result limits are enforced.
- Typed web chat, voice chat, and external messaging reuse the same policy-controlled tool layer.
- Sensitive tool content is absent from normal logs and traces.
- Version-matched Spring AI integration and upgrade behavior are covered by automated tests.
