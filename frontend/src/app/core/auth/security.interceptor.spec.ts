import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CsrfTokenStore } from './csrf-token.store';
import { securityInterceptor } from './security.interceptor';

describe('securityInterceptor', () => {
  let http: HttpClient;
  let requests: HttpTestingController;
  let csrf: CsrfTokenStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([securityInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    requests = TestBed.inject(HttpTestingController);
    csrf = TestBed.inject(CsrfTokenStore);
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
});
