import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environments';
import { Account, CreateAccountRequest } from './account.models';

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
}
