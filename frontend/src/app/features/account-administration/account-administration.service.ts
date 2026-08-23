import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environments';
import { Account, AccountRole, CreateAccountRequest, RevokeSessionsResponse } from './account.models';

@Injectable({ providedIn: 'root' })
export class AccountAdministrationService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = environment.apiUrl;

  findAll(): Observable<Account[]> {
    return this.http.get<Account[]>(`${this.apiUrl}/admin/users`);
  }

  create(request: CreateAccountRequest): Observable<Account> {
    return this.http.post<Account>(`${this.apiUrl}/admin/users`, request);
  }

  updateRoles(accountId: number, roles: AccountRole[]): Observable<Account> {
    return this.http.put<Account>(`${this.apiUrl}/admin/users/${accountId}/roles`, { roles });
  }

  updateStatus(accountId: number, enabled: boolean): Observable<Account> {
    return this.http.put<Account>(`${this.apiUrl}/admin/users/${accountId}/status`, { enabled });
  }

  revokeSessions(accountId: number): Observable<RevokeSessionsResponse> {
    return this.http.post<RevokeSessionsResponse>(`${this.apiUrl}/admin/users/${accountId}/sessions/revoke`, {});
  }

  unlock(accountId: number): Observable<Account> {
    return this.http.post<Account>(`${this.apiUrl}/admin/users/${accountId}/lockout/unlock`, {});
  }

  resetPassword(accountId: number, newPassword: string): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/admin/users/${accountId}/password`, { newPassword });
  }
}
