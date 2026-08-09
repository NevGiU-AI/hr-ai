import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { BehaviorSubject, Observable, catchError, map, of, switchMap, tap } from 'rxjs';
import { environment } from '../../../environments/environments';
import { AuthUser, CsrfResponse } from './auth.models';
import { CsrfTokenStore } from './csrf-token.store';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfTokenStore);
  private readonly apiUrl = environment.apiUrl;
  private readonly userSubject = new BehaviorSubject<AuthUser | null>(null);

  readonly user$ = this.userSubject.asObservable();
  get user(): AuthUser | null { return this.userSubject.value; }

  ensureAuthenticated(): Observable<boolean> {
    if (this.user) return of(true);
    return this.ensureCsrf().pipe(
      switchMap(() => this.http.get<AuthUser>(`${this.apiUrl}/auth/me`)),
      tap((user) => this.userSubject.next(user)),
      map(() => true),
      catchError(() => {
        this.userSubject.next(null);
        return of(false);
      }),
    );
  }

  login(email: string, password: string): Observable<AuthUser> {
    return this.ensureCsrf().pipe(
      switchMap(() => this.http.post<AuthUser>(`${this.apiUrl}/auth/login`, { email, password })),
      tap((user) => this.userSubject.next(user)),
    );
  }

  logout(): Observable<void> {
    return this.ensureCsrf().pipe(
      switchMap(() => this.http.post<void>(`${this.apiUrl}/auth/logout`, {})),
      tap(() => {
        this.userSubject.next(null);
        this.csrf.token = '';
      }),
    );
  }

  private ensureCsrf(): Observable<void> {
    if (this.csrf.token) return of(void 0);
    return this.http.get<CsrfResponse>(`${this.apiUrl}/auth/csrf`).pipe(
      tap((response) => {
        this.csrf.token = response.token;
        this.csrf.headerName = response.headerName;
      }),
      map(() => void 0),
    );
  }
}
