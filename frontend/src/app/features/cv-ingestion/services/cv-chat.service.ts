import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environments';
import {
  CvChatCandidateSearchResponse,
  CvChatConversation,
  CvChatConversationHistory,
  CvChatConversationTurn,
  CvChatOrganizationDiscoveryResponse,
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

  /** Compares confirmed IDs and, when selected, one approved job; the server re-authorizes the complete scope. */
  compare(candidateIds: number[], message: string, jobId: number | null = null): Observable<CvChatComparisonResponse> {
    const body: { candidateIds: number[]; message: string; jobId?: number } = { candidateIds, message };
    if (jobId !== null) body.jobId = jobId;
    return this.http.post<CvChatComparisonResponse>(`${this.apiUrl}/chat/candidates/compare`, body);
  }

  /** Extracts references once and returns the cards from those exact tenant-scoped searches for confirmation. */
  discoverWithTools(message: string): Observable<CvChatToolDiscoveryResponse> {
    return this.http.post<CvChatToolDiscoveryResponse>(`${this.apiUrl}/chat/tools/discover`, { message });
  }

  discoverOrganizationCandidates(message: string): Observable<CvChatOrganizationDiscoveryResponse> {
    return this.http.post<CvChatOrganizationDiscoveryResponse>(
      `${this.apiUrl}/chat/tools/organization-discover`, { message });
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

  createConversation(candidateIds: number[], jobId: number | null): Observable<CvChatConversation> {
    const body: { candidateIds: number[]; jobId?: number } = { candidateIds };
    if (jobId !== null) body.jobId = jobId;
    return this.http.post<CvChatConversation>(`${this.apiUrl}/chat/conversations`, body);
  }

  getConversation(id: string): Observable<CvChatConversation> {
    return this.http.get<CvChatConversation>(`${this.apiUrl}/chat/conversations/${id}`);
  }

  getConversationHistory(id: string): Observable<CvChatConversationHistory> {
    return this.http.get<CvChatConversationHistory>(`${this.apiUrl}/chat/conversations/${id}/messages`);
  }

  askConversation(id: string, message: string): Observable<CvChatConversationTurn> {
    return this.http.post<CvChatConversationTurn>(`${this.apiUrl}/chat/conversations/${id}/messages`, { message });
  }

  deleteConversation(id: string): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/chat/conversations/${id}`);
  }
}
