import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environments';
import { AccountAdministrationService } from './account-administration.service';
import { CreateAccountRequest } from './account.models';

describe('AccountAdministrationService', () => {
  let service: AccountAdministrationService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(AccountAdministrationService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists accounts in the authenticated administrator organization', () => {
    service.findAll().subscribe();
    const request = http.expectOne(`${environment.apiUrl}/admin/users`);
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('creates an account without allowing the client to select an organization', () => {
    const payload: CreateAccountRequest = {
      email: 'recruiter@example.com', password: 'secure-password', roles: ['RECRUITER'],
    };
    service.create(payload).subscribe();
    const request = http.expectOne(`${environment.apiUrl}/admin/users`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);
    expect(request.request.body.organizationId).toBeUndefined();
    request.flush({ id: 2, organizationId: 'default', enabled: true, ...payload });
  });
});
