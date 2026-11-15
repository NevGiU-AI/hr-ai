import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { AuthSessionState } from './auth-session-state.service';
import { CsrfTokenStore } from './csrf-token.store';
import { ACTIVE_CV_CHAT_CONVERSATION_STORAGE_KEY } from '../storage/application-storage-keys';

@Component({ template: '' })
class TestPageComponent {}

describe('AuthSessionState', () => {
  let state: AuthSessionState;
  let csrf: CsrfTokenStore;
  let router: Router;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([
        { path: 'candidates/import', component: TestPageComponent },
        { path: 'login', component: TestPageComponent },
      ])],
    });
    state = TestBed.inject(AuthSessionState);
    csrf = TestBed.inject(CsrfTokenStore);
    router = TestBed.inject(Router);
  });

  afterEach(() => localStorage.clear());

  it('clears user and CSRF state when the current tab logs out', () => {
    state.authenticate({ id: 1, email: 'admin@example.com', organizationId: 'staging', roles: ['ADMIN'] });
    csrf.token = 'csrf-token';
    localStorage.setItem(ACTIVE_CV_CHAT_CONVERSATION_STORAGE_KEY, 'owned-conversation');

    state.expire(false, false);

    expect(state.user).toBeNull();
    expect(csrf.token).toBe('');
    expect(localStorage.getItem(ACTIVE_CV_CHAT_CONVERSATION_STORAGE_KEY)).toBeNull();
  });

  it('clears owner-bound browser state when authentication changes account', () => {
    state.authenticate({ id: 1, email: 'recruiter@example.com', organizationId: 'staging', roles: ['RECRUITER'] });
    localStorage.setItem(ACTIVE_CV_CHAT_CONVERSATION_STORAGE_KEY, 'recruiter-conversation');

    state.authenticate({ id: 2, email: 'admin@example.com', organizationId: 'staging', roles: ['ADMIN'] });

    expect(localStorage.getItem(ACTIVE_CV_CHAT_CONVERSATION_STORAGE_KEY)).toBeNull();
  });

  it('preserves owner-bound browser state when the same account is revalidated', () => {
    const user = { id: 1, email: 'recruiter@example.com', organizationId: 'staging', roles: ['RECRUITER'] };
    state.authenticate(user);
    localStorage.setItem(ACTIVE_CV_CHAT_CONVERSATION_STORAGE_KEY, 'recruiter-conversation');

    state.authenticate(user);

    expect(localStorage.getItem(ACTIVE_CV_CHAT_CONVERSATION_STORAGE_KEY)).toBe('recruiter-conversation');
  });

  it('reacts to a logout broadcast from another tab', async () => {
    state.authenticate({ id: 1, email: 'admin@example.com', organizationId: 'staging', roles: ['ADMIN'] });
    csrf.token = 'csrf-token';
    await router.navigateByUrl('/candidates/import');

    const channel = (state as unknown as { channel: BroadcastChannel | null }).channel;
    channel?.dispatchEvent(new MessageEvent('message', { data: { type: 'logout' } }));
    await Promise.resolve();

    expect(state.user).toBeNull();
    expect(csrf.token).toBe('');
    expect(router.url).toContain('/login');
  });

  it('reacts immediately to the cross-tab local-storage logout signal', async () => {
    state.authenticate({ id: 1, email: 'admin@example.com', organizationId: 'staging', roles: ['ADMIN'] });
    csrf.token = 'csrf-token';
    await router.navigateByUrl('/candidates/import');

    window.dispatchEvent(new StorageEvent('storage', {
      key: 'hr-ai-auth-logout',
      newValue: 'logout-event-id',
    }));
    await Promise.resolve();

    expect(state.user).toBeNull();
    expect(csrf.token).toBe('');
    expect(router.url).toContain('/login');
  });
});
