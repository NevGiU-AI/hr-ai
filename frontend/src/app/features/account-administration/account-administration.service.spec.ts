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

  it('updates roles, status, revokes sessions, and unlocks a specific account', () => {
    service.updateRoles(2, ['REVIEWER']).subscribe();
    const roles = http.expectOne(`${environment.apiUrl}/admin/users/2/roles`);
    expect(roles.request.method).toBe('PUT');
    expect(roles.request.body).toEqual({ roles: ['REVIEWER'] });
    roles.flush({ id: 2, email: 'user@example.com', organizationId: 'default', enabled: true, roles: ['REVIEWER'] });

    service.updateStatus(2, false).subscribe();
    const status = http.expectOne(`${environment.apiUrl}/admin/users/2/status`);
    expect(status.request.method).toBe('PUT');
    expect(status.request.body).toEqual({ enabled: false });
    status.flush({ id: 2, email: 'user@example.com', organizationId: 'default', enabled: false, roles: ['REVIEWER'] });

    service.revokeSessions(2).subscribe();
    const sessions = http.expectOne(`${environment.apiUrl}/admin/users/2/sessions/revoke`);
    expect(sessions.request.method).toBe('POST');
    sessions.flush({ revokedSessions: 1 });

    service.unlock(2).subscribe();
    const unlock = http.expectOne(`${environment.apiUrl}/admin/users/2/lockout/unlock`);
    expect(unlock.request.method).toBe('POST');
    unlock.flush({ id: 2, email: 'user@example.com', organizationId: 'default', enabled: true,
      roles: ['REVIEWER'], locked: false, lockoutRemainingSeconds: 0 });
  });
});
