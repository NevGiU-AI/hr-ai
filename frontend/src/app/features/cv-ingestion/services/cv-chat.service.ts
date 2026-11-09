import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environments';
import {
  CvChatCandidateSearchResponse,
  CvChatComparisonResponse,
  CvChatResponse,
  CvChatToolDiscoveryResponse,
  CvChatToolOrchestrationOutcome,
} from '../models/cv-chat.models';

@Injectable({ providedIn: 'root' })
export class CvChatService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = environment.apiUrl;

  /** The authenticated session supplies tenant and role context; neither is accepted from browser-controlled input. */
  ask(candidateId: number, message: string): Observable<CvChatResponse> {
    return this.http.post<CvChatResponse>(`${this.apiUrl}/chat/candidates/${candidateId}`, { message });
  }

  /** Finds bounded partial-name matches; the browser must still submit an explicit candidate ID for chat. */
  searchCandidates(query: string): Observable<CvChatCandidateSearchResponse> {
    return this.http.post<CvChatCandidateSearchResponse>(`${this.apiUrl}/chat/candidates/search`, { query });
  }

  /** Compares only browser-confirmed IDs; the server re-authorizes the entire set in the authenticated tenant. */
  compare(candidateIds: number[], message: string): Observable<CvChatComparisonResponse> {
    return this.http.post<CvChatComparisonResponse>(`${this.apiUrl}/chat/candidates/compare`, {
      candidateIds,
      message,
    });
  }

  /** Extracts references once and returns the cards from those exact tenant-scoped searches for confirmation. */
  discoverWithTools(message: string): Observable<CvChatToolDiscoveryResponse> {
    return this.http.post<CvChatToolDiscoveryResponse>(`${this.apiUrl}/chat/tools/discover`, { message });
  }

  /**
   * Plans only inside IDs explicitly confirmed in the UI. The optional approved job is omitted when no job is selected.
   */
  planConfirmedScope(
    message: string,
    candidateIds: number[],
    jobId: number | null,
  ): Observable<CvChatToolOrchestrationOutcome> {
    const body: { message: string; candidateIds: number[]; jobId?: number } = { message, candidateIds };
    if (jobId !== null) body.jobId = jobId;
    return this.http.post<CvChatToolOrchestrationOutcome>(`${this.apiUrl}/chat/tools/confirmed-scope`, body);
  }
}
