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
- Evidence/page and excerpt limits are configurable with `CV_CHAT_MAX_EVIDENCE_PAGES` and
  `CV_CHAT_MAX_EXCERPT_CHARACTERS`; deployment defaults are `10` and `1200`. Semantic retrieval adds an independent
  rollout switch plus result/relevance bounds described below.
- The Angular candidate workspace exposes this bounded endpoint only to `ADMIN`, `RECRUITER`, and `REVIEWER`. It
  validates and submits one question for the selected candidate, displays progress and structured retryable failures,
  distinguishes fixed safe responses from evidence-backed answers, and links each citation to its document card.

The candidate-specific endpoint can now consume vector retrieval when its separate environment switch is enabled. It
does not yet provide multi-candidate search/comparison, persisted conversation memory, streaming, or speech controls.
Those remain explicit roadmap work and must not be inferred from this single-candidate retrieval slice.

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

Multi-candidate result cards, conversation memory, and the formal AI evaluation suite remain separate implementation
slices; acceptance of this bounded single-candidate path does not mark those capabilities complete.

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

If a referenced name does not exist in that organization, the request follows the generic not-found contract. If a
name matches more than one candidate, the application asks the recruiter to choose the intended record rather than
guessing. The recruiter may then compare the verified candidate set using any authorized criterion, including skills,
experience, education, leadership evidence, evaluation results, or fit for an explicitly selected job.

The model never performs global name resolution, invents or silently expands the candidate set, or supplies the
organization boundary. Comparison uses only authorized evidence for the resolved IDs, and every factual conclusion
requires candidate/document citations.

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
7. Build Spring AI tools for candidate search, tenant-scoped name resolution, candidate detail, filtering, comparison,
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
