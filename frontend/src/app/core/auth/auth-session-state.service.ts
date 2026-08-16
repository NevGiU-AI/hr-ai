import { Injectable, NgZone, inject } from '@angular/core';
import { Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { AuthUser } from './auth.models';
import { CsrfTokenStore } from './csrf-token.store';

const AUTH_CHANNEL = 'hr-ai-auth';
const AUTH_LOGOUT_STORAGE_KEY = 'hr-ai-auth-logout';

@Injectable({ providedIn: 'root' })
export class AuthSessionState {
  private readonly csrf = inject(CsrfTokenStore);
  private readonly router = inject(Router);
  private readonly zone = inject(NgZone);
  private readonly userSubject = new BehaviorSubject<AuthUser | null>(null);
  private readonly channel = typeof BroadcastChannel === 'undefined'
    ? null
    : new BroadcastChannel(AUTH_CHANNEL);

  readonly user$ = this.userSubject.asObservable();
  get user(): AuthUser | null { return this.userSubject.value; }

  constructor() {
    this.channel?.addEventListener('message', (event: MessageEvent) => {
      if (event.data?.type !== 'logout') return;
      this.handleRemoteLogout();
    });

    if (typeof window !== 'undefined') {
      window.addEventListener('storage', (event: StorageEvent) => {
        if (event.key !== AUTH_LOGOUT_STORAGE_KEY || event.newValue === null) return;
        this.handleRemoteLogout();
      });
    }
  }

  authenticate(user: AuthUser): void {
    this.userSubject.next(user);
  }

  expire(broadcast = true, redirect = true): void {
    const returnUrl = this.router.url;
    this.userSubject.next(null);
    this.csrf.token = '';

    if (broadcast) {
      this.channel?.postMessage({ type: 'logout' });
      if (typeof localStorage !== 'undefined') {
        localStorage.setItem(AUTH_LOGOUT_STORAGE_KEY, `${Date.now()}-${crypto.randomUUID()}`);
      }
    }
    if (redirect && !returnUrl.startsWith('/login')) {
      void this.router.navigate(['/login'], { queryParams: { returnUrl } });
    }
  }

  private handleRemoteLogout(): void {
    this.zone.run(() => this.expire(false, true));
  }
}
