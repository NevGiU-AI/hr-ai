import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AccountAdministrationComponent } from './account-administration.component';
import { AccountAdministrationService } from './account-administration.service';
import { Account } from './account.models';

describe('AccountAdministrationComponent', () => {
  let fixture: ComponentFixture<AccountAdministrationComponent>;
  let component: AccountAdministrationComponent;
  const existing: Account = {
    id: 1, email: 'admin@example.com', organizationId: 'default', enabled: true, roles: ['ADMIN'],
  };
  const api = {
    findAll: jasmine.createSpy('findAll'),
    create: jasmine.createSpy('create'),
  };

  beforeEach(async () => {
    api.findAll.and.returnValue(of([existing]));
    api.create.calls.reset();
    await TestBed.configureTestingModule({
      imports: [AccountAdministrationComponent],
      providers: [{ provide: AccountAdministrationService, useValue: api }],
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
});
