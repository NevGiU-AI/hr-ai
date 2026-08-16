import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { EMPTY, Observable, catchError, exhaustMap, filter, fromEvent, map, merge, of, switchMap, tap, throwError, timer } from 'rxjs';
import { environment } from '../../../environments/environments';
import { AuthUser, CsrfResponse } from './auth.models';
import { AuthSessionState } from './auth-session-state.service';
import { CsrfTokenStore } from './csrf-token.store';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly csrf = inject(CsrfTokenStore);
  private readonly session = inject(AuthSessionState);
  private readonly apiUrl = environment.apiUrl;

  readonly user$ = this.session.user$;
  get user(): AuthUser | null { return this.session.user; }

  constructor() {
    merge(
      timer(5_000, 5_000),
      fromEvent(window, 'focus'),
      fromEvent(document, 'visibilitychange').pipe(
        filter(() => document.visibilityState === 'visible'),
      ),
    ).pipe(
      filter(() => this.session.user !== null),
      exhaustMap(() => this.http.get<AuthUser>(`${this.apiUrl}/auth/me`).pipe(
        tap((user) => this.session.authenticate(user)),
        catchError(() => EMPTY),
      )),
    ).subscribe();
  }

  ensureAuthenticated(): Observable<boolean> {
    return this.ensureCsrf().pipe(
      switchMap(() => this.http.get<AuthUser>(`${this.apiUrl}/auth/me`)),
      tap((user) => this.session.authenticate(user)),
      map(() => true),
      catchError(() => {
        this.session.expire(false, false);
        return of(false);
      }),
    );
  }

  login(email: string, password: string): Observable<AuthUser> {
    return this.ensureCsrf().pipe(
      switchMap(() => this.http.post<AuthUser>(`${this.apiUrl}/auth/login`, { email, password })),
      tap((user) => this.session.authenticate(user)),
    );
  }

  logout(): Observable<void> {
    return this.ensureCsrf().pipe(
      switchMap(() => this.http.post<void>(`${this.apiUrl}/auth/logout`, {})),
      tap(() => this.session.expire(true, false)),
      catchError((error) => {
        this.session.expire(true, false);
        return throwError(() => error);
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
