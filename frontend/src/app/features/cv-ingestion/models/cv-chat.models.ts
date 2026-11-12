export interface CvChatCitation {
  reference: string;
  candidateId: number;
  documentId: number | null;
  pageNumber: number | null;
  excerpt: string;
}

export interface CvChatResponse {
  answer: string;
  citations: CvChatCitation[];
}

/** Minimal identity returned by tenant-scoped candidate discovery; it intentionally excludes contact and CV data. */
export interface CvChatCandidateOption {
  id: number;
  name: string;
  currentTitle: string | null;
}

export interface CvChatCandidateSearchResponse {
  candidates: CvChatCandidateOption[];
  truncated: boolean;
}

/** Grounded comparison returned only after the backend re-authorizes every explicitly selected candidate. */
export interface CvChatComparisonResponse {
  answer: string;
  candidates: CvChatCandidateOption[];
  citations: CvChatCitation[];
}

export type CvChatLanguageReferenceStatus = 'RESOLVED' | 'CHOICES' | 'NOT_FOUND';

/** Candidate cards proposed from natural language before the recruiter confirms any evidence scope. */
export interface CvChatLanguageReference {
  requestedName: string;
  status: CvChatLanguageReferenceStatus;
  candidates: CvChatCandidateOption[];
  truncated: boolean;
}

export interface CvChatLanguageScopeResponse {
  message: string;
  references: CvChatLanguageReference[];
}

export type CvChatToolWorkflowKind =
  | 'CANDIDATE_CONFIRMATION_REQUIRED'
  | 'CLARIFICATION_REQUIRED'
  | 'CONFIRMED_SCOPE_READY'
  | 'REFUSED';

/**
 * Application-validated workflow decision from the Spring AI read-tool orchestrator.
 * Candidate IDs and authorization facts deliberately remain outside the model-owned response.
 */
export interface CvChatToolOrchestrationOutcome {
  kind: CvChatToolWorkflowKind;
  message: string;
}

/**
 * One-pass discovery result. Candidate cards are Java-owned results from the same tenant-scoped searches that
 * produced the workflow decision; they are never model-generated scope.
 */
export interface CvChatToolDiscoveryResponse extends CvChatToolOrchestrationOutcome {
  candidateScope: CvChatLanguageScopeResponse | null;
}

export interface CvChatOrganizationDiscoveryResponse extends CvChatToolOrchestrationOutcome {
  candidates: Array<CvChatCandidateOption & { evidenceReferences: string[] }>;
  truncated: boolean;
}

export interface CvChatConversation {
  id: string;
  version: number;
  candidates: CvChatCandidateOption[];
  job: { id: number; title: string; level: string | null } | null;
  createdAt: string;
  updatedAt: string;
  expiresAt: string;
}

export interface CvChatTimelineMessage {
  role: 'USER' | 'ASSISTANT';
  content: string;
  citations?: CvChatCitation[];
}

export interface CvChatConversationHistory {
  messages: CvChatTimelineMessage[];
}

export interface CvChatConversationTurn {
  conversationId: string;
  version: number;
  answer: string;
  candidates: CvChatCandidateOption[];
  job: CvChatConversation['job'];
  citations: CvChatCitation[];
  expiresAt: string;
}
