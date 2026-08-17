import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AccountAdministrationComponent } from './account-administration.component';
import { AccountAdministrationService } from './account-administration.service';
import { Account } from './account.models';
import { AuthService } from '../../core/auth/auth.service';

describe('AccountAdministrationComponent', () => {
  let fixture: ComponentFixture<AccountAdministrationComponent>;
  let component: AccountAdministrationComponent;
  const existing: Account = {
    id: 1, email: 'admin@example.com', organizationId: 'default', enabled: true, roles: ['ADMIN'],
    locked: false, lockoutRemainingSeconds: 0,
  };
  const api = {
    findAll: jasmine.createSpy('findAll'),
    create: jasmine.createSpy('create'),
    updateRoles: jasmine.createSpy('updateRoles'),
    updateStatus: jasmine.createSpy('updateStatus'),
    revokeSessions: jasmine.createSpy('revokeSessions'),
    unlock: jasmine.createSpy('unlock'),
  };
  const auth = { user: { id: 1 } };

  beforeEach(async () => {
    api.findAll.and.returnValue(of([existing]));
    api.create.calls.reset();
    api.updateRoles.calls.reset();
    api.updateStatus.calls.reset();
    api.revokeSessions.calls.reset();
    api.unlock.calls.reset();
    await TestBed.configureTestingModule({
      imports: [AccountAdministrationComponent],
      providers: [
        { provide: AccountAdministrationService, useValue: api },
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AccountAdministrationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads and displays organization accounts', () => {
    expect(api.findAll).toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('tbody').textContent).toContain('admin@example.com');
  });

  it('creates an account with selected roles and clears the password', () => {
    const created: Account = {
      id: 2, email: 'recruiter@example.com', organizationId: 'default', enabled: true, roles: ['RECRUITER'],
      locked: false, lockoutRemainingSeconds: 0,
    };
    api.create.and.returnValue(of(created));
    component.form.patchValue({ email: created.email, password: 'secure-password' });

    component.submit();

    expect(api.create).toHaveBeenCalledWith({
      email: created.email, password: 'secure-password', roles: ['RECRUITER'],
    });
    expect(component.accounts.map((account) => account.email)).toContain(created.email);
    expect(component.form.controls.password.value).toBe('');
    expect(component.success).toContain(created.email);
  });

  it('requires at least one role', () => {
    component.form.patchValue({
      email: 'reviewer@example.com',
      password: 'secure-password',
      roles: { ADMIN: false, RECRUITER: false, REVIEWER: false, READ_ONLY: false },
    });
    component.submit();
    expect(api.create).not.toHaveBeenCalled();
    expect(component.error).toBe('Select at least one role.');
  });

  it('updates roles for another account', () => {
    const account: Account = {
      id: 2, email: 'user@example.com', organizationId: 'default', enabled: true, roles: ['RECRUITER'],
      locked: false, lockoutRemainingSeconds: 0,
    };
    component.accounts.push(account);
    component.roleDrafts.set(account.id, new Set(['REVIEWER']));
    api.updateRoles.and.returnValue(of({ ...account, roles: ['REVIEWER'] }));

    component.saveRoles(account);

    expect(api.updateRoles).toHaveBeenCalledWith(2, ['REVIEWER']);
    expect(component.accounts.find(({ id }) => id === 2)?.roles).toEqual(['REVIEWER']);
  });

  it('revokes sessions after confirmation', () => {
    const account: Account = {
      id: 2, email: 'user@example.com', organizationId: 'default', enabled: true, roles: ['RECRUITER'],
      locked: false, lockoutRemainingSeconds: 0,
    };
    spyOn(window, 'confirm').and.returnValue(true);
    api.revokeSessions.and.returnValue(of({ revokedSessions: 2 }));

    component.revokeSessions(account);

    expect(api.revokeSessions).toHaveBeenCalledWith(2);
    expect(component.success).toContain('2 active sessions');
  });

  it('unlocks a temporarily locked account', () => {
    const account: Account = {
      id: 2, email: 'user@example.com', organizationId: 'default', enabled: true, roles: ['RECRUITER'],
      locked: true, lockoutRemainingSeconds: 300,
    };
    component.accounts.push(account);
    api.unlock.and.returnValue(of({ ...account, locked: false, lockoutRemainingSeconds: 0 }));

    component.unlock(account);

    expect(api.unlock).toHaveBeenCalledWith(2);
    expect(component.accounts.find(({ id }) => id === 2)?.locked).toBeFalse();
  });
});
