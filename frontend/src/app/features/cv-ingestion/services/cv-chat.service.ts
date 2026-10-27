import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environments';
import { CvChatResponse } from '../models/cv-chat.models';

@Injectable({ providedIn: 'root' })
export class CvChatService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = environment.apiUrl;

  /** The authenticated session supplies tenant and role context; neither is accepted from browser-controlled input. */
  ask(candidateId: number, message: string): Observable<CvChatResponse> {
    return this.http.post<CvChatResponse>(`${this.apiUrl}/chat/candidates/${candidateId}`, { message });
  }
}
