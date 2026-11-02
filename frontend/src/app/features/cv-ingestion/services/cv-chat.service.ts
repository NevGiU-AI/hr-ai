import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environments';
import { CvChatCandidateSearchResponse, CvChatComparisonResponse, CvChatResponse } from '../models/cv-chat.models';

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
}
