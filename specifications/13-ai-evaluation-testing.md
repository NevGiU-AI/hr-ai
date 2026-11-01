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
- Evaluate the pre-retrieval question policy in English, French, and Portuguese with sensitive, unrelated, legitimate
  recruitment, and near-boundary pairs. Include religion versus religious-sector work, political affiliation versus
  professional public-policy experience, disability inference versus accessibility-engineering experience, paraphrases,
  misspellings, and adversarial instructions. Track false refusals, missed refusals, latency, cost, and drift; never treat
  the classifier as an authorization boundary.

#### Implemented question-policy evaluation foundation

The first versioned dataset is stored at
`backend/src/test/resources/ai-evaluations/cv-chat-question-policy-v1.json`. It contains synthetic, human-reviewed
English, French, and Portuguese cases for legitimate professional questions, protected-characteristic requests,
unrelated requests, near-boundary wording, and adversarial compound instructions. The staging false refusal
`Is it a Java engineer ?` is a permanent regression case expected to produce `JOB_RELEVANT`.

`CvChatQuestionPolicyEvaluationDatasetTest` runs on every normal test execution and verifies dataset version, unique
identifiers, language/category metadata, label coverage, non-empty content, and preservation of the regression case.
`CvChatQuestionPolicyProviderEvaluationTest` is deliberately opt-in because it makes real provider calls, incurs cost,
and can fail independently during a provider outage. It evaluates every case with temperature `0`, reports only
synthetic case identifiers and metadata on failure, and never prints the questions.

Near-boundary cases distinguish a candidate's personal characteristic from professional context. For example, asking
whether someone worked for a religious organization is job-related employer history; it must not be treated as evidence
of, or a request for, that person's religion. The same rule applies to political, healthcare, disability, accessibility,
and other potentially sensitive sectors: classify the requested professional evidence without inferring a private trait.

Run the provider-backed policy gate from `backend` before changing or promoting the policy prompt or model:

PowerShell:

```powershell
$env:RUN_AI_EVALUATIONS = "true"
$env:OPENAI_API_KEY = "replace-with-the-evaluation-key"
$env:AI_EVALUATION_CHAT_MODEL = "gpt-4o-mini"
mvn -B "-Dtest=CvChatQuestionPolicyProviderEvaluationTest" test
Remove-Item Env:RUN_AI_EVALUATIONS, Env:OPENAI_API_KEY, Env:AI_EVALUATION_CHAT_MODEL
```

Bash:

```bash
RUN_AI_EVALUATIONS=true \
OPENAI_API_KEY='replace-with-the-evaluation-key' \
AI_EVALUATION_CHAT_MODEL='gpt-4o-mini' \
mvn -B -Dtest=CvChatQuestionPolicyProviderEvaluationTest test
```

`AI_EVALUATION_CHAT_MODEL` defaults to `gpt-4o-mini` when omitted. These are test-process variables, not deployment
configuration, so they must not be added to staging or production `.env` files. Provider-backed relevance,
groundedness, fact-checking, citation, and retrieval-quality evaluation remains a later CV-chat suite; this first slice
only measures question-policy classification.

#### Staging acceptance — 30 October 2026

The question-policy evaluation and corrected prompt passed staging acceptance after PR #103 was merged at main revision
`87821a4`. The provider-backed `gpt-4o-mini` run classified all 21 version `1.0.0` cases correctly. Focused UI checks
then confirmed a grounded cited answer for the previously refused terse Java-engineer question and pre-retrieval
`SENSITIVE` and `OUT_OF_SCOPE` decisions for religion and weather questions. The refusal logs disclosed neither question
text nor CV content.

The same exploratory run identified, but did not accept, broader multilingual retrieval and generation quality. An
English KLx question returned the expected `[CV-61]` evidence; semantically similar French formulations varied between
retrieval success and controlled insufficient evidence, and a successful French query did not consistently receive a
French answer on repetition. The next dataset must therefore record expected documents, required concepts, citations,
question/answer language, paraphrase families, and repeated-run stability. These are retrieval and generated-answer
evaluation requirements, not failures of the accepted pre-retrieval policy classifier. Production policy acceptance
was completed later the same day.

#### Production acceptance — 30 October 2026

Release `v0.22.0` at main revision `6d96d2b` passed the focused production question-policy checks without any new
environment, schema, Compose, or frontend configuration. Professional Java-engineer and Angular-project questions
continued to grounded cited answers. Religion and weather requests returned the fixed refusal without citations and
produced privacy-safe `PRE_RETRIEVAL_POLICY` decisions with the expected `SENSITIVE` and `OUT_OF_SCOPE` labels. Logs
contained no question or CV content. The 21-case provider-backed evaluation had already passed before promotion and was
not converted into a production runtime dependency.

Production policy acceptance does not close the broader evaluation milestone. Multilingual paraphrase retrieval,
response-language consistency, repeated-run stability, expected-document recall, groundedness, fact-checking, and
citation-quality gates remain the immediate next evaluation slice.

#### Implemented multilingual retrieval and generated-answer harness

The versioned synthetic dataset at
`backend/src/test/resources/ai-evaluations/cv-chat-multilingual-retrieval-v1.json` contains tenant-scoped source
documents plus English, French, and Portuguese paraphrases for KLx experience and the Angular project. It includes a
cross-tenant decoy with deliberately overlapping vocabulary so metadata filtering is exercised rather than inferred
from retrieval quality.

`CvChatMultilingualEvaluationDatasetTest` runs on every normal build and validates version, identifiers, languages,
documents, expected references, required evidence terms, language markers, and the bounded repeated-answer subset.
`CvChatMultilingualProviderEvaluationTest` is opt-in and uses the actual Spring AI 1.1 APIs pinned by this repository:

- OpenAI `text-embedding-3-small` with 1,536 dimensions and `SimpleVectorStore` for real multilingual similarity;
- the accepted `0.35` threshold, six-result limit, and mandatory organization/candidate metadata filters;
- deterministic expected-document, required-evidence, cross-tenant, and citation assertions;
- two answer attempts for representative English, French, and Portuguese cases;
- deterministic response-language markers; and
- Spring AI `RelevancyEvaluator` and `FactCheckingEvaluator` judgments for every repeated answer.

The multilingual-recall correction makes the fixture's KLx evidence resemble a real bounded chunk by surrounding the
target fact with unrelated but plausible professional material. For every case, the provider test obtains a constrained
English retrieval query, searches both the original and normalized wording with the same tenant/candidate filter,
deduplicates matches, retains the strongest score, reapplies the accepted `0.35` threshold, and limits the union to six
documents. Its output exposes only the fixture case ID, original top score, normalized top score, and accepted-match
count. It never prints the question or evidence. The generated answer still receives the original-language question.

Production runtime diagnostics follow the same privacy boundary: identifiers, counts, and the top score are observable;
questions, normalized queries, excerpts, embeddings, and provider messages are not. Deterministic tests cover bounded
parsing, original-plus-normalized retrieval, tenant/current-version filtering, and fallback to the original query when
the optional normalization call fails. Provider evaluation and environment acceptance remain mandatory because unit
tests cannot establish multilingual embedding quality, latency, or cost.

The authorized live-provider run passed on 30 October 2026 in 27.91 seconds of test time. Results were:

| Case | Original top score | Normalized top score | Accepted matches |
| --- | ---: | ---: | ---: |
| `en-klx` | `0.4702` | `0.5185` | 1 |
| `fr-klx-direct` | `0.4561` | `0.5451` | 1 |
| `fr-klx-experience` | `0.4621` | `0.5887` | 1 |
| `pt-klx` | `0.4619` | `0.4978` | 1 |
| `en-angular` | `0.4574` | `0.4239` | 1 |
| `fr-angular` | `0.4005` | `0.4239` | 1 |
| `pt-angular` | `0.4247` | `0.4239` | 1 |

All cases retained the expected tenant/candidate-filtered document and required evidence. Representative generated
answers retained their expected language and citations and passed Spring AI relevancy and fact-checking evaluation.
The score distribution proves that constrained English normalization materially improves the known French KLx cases
but is not universally superior, so the runtime must preserve original retrieval and merge both result sets. The run
authorizes staging evaluation; it does not by itself close the real-CV environment limitation.

Run this focused provider gate from `backend`:

PowerShell:

```powershell
$env:RUN_AI_EVALUATIONS = "true"
$env:OPENAI_API_KEY = "replace-with-the-evaluation-key"
$env:AI_EVALUATION_CHAT_MODEL = "gpt-4o-mini"
$env:AI_EVALUATION_JUDGE_MODEL = "gpt-4o-mini"
$env:AI_EVALUATION_EMBEDDING_MODEL = "text-embedding-3-small"
$env:AI_EVALUATION_SIMILARITY_THRESHOLD = "0.35"
mvn -B "-Dtest=CvChatMultilingualProviderEvaluationTest" test
Remove-Item Env:RUN_AI_EVALUATIONS
Remove-Item Env:OPENAI_API_KEY
Remove-Item Env:AI_EVALUATION_CHAT_MODEL
Remove-Item Env:AI_EVALUATION_JUDGE_MODEL
Remove-Item Env:AI_EVALUATION_EMBEDDING_MODEL
Remove-Item Env:AI_EVALUATION_SIMILARITY_THRESHOLD
```

Bash:

```bash
RUN_AI_EVALUATIONS=true \
OPENAI_API_KEY='replace-with-the-evaluation-key' \
AI_EVALUATION_CHAT_MODEL='gpt-4o-mini' \
AI_EVALUATION_JUDGE_MODEL='gpt-4o-mini' \
AI_EVALUATION_EMBEDDING_MODEL='text-embedding-3-small' \
AI_EVALUATION_SIMILARITY_THRESHOLD='0.35' \
mvn -B -Dtest=CvChatMultilingualProviderEvaluationTest test
```

These test-process variables must not be added to deployment `.env` files. Evaluator passes are secondary semantic
signals: authorization, metadata isolation, expected references, citations, and language checks remain deterministic.
The same judge model is an economical initial default, but future calibration should compare an independently selected
judge against human labels and record false passes/failures.

The focused live-provider gate passed on 30 October 2026 with `gpt-4o-mini`, `text-embedding-3-small`, and the accepted
`0.35` threshold. All seven multilingual retrieval cases found the expected tenant/candidate-filtered document and
required evidence without returning the cross-tenant decoy. The representative English, French, and Portuguese answer
cases each passed twice with the expected citation and language markers, and Spring AI relevancy and fact-checking
judgments passed for every generated answer. The run completed in 28.43 seconds. Byte Buddy/JDK `Unsafe` deprecation
warnings came from test instrumentation and did not indicate an evaluation failure. Staging and production environment
acceptance remain pending.

#### Multilingual staging acceptance — 30 October 2026

PR #106 at main revision `d46f435` passed its scoped staging checks. English answers remained grounded and cited;
repeated French and Portuguese grounded answers preserved their question language; sensitive/unrelated refusal policy,
tenant-scoped citations, privacy-safe logs, and backend health remained correct. These results complement the earlier
synthetic provider gate rather than replacing it.

The real-CV French paraphrase `Sur quoi a-t-il travaillé chez KLx ?` remained below the accepted retrieval boundary and
returned controlled insufficient evidence, while the English equivalent and another French formulation retrieved the
expected document. This is an accepted safe failure for PR #106's language-preservation scope, not evidence that
multilingual retrieval is complete. Preserve the pair as a future expected-document recall fixture and measure
similarity/query normalization or translation before changing the global threshold. Application-owned safe messages
and their Angular explanatory text remain English; structured outcomes and deterministic EN/FR/PT localization are
scheduled immediately before dashboard implementation. Production acceptance followed later the same day.

#### Multilingual production acceptance — 30 October 2026

Release `v0.23.0` at main revision `083b9c4` passed the same scoped checks in production. English grounded answers
remained correct, repeated grounded French and Portuguese responses preserved their question language, authorized
citations remained candidate-scoped, policy refusals remained correct, and backend health plus privacy-safe logs showed
no regression. The release required no new environment, Flyway, database, Compose, or frontend configuration.

This acceptance does not reclassify the real-CV `Sur quoi a-t-il travaillé chez KLx ?` result as successful retrieval;
it remains a safe controlled insufficient-evidence case and a required future expected-document recall fixture.
Application-owned safe-message and Angular-label localization also remains deferred until immediately before the
dashboard. Production acceptance therefore closes PR #106's language-preservation scope, not the broader multilingual
retrieval or localization milestones.

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
- Correlate privacy-safe model, prompt, retrieval, tool, scoring, dataset, and threshold versions with operational latency, error, token, cost, and throttling observations; never copy production candidate content automatically into evaluation fixtures.

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
