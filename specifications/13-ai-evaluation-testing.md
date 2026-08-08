# AI Evaluation Testing

## Objective

Detect hallucination, unsupported claims, regressions, unsafe tool use, retrieval failures, and quality degradation through a layered evaluation system. AI-model evaluation complements deterministic assertions, task-specific metrics, security tests, and calibrated human review; it does not replace them.

## Evaluation layers

### 1. Deterministic assertions

Run deterministic checks on every pull request where relevant:

- Request, response, persistence, tool, and error schemas.
- Required and forbidden fields, value ranges, scoring formulas, and approved weights.
- Authorization, organization isolation, consent, retention, and confirmation gates.
- Citation identifiers, evidence references, tool arguments, idempotency, and audit creation.
- Timeouts, call-count limits, payload limits, error mapping, and safe fallback behavior.

### 2. Versioned offline evaluation datasets

Maintain privacy-safe, human-reviewed datasets with stable identifiers and expected outcomes for:

- Job descriptions, required constraints, missing information, and prohibited inventions.
- CVs with known evidence, extracted metadata, expected metric ranges, and counterfactual variants.
- Native and scanned documents with reference text and page locations.
- Candidate-search, comparison, ambiguity, no-result, refusal, and citation cases.
- Prompt-injection, cross-organization, data-export, tool-selection, and confirmation-bypass attacks.
- Speech recordings with reference transcripts and technical named entities.
- Approved and rejected generated-media examples with moderation, provenance, disclosure, and accessibility expectations.

Do not use real candidate personal data in repository test fixtures. Govern any restricted evaluation dataset outside Git with the same access, retention, and deletion controls as production candidate data.

### 3. AI-assisted evaluators

Use version-matched Spring AI `Evaluator` implementations or application-specific evaluators for semantic properties such as:

- Relevance to the user request and supplied context.
- Factual support and groundedness.
- Completeness and citation faithfulness.
- Clarity, inclusivity, refusal quality, and uncertainty communication.
- Tool-selection and final-answer consistency with tool results.

The evaluator model may differ from the generation model. Pin evaluator model, snapshot, prompt, temperature, dataset, and thresholds. Do not expose evaluator prompts or results containing candidate data through ordinary logs.

### 4. Human calibration

Recruiters, product owners, and appropriate risk reviewers must label representative benchmark samples and periodically compare their judgments with automated evaluators. Track agreement, false passes, false failures, borderline cases, and evaluator drift. Human review remains mandatory for new high-severity failures and sensitive fairness or legal conclusions.

## Capability-specific evaluation

### Job generation

- Validate structured output deterministically.
- Detect invented salary, benefits, qualifications, employer facts, or locations.
- Measure constraint adherence, missing-information recall, clarity, inclusivity, latency, and recruiter edit or acceptance rates.

### CV ingestion, OCR, and metadata

- Measure character error rate, word error rate, named-entity accuracy, page-reference accuracy, and native-versus-OCR routing accuracy.
- Test names, emails, dates, employers, skills, qualifications, tables, multiple languages, poor scans, and reprocessing behavior.
- Treat exact reference comparison as authoritative; use semantic evaluation only as a supplement.

### Candidate evaluation

- Verify every explanation and score is supported by the CV and approved job criteria.
- Detect invented experience, education, employers, dates, skills, and achievements.
- Measure score consistency, evidence completeness, confidence calibration, and ranking stability under irrelevant formatting changes.
- Use counterfactual tests to ensure irrelevant demographic or presentation changes do not alter results.
- Keep the approved deterministic scoring formula and human-reviewed rubric authoritative; an evaluator model must not certify fairness or decide candidate suitability.

### RAG and CV chat

- Measure retrieval recall at `k`, precision, ranking quality, answer relevance, groundedness, citation correctness, completeness, refusal quality, and latency.
- Verify ambiguous requests trigger clarification and unsupported requests do not produce guessed answers.
- Prove prompt injection cannot override policy, expose another organization, or invoke unavailable tools.

### Spring AI tools

- Test correct tool selection, structured arguments, trusted context injection, authorization, result minimization, and final-answer faithfulness to tool results.
- Verify query prompts cannot call command tools and command tools cannot execute without an exact preview and explicit confirmation.
- Test loop, recursion, call-count, time, payload, result, concurrency, idempotency, and audit limits.

### Speech

- Speech-to-text: measure word error rate, named-entity and technical-term accuracy, language coverage, correction rate, latency, and cost.
- Text-to-speech: assess intelligibility, pronunciation, accessibility controls, interruption behavior, latency, and sensitive-content playback restrictions.
- Never evaluate or infer identity, accent quality, emotion, personality, confidence, or candidate fit from voice characteristics.

### Generated images and video

- Evaluate prompt adherence, moderation, brand controls, provenance, disclosure, captions, transcripts, accessibility, and absence of candidate data or likeness.
- Require human approval before publication; automated evaluation cannot authorize media release.

## CI and release strategy

- Run deterministic unit, schema, authorization, confirmation, and security tests on every pull request.
- Run a small, stable AI-evaluation smoke set on pull requests when provider access and cost permit.
- Run the complete live-model evaluation suite nightly, before model or prompt promotion, and before a production release.
- Compare aggregate scores and severity-weighted regressions rather than exact wording.
- Quarantine provider outages separately from product-quality failures, but never convert an evaluation failure into a silent pass.
- Store privacy-safe evaluation summaries, model and prompt versions, thresholds, and failure identifiers as CI artifacts.
- Require reviewed threshold changes through the same pull-request process as prompts and scoring rules.

## Initial release gates

Exact thresholds remain an approved product decision and must be calibrated against the benchmark. At minimum:

- All schema, authorization, organization-isolation, consent, and confirmation tests pass.
- No critical fixture contains an invented employer, degree, date, qualification, or candidate fact.
- Every state-changing tool test proves preview, confirmation, idempotency, and audit behavior.
- Retrieval, groundedness, citation, OCR, transcription, latency, and cost metrics meet documented thresholds without an unapproved regression.
- New high-severity failures receive human review before release.

## Spring AI version boundary

The repository currently pins Spring AI `1.1.0`, while current reference documentation also covers Spring AI 2.x. Build evaluation datasets, metric records, and release reports behind application-owned interfaces. Verify evaluator availability and signatures against the pinned version, consider an independently tested upgrade within the 1.1 line, and evaluate 2.x separately alongside the planned tool-calling migration.

## Acceptance criteria

- Every AI capability has deterministic invariants and a versioned evaluation dataset.
- Models, prompts, tools, retrieval settings, scoring weights, datasets, evaluator prompts, and thresholds are versioned together.
- Evaluation reports distinguish application defects, model-quality failures, policy failures, and provider outages.
- Automated evaluator judgments are calibrated against human labels and monitored for drift.
- Security, authorization, privacy, and fairness conclusions never depend on an LLM judge alone.
- Model, prompt, retrieval, OCR, tool, or provider changes cannot reach production without their required regression suite and reviewed quality gate.
