# CV Database Chat and Prompting

## Objective

Allow authenticated HR users to retrieve, summarize, filter, and compare indexed candidates using natural-language queries.

## Implemented foundation

The first backend slice supports an imperative question about one explicitly selected candidate:

- `POST /api/chat/candidates/{candidateId}` accepts a validated text question.
- `ADMIN`, `RECRUITER`, and `REVIEWER` may call it; `READ_ONLY` may not.
- The authenticated principal supplies the organization. The model cannot choose or override it, and missing and
  cross-organization candidate identifiers both return `404`.
- The backend loads a short evidence snapshot in a read-only transaction, closes that transaction, and only then makes
  the slower Spring AI provider call.
- OCR documents contribute bounded page evidence such as `[CV-12-P2]`. Native extraction contributes document-level
  evidence such as `[CV-12]`.
- Every returned answer must contain at least one supplied reference. Unknown, invented, or absent citations cause a
  stable `502` response instead of returning an ungrounded answer.
- Evidence/page and excerpt limits are configurable with `CV_CHAT_MAX_EVIDENCE_PAGES` and
  `CV_CHAT_MAX_EXCERPT_CHARACTERS`; deployment defaults are `10` and `1200`.

This slice does not yet provide vector indexing, multi-candidate search/comparison, persisted conversation memory, the
Angular chat interface, streaming, or speech controls. Those remain explicit roadmap work and must not be inferred from
the presence of this endpoint.

## Supported interactions

- Find candidates by skills, experience, location, education, title, employer, or evaluation metric.
- Apply multiple filters and refine them over follow-up turns.
- Ask questions about a specific candidate.
- Compare selected candidates against explicit criteria.
- Return concise summaries with references to candidate profiles.
- Ask for clarification when the request is ambiguous.
- Explain when no candidate matches or when an internal operation fails.

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
2. Enable and configure the Spring AI pgvector integration.
3. Design the chunking strategy and metadata schema for candidate, organization, CV, and job identifiers.
4. Index normalized CV content and evaluation summaries after ingestion.
5. Implement organization- and role-based retrieval filters before semantic search.
6. Build a retrieval service that returns relevant chunks plus structured candidate data.
7. Build Spring AI tools for candidate search, candidate detail, filtering, comparison, evidence, document status, and governed dashboard aggregates. Make tools request-scoped, inject authenticated user and organization context outside model-controlled arguments, and separate bounded query tools from preview-and-confirm command tools.
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
- Each factual candidate claim links to supporting source data.
- Candidate comparison uses the requested criteria consistently.
- Ambiguous questions trigger clarification instead of guessed answers.
- No-result and service-failure states are explicit and actionable.
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
