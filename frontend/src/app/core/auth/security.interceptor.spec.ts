import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthSessionState } from './auth-session-state.service';
import { CsrfTokenStore } from './csrf-token.store';
import { securityInterceptor } from './security.interceptor';

describe('securityInterceptor', () => {
  let http: HttpClient;
  let requests: HttpTestingController;
  let csrf: CsrfTokenStore;
  let session: AuthSessionState;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([securityInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    http = TestBed.inject(HttpClient);
    requests = TestBed.inject(HttpTestingController);
    csrf = TestBed.inject(CsrfTokenStore);
    session = TestBed.inject(AuthSessionState);
    csrf.token = 'csrf-token';
  });

  afterEach(() => requests.verify());

  it('sends credentials and CSRF protection for state-changing requests', () => {
    http.post('/api/test', {}).subscribe();
    const request = requests.expectOne('/api/test');
    expect(request.request.withCredentials).toBeTrue();
    expect(request.request.headers.get('X-XSRF-TOKEN')).toBe('csrf-token');
    request.flush({});
  });

  it('does not add a CSRF header to safe requests', () => {
    http.get('/api/test').subscribe();
    const request = requests.expectOne('/api/test');
    expect(request.request.withCredentials).toBeTrue();
    expect(request.request.headers.has('X-XSRF-TOKEN')).toBeFalse();
    request.flush({});
  });

  it('expires the cached session when a business request returns 401', () => {
    const expire = spyOn(session, 'expire');

    http.get('/api/candidates').subscribe({ error: () => undefined });
    requests.expectOne('/api/candidates').flush(
      { message: 'Authentication required' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(expire).toHaveBeenCalledWith(true, true);
  });

  it('leaves login failures for the login form to display', () => {
    const expire = spyOn(session, 'expire');

    http.post('/api/auth/login', {}).subscribe({ error: () => undefined });
    requests.expectOne('/api/auth/login').flush(
      { message: 'Invalid email or password' },
      { status: 401, statusText: 'Unauthorized' },
    );

    expect(expire).not.toHaveBeenCalled();
  });
});
