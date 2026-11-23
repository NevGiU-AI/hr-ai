import { discardPeriodicTasks, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environments';
import { AuthSessionState } from './auth-session-state.service';
import { AuthService } from './auth.service';
import { CsrfTokenStore } from './csrf-token.store';

describe('AuthService session heartbeat', () => {
  let auth: AuthService;
  let session: AuthSessionState;
  let csrf: CsrfTokenStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    session = TestBed.inject(AuthSessionState);
    csrf = TestBed.inject(CsrfTokenStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('revalidates an authenticated session without user interaction', fakeAsync(() => {
    auth = TestBed.inject(AuthService);
    session.authenticate({ id: 1, email: 'admin@example.com', organizationId: 'staging', roles: ['ADMIN'] });

    tick(5_000);

    const request = http.expectOne(`${environment.apiUrl}/auth/me`);
    expect(request.request.method).toBe('GET');
    request.flush({ id: 1, email: 'admin@example.com', organizationId: 'staging', roles: ['ADMIN'] });
    session.expire(false, false);
    discardPeriodicTasks();
    void auth;
  }));

  it('revalidates immediately when a background tab receives focus', fakeAsync(() => {
    auth = TestBed.inject(AuthService);
    session.authenticate({ id: 1, email: 'admin@example.com', organizationId: 'staging', roles: ['ADMIN'] });

    window.dispatchEvent(new Event('focus'));

    const request = http.expectOne(`${environment.apiUrl}/auth/me`);
    expect(request.request.method).toBe('GET');
    request.flush({ id: 1, email: 'admin@example.com', organizationId: 'staging', roles: ['ADMIN'] });
    session.expire(false, false);
    discardPeriodicTasks();
    void auth;
  }));

  it('preserves a cached authenticated user when revalidation is temporarily unavailable', fakeAsync(() => {
    auth = TestBed.inject(AuthService);
    csrf.token = 'existing-csrf-token';
    session.authenticate({ id: 1, email: 'admin@example.com', organizationId: 'staging', roles: ['ADMIN'] });
    let authenticated: boolean | undefined;

    auth.ensureAuthenticated().subscribe((value) => authenticated = value);
    http.expectOne(`${environment.apiUrl}/auth/me`).flush(
      { message: 'Service temporarily unavailable' },
      { status: 503, statusText: 'Service Unavailable' },
    );
    tick();

    expect(authenticated).toBeTrue();
    expect(session.user?.email).toBe('admin@example.com');
    session.expire(false, false);
    discardPeriodicTasks();
  }));

  it('expires a cached user when revalidation confirms the session is unauthorized', fakeAsync(() => {
    auth = TestBed.inject(AuthService);
    csrf.token = 'existing-csrf-token';
    session.authenticate({ id: 1, email: 'admin@example.com', organizationId: 'staging', roles: ['ADMIN'] });
    let authenticated: boolean | undefined;

    auth.ensureAuthenticated().subscribe((value) => authenticated = value);
    http.expectOne(`${environment.apiUrl}/auth/me`).flush(
      { message: 'Authentication required' },
      { status: 401, statusText: 'Unauthorized' },
    );
    tick();

    expect(authenticated).toBeFalse();
    expect(session.user).toBeNull();
    discardPeriodicTasks();
  }));

  it('does not invent an authenticated identity after a transient cold-start failure', fakeAsync(() => {
    auth = TestBed.inject(AuthService);
    csrf.token = 'existing-csrf-token';
    let failureStatus: number | undefined;

    auth.ensureAuthenticated().subscribe({ error: (error) => failureStatus = error.status });
    http.expectOne(`${environment.apiUrl}/auth/me`).flush(
      { message: 'Bad gateway' },
      { status: 502, statusText: 'Bad Gateway' },
    );
    tick();

    expect(failureStatus).toBe(502);
    expect(session.user).toBeNull();
    discardPeriodicTasks();
  }));
});
