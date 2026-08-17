import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environments';
import { SecurityAuditPage } from './security-audit.models';

@Injectable({ providedIn: 'root' })
export class SecurityAuditService {
  private readonly http = inject(HttpClient);

  findAll(page = 0, size = 50): Observable<SecurityAuditPage> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<SecurityAuditPage>(`${environment.apiUrl}/admin/security-events`, { params });
  }
}
