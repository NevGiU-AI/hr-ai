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
