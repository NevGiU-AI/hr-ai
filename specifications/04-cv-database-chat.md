# CV Database Chat and Prompting

## Objective

Allow authenticated HR users to retrieve, summarize, filter, and compare indexed candidates using natural-language queries.

## Implemented foundation

The first backend slice supports an imperative question about one explicitly selected candidate:

- `POST /api/chat/candidates/{candidateId}` accepts a validated text question.
- `ADMIN`, `RECRUITER`, and `REVIEWER` may call it; `READ_ONLY` may not.
- The authenticated principal supplies the organization. The model cannot choose or override it, and missing and
  cross-organization candidate identifiers both return `404`.
- A dedicated question-policy model classifies the untrusted recruiter question as `JOB_RELEVANT`, `OUT_OF_SCOPE`, or
  `SENSITIVE` before any candidate evidence is retrieved. Only `JOB_RELEVANT` continues to semantic search; both other
  decisions become the same fixed public refusal without citations.
- The backend verifies the candidate and current document in a read-only transaction. Semantic search suspends that
  JPA transaction while it embeds the question and queries pgvector, and the later chat-model call runs after evidence
  loading has completed; slow provider calls therefore do not retain relational transactions or connections.
- OCR documents contribute bounded page evidence such as `[CV-12-P2]`. Native extraction contributes document-level
  evidence such as `[CV-12]`.
- Every returned answer must contain at least one supplied reference. Unknown, invented, or absent citations cause a
  stable `502` response instead of returning an ungrounded answer. The only exceptions are exact, model-internal
  `INSUFFICIENT_EVIDENCE` and `OUT_OF_SCOPE` control results. Java replaces those markers with fixed safe wording and
  an empty citation list; arbitrary uncited model prose remains invalid.
- Grounded answers are instructed to preserve the recruiter question's language even when the authorized CV evidence
  uses another language. The answer call uses temperature `0` to reduce avoidable wording/language variation; provider
  determinism is still measured through repeated evaluation rather than assumed.
- Evidence/page and excerpt limits are configurable with `CV_CHAT_MAX_EVIDENCE_PAGES` and
  `CV_CHAT_MAX_EXCERPT_CHARACTERS`; deployment defaults are `10` and `1200`. Semantic retrieval adds an independent
  rollout switch plus result/relevance bounds described below.
- The Angular candidate workspace exposes this bounded endpoint only to `ADMIN`, `RECRUITER`, and `REVIEWER`. It
  validates and submits one question for the selected candidate, displays progress and structured retryable failures,
  distinguishes fixed safe responses from evidence-backed answers, and links each citation to its document card.

The candidate-specific endpoint can consume vector retrieval when its separate environment switch is enabled. The
application also provides deterministic candidate discovery, explicit picker-first comparison, and the Angular
language-first orchestration boundary described below. Persisted conversation memory, the dedicated message-style
chat page, streaming, and speech controls remain explicit roadmap work and must not be inferred from the current
request-scoped chat interfaces.

### Pre-retrieval question policy

Question policy is intentionally separate from evidence availability. A religion, political-affiliation, health, or
other sensitive-trait request is refused before vector search, while a legitimate question about professional political,
public-policy, accessibility, or healthcare experience may proceed. A permitted job-relevant question with no matching
CV evidence still receives the separate controlled insufficient-evidence response.

The classifier sees only the normalized question and must return exactly one application-owned label. Blank, arbitrary,
or failed provider output fails closed with a stable `502`; logs contain candidate/organization identifiers and failure
type but never the question or provider message. Classification uses temperature `0` and explicit examples for short,
yes/no, and grammatically imperfect recruiter questions so wording such as `Is it a Java engineer?` remains
`JOB_RELEVANT`. Sensitive intent still takes precedence when a question combines professional wording with a request
for a protected characteristic. Privacy-safe refusal logs distinguish `PRE_RETRIEVAL_POLICY` from
`ANSWER_MODEL_POLICY`, allowing operators to identify the refusing layer without recording question or CV content.
The answer model retains its own `OUT_OF_SCOPE` handling as a defense-in-depth layer after retrieval. Authorization,
tenant filtering, and citation enforcement remain deterministic application controls and never depend on either model.

This placement follows OWASP's model-based guardrail guidance, which describes input screening before the primary model,
warns that keyword filters miss paraphrases and other languages, and explicitly notes that every guardrail call adds
latency and cost. OWASP also advises treating user and retrieved content as untrusted and keeping authorization outside
the model. GDPR Article 5's purpose-limitation and data-minimisation principles support avoiding candidate-data retrieval
for a question already classified as prohibited. The NIST Generative AI Profile supports the required recurring
adversarial testing rather than treating one prompt or model as a permanent security boundary.

Sources:

- [OWASP LLM Prompt Injection Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/LLM_Prompt_Injection_Prevention_Cheat_Sheet.html)
- [OWASP AI Agent Security Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/AI_Agent_Security_Cheat_Sheet.html)
- [NIST AI RMF Generative AI Profile](https://nvlpubs.nist.gov/nistpubs/ai/NIST.AI.600-1.pdf)
- [GDPR Article 5](https://eur-lex.europa.eu/eli/reg/2016/679/oj)

## Semantic-indexing foundation

The indexing foundation remains independently deployable, and the candidate-chat endpoint now has a separately
controlled opt-in retrieval path:

PostgreSQL remains the application's relational database. pgvector is an extension loaded into that same database; it
adds the `vector` type, cosine-distance operations, and HNSW/IVFFlat indexing rather than introducing another data
service. The deployment and migration tests use `pgvector/pgvector:pg16`, which is PostgreSQL 16 with the extension
files installed. Flyway still enables `vector` per database and owns every schema object. Testcontainers is explicitly
told that this image is a compatible substitute for the standard `postgres` image so its PostgreSQL container support
can supply the JDBC URL, credentials, lifecycle, and cleanup normally.

- Flyway V6 enables pgvector and creates the Spring AI `PgVectorStore`-compatible `cv_semantic_chunks` table with
  1,536-dimensional embeddings and an HNSW cosine index. Spring AI owns document persistence and portable metadata
  filtering, while Flyway remains the only schema owner. Generated relational columns and composite
  `(id, organization_id)` foreign keys prevent metadata from referencing a candidate or document in another tenant.
- Native text is divided into bounded overlapping document chunks. OCR is chunked one page at a time so retrieval can
  preserve reviewer-visible page citations.
- `text-embedding-3-small` is the default pinned embedding model. Spring AI and the Flyway vector column are both
  configured for 1,536 dimensions, so a provider/configuration mismatch fails instead of silently changing the schema.
- Indexing begins only after successful CV import or reprocessing commit. The provider call therefore cannot extend or
  roll back the business-data transaction. Replacement of one document's old chunks with its new chunks is atomic.
- Candidate/document erasure removes chunks through database cascades. A bounded hourly reconciliation pass retries
  provider failures, indexes pre-existing documents, and replaces rows whose source version is stale.
- Every similarity query requires an application-supplied organization identifier and supplies it to Spring AI as a
  mandatory metadata filter. An optional candidate-ID filter can narrow—but never widen—the tenant boundary. Generated
  database columns provide a second tenant-integrity boundary underneath the portable Spring AI metadata contract.
- Extracted chunks and embeddings are sensitive derived candidate data. They remain in PostgreSQL, are never written to
  application logs, and are sent to the configured embedding provider only when indexing is explicitly enabled.

### Vector similarity and indexing

`COSINE_DISTANCE` defines semantic proximity. PostgreSQL compares the direction of the recruiter-question embedding
with each eligible CV-chunk embedding using pgvector's `<=>` operator. A smaller distance means the vectors point in
more similar semantic directions; when a similarity score is needed, it can be expressed as `1 - cosine distance`.
This suits text retrieval because related wording can remain close even when the literal terms or vector magnitudes
differ.

`HNSW` means Hierarchical Navigable Small World. It builds a layered graph linking nearby vectors so PostgreSQL can
navigate toward likely nearest neighbours instead of comparing the question with every stored chunk. It provides fast,
high-recall interactive retrieval, but consumes additional storage and memory, makes inserts/index construction more
expensive, and returns approximate rather than guaranteed exact nearest neighbours. The configured 1,536-dimensional
OpenAI embeddings fit pgvector's standard-vector HNSW limit.

These settings serve separate responsibilities: cosine distance defines what “near” means, while HNSW finds nearby
vectors efficiently. Neither setting authorizes access. Spring AI must first apply the mandatory organization metadata
filter and any optional candidate-ID narrowing filter; the generated relational columns and foreign keys provide the
database-level tenant-integrity boundary.

`CV_SEMANTIC_INDEX_ENABLED` defaults to `false` and controls index creation/reconciliation.
`CV_CHAT_SEMANTIC_RETRIEVAL_ENABLED` separately defaults to `false`, allowing an accepted index to remain operational
while query-time behavior is tested. When enabled, the selected candidate is authorized before search; the authenticated
organization and verified candidate ID become mandatory vector filters; only the current document and matching source
version are accepted; low-scoring matches are rejected; repeated chunks from one document/page are merged into one
bounded citation; and an empty result returns the backend-controlled insufficient-evidence response without calling
the answer model. The earlier question-policy call still runs for every request. Retrieval failures return a stable
`502` without logging questions, excerpts, embeddings, or provider messages.

For policy-approved questions, the multilingual-recall correction now asks a dedicated constrained model to produce one
bounded English retrieval query while preserving employer, project, certification, technology, and product names. The
answer model still receives the recruiter's original question, so normalization cannot change the requested language or
intent. Semantic search runs once with the original wording and once with the normalized query, deduplicates identical
chunks by keeping their strongest score, reapplies candidate/current-document/source-version checks, sorts by score, and
caps the combined evidence at the configured maximum. The normalizer never chooses organization, candidate, document,
or authorization scope. If normalization fails, retrieval safely falls back to the already policy-approved original
question. The additional model and embedding calls add latency and provider cost only when semantic retrieval is enabled
and a current CV document exists.

Operational diagnostics record candidate/document identifiers, query count, raw and accepted match counts, and the top
numeric similarity score. They deliberately exclude recruiter wording, normalized queries, CV text, embeddings, and
provider messages. This allows successful and failed paraphrases to be compared without placing candidate or recruiter
content in logs. The global `0.35` threshold is unchanged.

The focused synthetic live-provider gate passed on 30 October 2026. The direct French KLx formulation improved from an
original-query top score of `0.4561` to `0.5451` after normalization, while the alternate French formulation improved
from `0.4621` to `0.5887`. All seven EN/FR/PT cases retained the expected tenant/candidate-filtered document, required
evidence, citations, answer-language checks, and evaluator passes. Normalization did not improve every case—for example,
the English Angular query scored `0.4574` originally and `0.4239` after normalization—confirming why production merges
both searches instead of replacing original wording. These synthetic results authorize staging validation; they do not
claim that the real-CV KLx miss is corrected until the deployed path passes its environment checks.

`CV_CHAT_SEMANTIC_MAX_MATCHES` defaults to `6` and is validated between `1` and `20`.
`CV_CHAT_SEMANTIC_MIN_SIMILARITY` defaults to `0.35` and is validated between `0.0` and `1.0`; it is an initial reviewed
staging value, not a universal quality claim. The existing excerpt limit still bounds every merged citation. No public
semantic-search controller is exposed: retrieval remains inside the authorized candidate-chat request.

## Environment acceptance

### Staging

The candidate-specific backend foundation and safe-refusal correction passed staging acceptance on 27 October 2026
for main revision `2f85ddc`. The accepted checks covered:

- healthy deployment with the configured evidence-page and excerpt limits;
- grounded native and OCR answers with backend-issued document or page citations;
- fixed `200` responses with an empty citation list for job-relevant insufficient evidence and out-of-scope or
  sensitive questions;
- continued rejection of unsupported answers rather than displaying arbitrary uncited provider prose;
- request validation, CSRF enforcement, authenticated role policy, generic missing/cross-tenant `404` behavior, and
  tenant-scoped candidate evidence;
- privacy-safe logs without recruiter questions, CV excerpts, prompts, or provider responses.

The candidate-specific Angular interface passed staging acceptance on 28 October 2026 at main revision `c321437`.
The accepted checks covered:

- visibility for `ADMIN`, `RECRUITER`, and `REVIEWER`, with no chat interface for `READ_ONLY`;
- grounded native-text and OCR answers, fixed safe responses without citations, and structured loading, failure, and
  retry states;
- clearing the previous response when the selected candidate changed;
- document and OCR-page citation labels linked to the matching ingestion-history document card; and
- corrected citation navigation that keeps `/candidates/import` active and scrolls to the selected document instead of
  resolving the root fragment through the default `/jobs/job-offer` route.

The opt-in semantic-indexing foundation passed staging acceptance on 29 October 2026 at main revision `2db9a7a`. The
accepted checks covered:

- Flyway V6 and Spring AI schema validation against the Flyway-owned `cv_semantic_chunks` table with 1,536-dimensional
  vectors and the HNSW cosine index;
- immediate post-commit indexing of native-text document `64` into five tenant-scoped chunks;
- bounded reconciliation recovery of documents `62` and `63` into three and two chunks respectively at the normal
  `02:11 UTC` scheduled run, followed by a zero pending-document count;
- privacy-safe operational logs with no CV excerpts, embeddings, or provider responses and no remaining
  `DELETE_EXISTING`, `INSERT_REPLACEMENT`, or default `vector_store` errors; and
- harmless PDFBox/FontBox missing-PostScript-name warnings that did not prevent extraction or semantic indexing.

Staging validation exposed and corrected two integration defects before acceptance. Spring AI's metadata-JSONPath
deletion failed against the deployed environment, so atomic replacement now uses a parameterized deletion through
V6's generated `organization_id` and `document_id` columns. The pgvector configuration was also incorrectly nested
under `spring.ai.openai`, causing Spring AI to ignore `table-name` and insert into its nonexistent default
`public.vector_store`; it is now correctly bound under `spring.ai.vectorstore.pgvector`. Container-backed replacement,
rollback, tenant-isolation, reconciliation, and deletion-cascade regressions protect both corrections. The accepted
index remains independently controlled from query-time semantic retrieval.

The tenant-scoped single-candidate retrieval path passed staging acceptance on 30 October 2026 after PR #98 was merged
at main revision `990f0cb`. Retrieval remained dark during the initial health and legacy-chat smoke checks, then was
enabled independently while indexing stayed active. Calibration used indexed facts from several question shapes rather
than accepting a threshold from one successful request. The original `0.55` value rejected valid grounded evidence;
`0.35` retained cited answers for exact company/technology facts, paraphrased technology experience, and indexed
education evidence, while genuinely absent restaurant and salary information still returned the fixed
insufficient-evidence response without citations. This value is the accepted initial deployment default, not a quality
guarantee for every model, language, CV corpus, or future embedding change.

The pre-retrieval question-policy correction passed focused staging acceptance on 30 October 2026 after PR #103 was
merged at main revision `87821a4`. The opt-in provider-backed evaluation classified all 21 synthetic, human-reviewed
English, French, and Portuguese cases correctly with `gpt-4o-mini`, including terse professional wording, sensitive
traits, unrelated requests, adversarial instructions, and the distinction between employment by a religious
organization and the candidate's personal religion. End-to-end checks confirmed that `Is it a Java engineer ?`
continued to semantic retrieval and returned a grounded `[CV-61]` answer. Religion and weather requests were rejected
at `PRE_RETRIEVAL_POLICY` as `SENSITIVE` and `OUT_OF_SCOPE`; privacy-safe logs contained candidate/organization IDs,
stage, and label but no question or CV content. An Angular-project question also returned the expected grounded
`[CV-61]` citation.

This acceptance is limited to question-policy behavior. Multilingual probing exposed a separate retrieval/answer
quality gap: the English KLx question retrieved grounded evidence, one French paraphrase returned insufficient evidence,
and another French paraphrase retrieved the same evidence but did not consistently preserve French across repeated
answers. These observations do not weaken the policy acceptance, but multilingual paraphrase recall and response-language
consistency remain required inputs to the next versioned retrieval and generated-answer evaluation suite. Production
acceptance of the policy correction completed later the same day in release `v0.22.0`.

The multilingual retrieval/generated-answer slice passed scoped staging acceptance on 30 October 2026 after PR #106
was merged at main revision `d46f435`. English grounded answers remained correct; repeated grounded French and
Portuguese answers preserved the question language and authorized citations; sensitive and unrelated policy behavior,
privacy-safe logs, and backend health did not regress. The versioned live-provider gate also passed all seven synthetic
retrieval cases, its repeated EN/FR/PT answer subset, deterministic tenant/candidate/reference/language assertions, and
Spring AI relevancy and fact-checking judgments.

Acceptance preserves one explicit limitation. The real-CV question `Sur quoi a-t-il travaillé chez KLx ?` still returned
controlled insufficient evidence although the English equivalent and another French formulation retrieved `[CV-61]`.
No unsupported claim or citation was produced. PR #106 intentionally changed answer-language preservation, not deployed
chunking, embeddings, or threshold policy, so this safe multilingual recall miss requires a separate measured retrieval
correction. Application-owned out-of-scope and insufficient-evidence messages plus the Angular explanatory note remain
English; structured outcomes and EN/FR/PT controlled-message localization are deferred until immediately before the
dashboard. This paragraph records PR #106's historical boundary; PR #109 subsequently corrected the real-CV miss as
recorded below.

The measured multilingual-recall correction passed focused staging acceptance on 1 November 2026 after PR #109 was
merged at main revision `26d59e8`. The deployed path searched both the original wording and its constrained English
retrieval query while retaining the accepted `0.35` threshold and six-chunk cap. Real English, French, and Portuguese
questions returned grounded, candidate-scoped citations; missing information remained controlled, and sensitive or
unrelated questions were still rejected before evidence retrieval. Logs for staging candidate `60` and current document
`61` reported `queryCount=2`, 12 raw matches, 4–6 accepted matches, and top similarity values from `0.4693` to `0.5535`.
The logs contained only identifiers and numeric diagnostics. They did not contain the original question, normalized
query, CV text, embeddings, or provider payloads. Repeated end-to-end requests completed in approximately 3–4 seconds.
This is the current staging baseline, not a final performance threshold; later concurrency validation must measure
p50/p95/p99 latency, provider cost, and throttling. No new environment variable, Flyway migration, database, Compose,
or frontend change was required. Focused production acceptance followed on 1 November 2026 in release `v0.24.0`.

### Production

The same candidate-specific backend and safe-refusal behavior passed production acceptance on 27 October 2026 without
adding CV-chat variables to the production environment file. Production used the reviewed Compose defaults of ten
evidence pages and 1,200 characters per excerpt. The accepted checks covered:

- healthy deployment and effective default configuration;
- grounded answers with valid backend-issued citations;
- fixed insufficient-evidence and out-of-scope responses with an empty citation list;
- request validation, CSRF enforcement, authentication, role policy, and generic not-found behavior; and
- privacy-safe application logs without questions, CV excerpts, prompts, provider responses, or credentials.

The candidate-specific Angular interface passed focused production acceptance on 28 October 2026 at main revision
`43dd2c3`. The accepted checks repeated the staging role visibility, native-text and OCR answers, fixed safe responses,
loading and failure behavior, candidate-change reset, and privacy-safe log review. Document and page citations remained
on `/candidates/import` and scrolled to the matching ingestion-history document card.

The semantic-indexing foundation passed production acceptance on 29 October 2026 at main revision `64a7a00`, with
`CV_SEMANTIC_INDEX_ENABLED=true`. Flyway V6 and the pgvector schema were healthy; disposable production document `4`
was indexed immediately into three tenant-scoped chunks; the reconciliation query reported zero pending documents;
and the focused log review found no replacement-deletion, replacement-insertion, PostgreSQL, or default-table errors.
Administrator-confirmed erasure of the disposable candidate then removed its candidate row, document row, and all
semantic chunks: each post-erasure count was zero. This validates the internal indexing lifecycle in production; it
does not itself enable semantic retrieval in candidate chat.

The separately switched tenant-scoped single-candidate retrieval path passed focused production acceptance on
30 October 2026 in release `v0.21.0` at main revision `0b9bf79`. Production kept semantic indexing enabled, enabled
query-time retrieval with six maximum matches and the staging-calibrated `0.35` minimum similarity, and confirmed
healthy deployment plus effective configuration. Exact company/technology facts, paraphrased experience, and indexed
education evidence returned grounded citations for the selected candidate; genuinely absent restaurant and salary
information retained the controlled insufficient-evidence response without factual claims or citations. The focused
log review found no semantic-retrieval failures or disclosure of recruiter questions, CV excerpts, embeddings, or
provider payloads.

The corrected pre-retrieval question policy passed focused production acceptance on 30 October 2026 in release
`v0.22.0` at main revision `6d96d2b`. The healthy deployment required no new environment, Flyway, database, Compose,
or frontend configuration. The terse Java-engineer and Angular-project questions continued to grounded retrieval and
returned authorized citations. Religion and weather questions returned the controlled refusal without citations and
were logged at `PRE_RETRIEVAL_POLICY` with `SENSITIVE` and `OUT_OF_SCOPE` labels. Log review confirmed that neither
recruiter questions nor CV content was recorded. This production acceptance covers the policy correction; the staging-
observed multilingual paraphrase-recall and response-language limitations remain pending evaluation work.

The multilingual generated-answer behavior passed focused production acceptance later on 30 October 2026 in release
`v0.23.0` at main revision `083b9c4`. English grounded answers remained correct; repeated grounded French and Portuguese
answers preserved their question language and authorized citations; policy refusals, tenant boundaries, backend health,
and privacy-safe logs did not regress. No new environment, Flyway, database, Compose, or frontend configuration was
required. At this release boundary, the real-CV French KLx paraphrase was a documented controlled
insufficient-evidence case rather than a resolved retrieval path. PR #109 subsequently resolved that path in release
`v0.24.0`. Application-owned safe messages and their Angular explanatory text remain English until the planned
pre-dashboard localization slice.

The dual-query multilingual-recall correction passed focused production acceptance on 1 November 2026 in release
`v0.24.0` at main revision `7576611`. Production repeated the successful grounded English, French, and Portuguese
questions, candidate-scoped citations, tenant isolation, missing-evidence result, and pre-retrieval sensitive/unrelated
refusals. The privacy-safe log review showed no retrieval errors and no recruiter question, normalized query, CV text,
embedding, or provider payload. No new environment variable, Flyway migration, database, Compose, or frontend change
was required. Production response latency was functionally acceptable during the smoke tests, but no percentile or
representative numeric baseline was captured; p50/p95/p99 measurement remains part of the later concurrency benchmark.

The neutral candidate-reference safeguard passed focused production acceptance on 10 November 2026 at main revision
`f759b88`. The answer prompts no longer infer or choose `he`, `she`, or singular `they` from a candidate name or CV
evidence. Single-candidate answers prefer “the candidate,” translated into the response language; comparisons prefer
the backend-supplied candidate label, confirmed display name, or the same neutral reference. Grounding, citations,
question-language preservation, safe refusals, and existing single- and multi-candidate behavior remained functional.
This is a generated-answer presentation rule, not pronoun detection or storage. No environment variable, Flyway
migration, database, Compose, or frontend change was required.

The dedicated multi-turn page, connecting the new bounded JDBC memory to follow-up execution, and the complete
retrieval/generated-answer evaluation suite remain separate implementation slices. The first versioned
question-policy dataset is operational, but acceptance of this bounded single-candidate path does not mark broader AI
evaluation complete.

## Supported interactions

- Find candidates by skills, experience, location, education, title, employer, or evaluation metric.
- Apply multiple filters and refine them over follow-up turns.
- Ask questions about a specific candidate.
- Compare candidates identified through structured UI selection or unambiguous natural-language references against
  recruiter-selected criteria.
- Return concise summaries with references to candidate profiles.
- Ask for clarification when the request is ambiguous.
- Explain when no candidate matches or when an internal operation fails.

### Candidate identification and comparison

Candidate identification is a deterministic authorization step, separate from the comparison criteria. A recruiter
may select candidates in the UI or write an unambiguous request such as “Compare Jane Smith and Peter Jones.” The
backend resolves each reference only inside the authenticated organization and converts every resolved name to a
verified tenant-scoped candidate ID before retrieving CV, evaluation, or job-fit evidence.

#### Final dual-entry conversation contract

The final web chat supports two entry paths that converge on the same tenant-verified candidate-ID set:

1. **Picker-first:** the recruiter searches for and explicitly selects candidates, then asks a question.
2. **Language-first:** the recruiter asks naturally, such as “Compare Jane and Peter for this role.” The application
   resolves candidate references deterministically inside the authenticated organization, displays minimal candidate
   cards, and requires explicit confirmation before retrieving any CV, evaluation, or job-fit evidence.

The existing Angular picker is therefore a reusable authorization and clarification component, not a disposable
picker-only screen. The conversation displays the confirmed scope as removable candidate chips or cards and provides
explicit **Change candidates** and **Clear conversation** actions. Adding, replacing, or removing a candidate updates
the verified scope only through recruiter action. Follow-up questions may reuse the confirmed IDs, but conversation
memory must never infer, add, replace, or silently widen that set.

The current candidate workspace is the integration and acceptance surface for these contracts, not the final chat
layout. Flyway V7 and the scoped-conversation CRUD API now establish the owner/tenant/candidate/job authorization
boundary and a bounded Spring AI JDBC memory repository; the answer path does not consume that memory yet. After this
foundation is accepted and follow-up execution is connected, build a dedicated CV-chat page with a message timeline,
one natural-language composer, visible candidate/job scope chips, confirmation cards only when resolution is
ambiguous, cited answers, conversation-history controls, and later speech input/playback. Picker-first and
language-first entry remain available and converge on the same application-owned confirmed scope.

#### Scoped conversation-memory staging acceptance

The Flyway V7 scoped-memory foundation passed focused staging acceptance on 11 November 2026 at main revision
`ec4e500`. A validated matched PostgreSQL and original-CV backup preceded deployment. The backend remained healthy,
Flyway reported schema version `7`, and Hibernate validation completed without migration or mapping errors.

Authenticated conversation creation, owner retrieval, explicit ordered scope replacement, and deletion passed through
the new `/api/chat/conversations` contract. Scope replacement refreshed the Time To Live (TTL) expiry and incremented
the optimistic version; replaying the stale version returned `409`. Duplicate IDs returned `400`, while missing or
cross-tenant candidates returned the generic `404`. A second authorized user could not retrieve the first user's
conversation and received the same generic `404`, confirming that a conversation ID alone grants no access.

PostgreSQL inspection confirmed the expected owning organization/user, candidate order, version, timestamps, and
expiry. The Spring AI memory table correctly contained zero rows because this slice does not yet connect memory to the
answer path. Deleting the disposable conversation removed its scope, candidate memberships, and any subordinate
memory rows. Existing single-candidate chat, picker-first and language-first discovery, multi-candidate comparison,
sensitive-question refusal, job generation, and candidate evaluation remained functional. No staging environment
override was required because the reviewed TTL, message-window, cleanup-batch, and cleanup-schedule defaults applied.

#### Scoped conversation-memory production acceptance

The same foundation passed focused production acceptance on 11 November 2026 in release `v0.36.0` at main revision
`10a4db1`. Production confirmed backend health, Flyway schema version `7`, authenticated conversation create/read/
scope-replace/delete behavior, owner and tenant isolation, stale-version conflict handling, database state, and
deletion cleanup. Existing single-candidate chat, candidate discovery, grounded comparison, job generation, and
candidate evaluation remained functional, and no production environment override was required.

This acceptance validates the storage, authorization, expiry, and cleanup foundation only. The Spring AI memory table
remains empty during ordinary chat because persisted conversation memory is not yet connected to follow-up answer
execution. The next slice must connect it without allowing remembered state to add, replace, remove, or widen the
application-confirmed candidate and optional approved-job scope.

An exact unique language match still appears for confirmation before evidence retrieval. Partial, ambiguous, or
multiple matches produce bounded candidate choices; missing names use the generic not-found behavior. The model may
help interpret the recruiter message, but it cannot supply the organization boundary or authorize a candidate. Both
entry paths call the same tenant-authorized retrieval and comparison services after confirmation, so citations,
refusals, role checks, privacy rules, and audit behavior remain identical.

If a referenced name does not exist in that organization, the request follows the generic not-found contract. If a
name matches more than one candidate, the application asks the recruiter to choose the intended record rather than
guessing. The recruiter may then compare the verified candidate set using any authorized criterion, including skills,
experience, education, leadership evidence, evaluation results, or fit for an explicitly selected job.

The model never performs global name resolution, invents or silently expands the candidate set, or supplies the
organization boundary. Comparison uses only authorized evidence for the resolved IDs, and every factual conclusion
requires candidate/document citations.

The first candidate-identification backend slice implements this boundary without yet exposing a model tool or
multi-candidate retrieval. `POST /api/chat/candidates/resolve` accepts a validated exact name and obtains the
organization exclusively from the authenticated principal. Matching is case-insensitive and ignores surrounding UI
whitespace, but deliberately excludes fuzzy and partial matching. A unique record returns `RESOLVED`; duplicate exact
names return `AMBIGUOUS` so the UI can require explicit selection; and missing or cross-tenant names produce the same
generic `404`. The response projects only candidate ID, name, and current title instead of loading email or CV content.
At most 20 options are returned with an explicit `truncated` marker. `ADMIN`, `RECRUITER`, and `REVIEWER` retain access
under the existing CV-chat role policy, while `READ_ONLY` remains forbidden. This endpoint only resolves identities;
it does not authorize evidence retrieval by itself, compare candidates, or permit the model to select an organization.

Recruiters may not know a candidate's complete name, so `POST /api/chat/candidates/search` provides a separate bounded
discovery operation. It performs case-insensitive substring matching only inside the authenticated organization,
treats SQL wildcard characters as literal user input, ranks an exact name first, and returns stable name/ID ordering.
Queries require at least two characters and results use the same minimal identity projection, 20-item limit, and
`truncated` marker. Missing and cross-tenant searches both return an empty list. Search results are choices only: even
one partial match must be explicitly selected before evidence retrieval.

The exact-resolution and partial-search contracts passed focused staging acceptance on 2 November 2026 for revision
`ddb6eaa`. `ADMIN`, `RECRUITER`, and `REVIEWER` could use both endpoints while `READ_ONLY` remained forbidden. Exact
unique names resolved correctly, duplicate exact names required explicit selection, and missing or cross-tenant exact
names shared the generic not-found response. Partial queries returned only organization-owned minimal identities,
reported bounded-result truncation, returned an empty list for missing and cross-tenant matches, and never selected a
candidate automatically. The selected candidate ID remained the required input for subsequent CV-chat requests.

Production accepted the same candidate-identification contracts on 2 November 2026 after PR #114 was merged at main
revision `2831d2d`. The focused checks covered unique and ambiguous exact resolution, missing and tenant-isolated
behavior, partial matching and minimum-length validation, bounded minimal responses, role authorization, and explicit
selection. A returned candidate ID then completed a grounded candidate-specific chat request successfully. No new
environment variable, Flyway migration, database operation, or infrastructure change was required.

The first frontend integration keeps discovery and authorization visibly separate. An authorized reviewer submits at
least two name characters, sees bounded minimal matches from their organization, and explicitly chooses one result.
Only that chosen ID updates the existing candidate details, evaluation, and single-candidate chat workflow. Empty,
truncated, loading, and structured-error states are displayed without exposing another organization's existence. This
picker does not yet create a multi-candidate comparison request or allow the model to choose a candidate.

The Angular picker passed focused staging acceptance on 2 November 2026 after PR #116 was merged at main revision
`cd075b3`. Testing confirmed the two-character minimum, manual button/Enter submission, case-insensitive partial
matching, safe empty state, bounded/truncated guidance, structured errors, explicit result selection, role visibility,
and successful reuse of the chosen ID by candidate details, evaluation history, and grounded chat. The existing
candidate dropdown and single-candidate workflow continued to work. No environment, Flyway, database, or
infrastructure change was required. Automatic debounced autocomplete remains an optional usability refinement.

Production repeated the focused Angular picker checks successfully on 2 November 2026 for the PR #116 implementation
at main revision `cd075b3`. Manual button and Enter submission, minimum-length enforcement, tenant-scoped partial
results, safe empty/truncated/error states, explicit selection, role visibility, and selected-ID reuse all behaved as
accepted in staging. Candidate details, evaluation history, grounded chat, and the existing candidate dropdown also
passed regression checks. Automatic autocomplete remains deferred rather than implied by this acceptance record.

### Explicit multi-candidate comparison backend

`POST /api/chat/candidates/compare` accepts one job-relevant question, between two and five distinct candidate IDs, and
an optional approved-job ID. The organization is taken only from the authenticated principal. Application code verifies
the complete candidate set before classification or evidence retrieval; one missing or cross-tenant ID therefore
produces the same generic `404` without disclosing which ID failed. Duplicate IDs are rejected, and the existing CV-chat
role and CSRF policy applies. If a job ID is present, the backend independently loads an approved job in the same
organization and converts it into a bounded `CvChatComparisonJobContext`; a missing or cross-tenant job receives the
same generic approved-job `404`. No job ID keeps the original candidate-only comparison contract.

After pre-retrieval sensitive/out-of-scope classification, evidence is loaded independently and remains bounded for
each verified candidate. The question-policy classifier receives only the trusted application fact that an approved job
was selected, never a model-proposed job or authorization state. If any candidate has no relevant evidence, the backend
returns a controlled insufficient-evidence response rather than letting the model turn a retrieval miss into a negative
comparison. The comparison model receives only the verified minimal identities, authorized evidence, and optional
bounded job requirements. Server-side validation rejects unknown citations and uncited prose. A comparison that does
not cite every selected candidate is replaced with the controlled insufficient-evidence response rather than exposing
partial output. A grounded response returns the answer, the verified minimal candidate identities, and only
reconstructed authorized citations. The model cannot search for, add, or select candidates or jobs; Angular supplies
only the recruiter-visible scope, and Java reauthorizes it at the final evidence boundary.

The backend comparison contract passed focused staging acceptance on 2 November 2026 after PR #120 was merged at main
revision `bf1ebb8`. The first live-provider request correctly failed closed with `502` because the generated comparison
did not use the bracketed citation syntax required by application validation. PR #120 corrected the provider boundary
without weakening validation: authorized references are now supplied as `[CV-<document>]` or
`[CV-<document>-P<page>]`, with an explicit instruction and regression test requiring that exact syntax. The repeated
request returned a grounded comparison of exactly the two selected candidates, with reconstructed citations for both
documents and no unselected candidate. Staging also accepted the sensitive-question refusal, one-candidate and
duplicate-ID `400` responses, generic missing/cross-tenant `404`, `READ_ONLY` `403`, allowed-role behavior, and
privacy-safe logs. No database migration or environment-variable change was required.

Production repeated the focused comparison acceptance successfully on 2 November 2026 after PR #121 was merged at
main revision `ff6b283`. The promoted immutable implementation returned a grounded two-candidate comparison with
authorized citations for both selected candidates and introduced no unselected candidate. Production also confirmed
the controlled sensitive-question response, candidate-count and duplicate validation, generic missing-ID behavior,
role enforcement, and privacy-safe logging. No production database migration or environment-variable change was
required. The backend comparison contract is therefore accepted in both environments.

### Angular multi-candidate comparison

PR #124 added the reusable picker-first Angular comparison interface. Candidate search results and the currently open
candidate can be added to an explicit two-to-five-candidate scope. The interface keeps that scope visible as removable
candidate chips, supports clearing the selection, preserves the written question across retryable errors, and sends
only the displayed candidate IDs plus an optional explicitly selected approved-job ID to
`POST /api/chat/candidates/compare`. Successful responses render the verified minimal candidate identities, answer,
and candidate-labelled citations. Selecting a citation opens its owning candidate before navigating to the cited
document, so the UI does not leave a source attached to the wrong person.

The client captures the submitted candidate-ID scope and ignores a delayed response if that scope changes before
completion. Ordinary selection controls are disabled while a request is active, while the scope comparison remains a
defense against future programmatic changes and asynchronous races. Removing, replacing, or erasing a candidate clears
any answer that belonged to the previous scope. `READ_ONLY` users do not receive the comparison interface, and the
backend remains the authoritative role and tenant boundary.

Focused staging acceptance completed on 2 November 2026 after PR #124. Selection, deduplication, the two-to-five
boundary, removal and clearing, grounded comparison, candidate-labelled citations, document navigation, controlled
sensitive/out-of-scope/insufficient-evidence responses, retry behavior, and authorization checks passed. Testing also
found that a job-relevant question could produce provider output citing only part of the verified selection. PR #125
kept the strict every-candidate citation rule but replaced that provider-validation `502` with the existing controlled
insufficient-evidence response and no partial citations. The correction passed the full staging comparison checks.
No schema, Flyway, or environment-variable change was required.

Production repeated the focused Angular comparison and fallback checks successfully on 2 November 2026 in release
`v0.28.0`. The immutable production deployment accepted the visible two-to-five-candidate scope, selection/removal and
clear behavior, grounded comparison, candidate-labelled citations, correct candidate/document navigation, controlled
sensitive/out-of-scope/insufficient-evidence responses, retry behavior, and role enforcement. A comparison without
complete citation coverage returned controlled insufficient evidence rather than `502` or partial provider output.
Privacy-safe logs contained no recruiter question, generated answer, or CV evidence. The picker-first comparison flow
is therefore accepted in both staging and production.

PRs #128–#130 completed and stabilized the language-first confirmation path on 3 November 2026. A recruiter can name
two to five candidates in a natural comparison request, review tenant-scoped candidate cards, resolve ambiguity
explicitly, and confirm the visible IDs before any CV evidence is retrieved. PR #129 made multi-token candidate lookup
independent of stored name order and prevented a trailing provider control token from appearing in a grounded answer.
PR #130 then prepared the criteria-focused retrieval-query set once per comparison and reused that exact set for every
confirmed candidate, rather than allowing separate normalization calls to introduce candidate-to-candidate retrieval
drift.

Focused staging acceptance repeated the same confirmed comparison and consistently returned grounded evidence and
citations for every selected candidate. Retrieval summaries showed the same query-count structure with accepted
matches for both candidates; no raw `INSUFFICIENT_EVIDENCE` token, `CITATION_COVERAGE` rejection, query-normalization
failure, or comparison-provider error appeared. Production repeated those checks successfully in release `v0.29.0`.
No schema, Flyway, environment-variable, or frontend configuration change was required. The picker-first and
language-first entry paths are therefore accepted in staging and production through the same verified-ID comparison
boundary.

PR #144 connected that language-first path to the Spring AI read-tool preflight. PRs #146–#154 then stabilized the
application-owned workflow: discovery became deterministic and one-pass, ambiguity returns bounded confirmation cards,
confirmed-scope callbacks can read only the exact selected candidate summaries and optional tenant-authorized approved
job, and provider output cannot authorize, refuse, or rewrite the workflow transition. The final grounded comparison is
deliberately outside the tool preflight and repeats candidate/job authorization before evidence retrieval.

The Angular language-derived scope now includes the selected candidate IDs, question, and optional job ID. Changing the
job invalidates a language-confirmed comparison, and starting another language-first request immediately clears its old
cards, confirmed comparison question/job context, result, and errors. The new request text and optional approved-job
dropdown selection remain available as inputs for the new request. A later refusal, clarification, or asynchronous
response therefore cannot leave cards from a previous request visible. This reset applies only to the language-first
scope; independently assembled picker-first selections remain intact.

## Inputs and outputs

Inputs:

- Natural-language query
- Optional push-to-talk audio that is transcribed into a visible, editable query
- Authenticated user and organization identifier
- Conversation/session identifier
- Authorized conversation history
- CV vector index
- Structured candidate metadata and source CV text

Outputs:

- Natural-language answer
- Structured candidate results for UI cards or tables
- Candidate identifiers and links
- Evidence references supporting the answer
- Updated conversation state
- Clarification, empty-result, or error response when appropriate
- Optional synthetic speech generated from the same authorized answer shown on screen

## Implementation steps

1. [x] Establish an imperative, candidate-specific endpoint with tenant authorization, bounded evidence, mandatory
   citations, provider-error handling, and role/CSRF tests.
2. [x] Add Flyway-owned, Spring AI `PgVectorStore`-compatible storage and configure its embedding model with a fixed
   1,536-dimensional contract. Spring AI schema initialization remains disabled; the feature remains opt-in until
   environment migration and acceptance.
3. [x] Add bounded, overlapping native/OCR chunking with organization, candidate, document, page, source-version, and
   lifecycle metadata protected by composite tenant foreign keys.
4. [ ] Validate automatic post-commit indexing, reprocessing replacement, deletion cascades, and scheduled
   missing/stale backfill in staging; evaluation-summary indexing remains future work.
5. Implement organization- and role-based retrieval filters before semantic search.
6. Build a retrieval service that returns relevant chunks plus structured candidate data.
7. [~] Build Spring AI tools for candidate search, tenant-scoped name resolution, candidate detail, filtering, comparison,
   evidence, document status, and governed dashboard aggregates. Resolve UI selections or unambiguous names to
   verified candidate IDs before retrieval; return not found or require disambiguation instead of allowing the model
   to guess the candidate set. Make tools request-scoped, inject authenticated user and organization context outside
   model-controlled arguments, and separate bounded query tools from preview-and-confirm command tools.
8. Require semantic-search responses to cite candidate records or source excerpts and prohibit unsupported claims.
9. Persist conversation state with clear retention and deletion rules.
10. Implement the web chat UI with text responses, result cards, loading states, and follow-up prompts.
11. Add ambiguity detection, clarification, no-result, timeout, and partial-failure behavior.
12. Protect against prompt injection in CV content, OCR text, transcripts, messages, and user queries, including attempts to expand tool access or bypass confirmation.
13. Add authorization tests proving users cannot retrieve another organization’s candidates.
14. Evaluate retrieval precision, answer faithfulness, tool selection, latency, and token cost against a fixed test set.
15. Add push-to-talk speech-to-text as an input adapter around the same authorized chat service.
16. Display the transcript for correction and delete raw audio after transcription by default.
17. Add optional text-to-speech playback without removing candidate cards, citations, or visual review.
18. Add pause, resume, speed, language, accessibility, and sensitive-content playback controls.
19. Measure transcription accuracy, voice latency, playback usage, and cost without profiling the speaker.
20. Add the selected external messaging channel only after web-chat acceptance criteria pass.

The first read-tool foundation now provides request-scoped `search_candidates` and `get_candidate_summary` callbacks,
trusted server-side organization/role injection, bounded execution, and an internal opt-in orchestrator. Discovery can
register only search; confirmed-scope planning may register both tools, but summary access is restricted to the exact
tenant-authorized IDs installed after human confirmation. The model returns only an application-validated workflow
kind plus presentation text—it cannot return candidate IDs, organization, role, or authorization state. Java may add
minimal tenant-authorized candidate cards to a discovery response for human confirmation; those IDs come from the
authenticated search results, never from model output. Illegal phase transitions such as claiming
`CONFIRMED_SCOPE_READY` during discovery fail closed.

The next read-tool increment adds `get_job_requirements`. It is excluded from discovery and ordinary candidate-only
planning. Application code must first tenant-authorize one recruiter-selected approved job and install its ID into the
immutable request scope; only then does the registry expose the callback. Execution repeats both confirmed-scope and
tenant checks and returns bounded evaluation criteria without organization, compensation, benefits, tone, or generation
metadata. This prepares job-grounded planning without giving the model authority to select a job.

The reviewed orchestration API separates discovery from confirmed-scope planning at
`POST /api/chat/tools/discover` and `POST /api/chat/tools/confirmed-scope`. Jakarta validation bounds messages and IDs;
Spring Security enforces authentication, CSRF, and the existing chat roles. The controller obtains organization only
from `AppUserPrincipal` and ignores injected organization/role fields. Confirmed-scope responses contain only the
validated workflow kind and presentation text. Discovery keeps those top-level fields and may add `candidateScope`
only for `CANDIDATE_CONFIRMATION_REQUIRED`; that scope contains the trimmed request plus the same extracted references
and minimal cards returned by the deterministic tenant-scoped searches. Refusal and clarification return a null
`candidateScope`. Controller and security tests cover role access, CSRF, authentication, validation, tenant-context
injection attempts, minimized responses, structured discovery/policy provider failures, and privacy-safe fallback when
the non-authoritative confirmed-scope tool preflight is unavailable.

PR #144 connected the Angular language-first flow to these two phases. The subsequent one-pass discovery correction
uses one provider reference-extraction call and one deterministic tenant-scoped search per extracted person, then
returns those exact cards in `candidateScope`. Angular consumes that scope directly instead of asking
`POST /api/chat/candidates/scope` to extract and resolve the same names a second time. This removes the possibility that
two provider calls disagree about an otherwise identical recruiter request. The older candidate-scope endpoint remains
temporarily available for cached-client compatibility, but it is not part of the current Angular sequence.
Clarification or refusal stops before any cards are returned. A confirmation outcome without `candidateScope` fails
closed in the browser rather than falling back to a second extraction. After the recruiter selects one card for every
reference, confirmed-scope validation receives only the selected candidate IDs, bounded message, and optional currently
selected approved-job ID. Java tenant-authorizes every selected candidate and optional approved job before returning
the application-owned `CONFIRMED_SCOPE_READY` state. A provider cannot refuse or advance this trusted workflow
transition. The comparison scope is populated only after that state; grounded comparison remains a separate recruiter
action and repeats candidate/job authorization. Request-version, message, selection, and job checks discard stale
asynchronous results. Starting a new language-first discovery also clears the earlier language-derived scope before the
request is sent, while leaving independently assembled picker-first choices untouched. No schema, migration, or
environment change is required.

### Tool-orchestrated language-first staging acceptance

Do not mark this slice accepted until all of the following pass on the deployed merged revision:

1. Confirm the backend container is healthy and the frontend loads without browser-console startup errors.
   On the staging VPS, use the deployment's two environment files consistently:

   ```bash
   cd /opt/nevgiu/deploy
   compose=(docker compose --env-file .env --env-file .images.env)
   "${compose[@]}" ps backend frontend
   "${compose[@]}" logs --tail=100 backend frontend
   ```

2. Submit a valid natural request naming two tenant candidates. Verify discovery succeeds, bounded confirmation cards
   appear, and no CV evidence or comparison answer is returned before confirmation.
3. Submit an unclear request. Verify the validated clarification message appears and candidate-card lookup does not
   continue.
4. Submit a sensitive or unrelated request. Verify it is refused before candidate-card lookup and no evidence is
   retrieved.
5. For the valid request, select exactly one card per reference and confirm. Verify confirmed-scope planning succeeds
   with no job selected and fills the visible comparison scope only after `CONFIRMED_SCOPE_READY`. Browser Network
   inspection should show `candidateIds` and no `jobId` in `/api/chat/tools/confirmed-scope`; it must contain no
   organization or role field.
6. Repeat with an explicitly selected approved job. Verify the confirmed-scope payload contains its `jobId`, changing
   the job invalidates the prior confirmed scope, and the grounded comparison uses the selected job criteria. Inspect
   the final `/api/chat/candidates/compare` request as well: the job-grounded flow contains that `jobId`, the no-job flow
   omits it, and neither request contains organization or role fields.
7. Review the visible candidates/question, submit candidate-only and job-grounded comparisons separately, and verify
   every factual claim is supported by citations for the confirmed candidates only.
8. After cards are visible, start a sensitive, unrelated, unclear, or different valid language-first request. Verify
   the previous language-derived cards, confirmed comparison question/job context, and result disappear immediately;
   the current job dropdown remains available for the new request, and refusal/clarification does not restore the old
   scope. Separately assembled picker-first chips must remain unchanged.
9. Recheck picker-first comparison, single-candidate chat, and candidate evaluation to detect regressions outside the
   new orchestration path.
10. Inspect privacy-safe backend logs. There must be no unexpected provider/orchestration error, cross-tenant access,
    confirmation bypass, raw prompt/CV content, or secret value.

Focused staging acceptance completed on 10 November 2026 at main revision `22d56c5` after PRs #146–#154. Valid
two-to-five-candidate discovery, bounded ambiguity cards, sensitive/unrelated refusal, optional-job and candidate-only
confirmed scope, grounded citations, privacy-safe diagnostics, and stale language-scope clearing passed. Picker-first
selection remained independent. Production repeated the focused workflow successfully on 10 November 2026 in release
`v0.32.0` at revision `1a5f247`, without an application runtime environment-variable, schema, migration, or Compose
change.

## Acceptance criteria

- Follow-up questions retain the correct conversation context.
- Search results respect organization and user permissions.
- Structured selections and natural-language names resolve only within the authenticated organization; missing names
  use generic not-found behavior and ambiguous names require recruiter selection.
- Each factual candidate claim links to supporting source data.
- Candidate comparison uses only the verified candidate IDs, the explicitly requested criteria, and cited authorized
  evidence; the model cannot invent or widen the candidate set.
- Ambiguous questions trigger clarification instead of guessed answers.
- No-result and service-failure states are explicit and actionable.
- Job-relevant questions unsupported by the evidence return a fixed insufficient-evidence response without citations.
- Unrelated questions and requests for protected or sensitive characteristics return a fixed out-of-scope response;
  those characteristics are neither disclosed nor inferred even when CV text mentions them.
- CV prompt injection cannot override system policy or expose unrelated records.
- Spoken queries produce an editable transcript before submission and follow the same authorization path as typed queries.
- Raw query audio is not retained by default, and retention exceptions require explicit policy and consent.
- Text-to-speech is optional, visibly synthetic, and never reads sensitive candidate details automatically.
- Voice characteristics are never used for identity, emotion, personality, accent, fluency, or candidate-fit scoring.
- The model cannot provide or override authenticated user, organization, role, or policy context used by tools.
- Read tools are bounded and minimized; command tools require a deterministic preview and explicit confirmation.
- Tool calls are authorized, limited, idempotent where necessary, and audited without logging sensitive content.
- A versioned evaluation suite measures retrieval recall, groundedness, citation correctness, completeness, refusal quality, tool selection, and cross-organization attack resistance.
- Automated semantic evaluators are calibrated against human-reviewed answers and never replace deterministic authorization or privacy assertions.
