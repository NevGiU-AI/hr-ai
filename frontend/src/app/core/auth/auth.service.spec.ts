import { discardPeriodicTasks, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { environment } from '../../../environments/environments';
import { AuthSessionState } from './auth-session-state.service';
import { AuthService } from './auth.service';

describe('AuthService session heartbeat', () => {
  let auth: AuthService;
  let session: AuthSessionState;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    session = TestBed.inject(AuthSessionState);
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
});
