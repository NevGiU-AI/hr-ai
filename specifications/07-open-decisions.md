# Open Decisions and Source Inconsistencies

Resolve these items before treating the functional design as implementation-ready.

## 1. Messaging channel

The source mentions WhatsApp in the product overview and one output example, but the detailed chat specification requires Telegram.

Decide:

- Telegram only
- WhatsApp only
- Both, delivered in a defined order

Recommended approach: implement a channel-neutral messaging adapter, stabilize web chat first, then add one external channel.

## 2. Composite evaluation formula

The source provides category weights but not individual metric weights. It also does not say whether AI confidence should affect candidate fit or remain a separate reliability indicator.

Decide:

- The exact weight of each metric.
- Whether weights are global defaults, per-job overrides, or both.
- Whether AI confidence changes the score or only triggers review.
- How missing metrics affect normalization.

Recommended approach: keep AI confidence outside candidate fit and use it to trigger human verification.

## 3. Employment gaps

The source says gaps longer than six months are “penalized.” This can create unfair or legally sensitive outcomes.

Decide:

- Whether the metric is scored at all.
- What counts as a gap.
- Which user-provided explanations are recognized.
- Whether it should only be a neutral review flag.

Recommended approach: flag potential gaps neutrally and exclude them from automatic ranking.

## 4. Candidate data governance

The source does not define:

- Candidate consent and lawful processing basis
- Retention and deletion periods
- Data residency
- Access roles and organization isolation
- Audit-log retention
- Candidate correction or appeal workflow

These decisions are release blockers because CVs contain personal data.

## 5. Authentication and authorization

The source assumes user identifiers but does not define authentication. Decide the identity provider, roles, tenant model, session behavior, and authorization rules for jobs, CVs, evaluations, chat, and dashboards.

The implemented tenant boundary currently maps one `organization_id` string to one organization. Before external
onboarding, decide organization creation, ownership, invitations, suspension/deletion, stable identifiers, display
names, and whether a user may belong to more than one organization. The existing `staging` and `production` identifiers
are historical backfill labels and must not become the customer-facing organization model.

## 6. “Real-time” dashboard behavior

Define an observable freshness target, such as “new evaluations appear within 10 seconds,” and select polling, server-sent events, or WebSockets accordingly.

## 7. Performance targets

Clarify:

- Whether the 30-second generation target applies to p95 or another percentile.
- Whether the 3-second dashboard target includes network time and first meaningful content.
- The dataset size and concurrent-user load used for testing.

## 8. Job-board export

The source marks export or direct posting as a future integration. Decide whether basic file export belongs in the first release and keep direct publishing out of scope until provider requirements are known.

## 9. AI quality measurement

Define:

- What “minimally edit” means for the 90% acceptance target.
- Ground-truth datasets for CV metric evaluation.
- Retrieval precision and answer-faithfulness thresholds.
- Owners and cadence for prompt/model regression review.
- Initial retrieval, groundedness, citation, OCR, transcription, latency, cost, and severity-weighted release thresholds.
- Which evaluation sets run on pull requests, nightly, before model promotion, and before release.
- Which evaluator model and prompt are used, how they are calibrated against human labels, and what disagreement requires manual review.

## 10. Multimodal provider and governance boundaries

Decide separately for OCR, speech-to-text, text-to-speech, image generation, and video generation:

- Whether processing is self-hosted or uses an approved managed provider.
- Permitted regions, subprocessors, retention behavior, and zero-data-retention requirements.
- Maximum file size, duration, latency, quality, and cost budgets.
- Whether raw audio or generated media may be persisted and for how long.
- Required consent, disclosure, provenance, moderation, and accessibility behavior.

Recommended approach: implement OCR first, add speech input and optional speech playback only after secure text CV chat exists, and keep generated images and videos behind separate evidence-based product gates. Never use voice, face, emotion, appearance, gesture, attention, personality, or behavior to score or rank candidates.

## 11. Backend concurrency and virtual threads

Define the representative workload, concurrency, downstream quotas, database connection budget, and minimum improvement required before enabling Java virtual threads in production. Virtual threads should orchestrate blocking I/O; CPU-heavy PDF, ZIP, local OCR, and media work must remain explicitly bounded. The decision must be based on staging load tests, tail latency, resource consumption, provider throttling, pinned-thread diagnostics, and graceful-shutdown behavior.

## 12. Spring AI tool calling and version strategy

Define the initial read-only tool allowlist, role-to-tool mapping, command confirmation policy, maximum tool-call loop, telemetry policy, and evidence required for tool-produced answers. Decide whether the first rollout remains on the pinned Spring AI 1.1 line or includes a separately reviewed 2.x migration. Do not combine a framework upgrade with privileged command-tool rollout without independent regression and security validation.
