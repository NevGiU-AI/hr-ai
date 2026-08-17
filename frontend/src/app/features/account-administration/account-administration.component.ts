import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable, finalize } from 'rxjs';
import { AccountAdministrationService } from './account-administration.service';
import { ACCOUNT_ROLES, Account, AccountRole } from './account.models';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-account-administration',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './account-administration.component.html',
  styleUrl: './account-administration.component.scss',
})
export class AccountAdministrationComponent implements OnInit {
  private readonly accountsApi = inject(AccountAdministrationService);
  private readonly formBuilder = inject(FormBuilder);
  private readonly auth = inject(AuthService);

  readonly availableRoles = ACCOUNT_ROLES;
  readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]],
    roles: this.formBuilder.nonNullable.group({
      ADMIN: false,
      RECRUITER: true,
      REVIEWER: false,
      READ_ONLY: false,
    }),
  });

  accounts: Account[] = [];
  loading = true;
  submitting = false;
  passwordVisible = false;
  error = '';
  success = '';
  busyAccountId: number | null = null;
  readonly roleDrafts = new Map<number, Set<AccountRole>>();

  get currentUserId(): number | null { return this.auth.user?.id ?? null; }

  ngOnInit(): void {
    this.loadAccounts();
  }

  togglePasswordVisibility(): void {
    this.passwordVisible = !this.passwordVisible;
  }

  submit(): void {
    const roles = this.selectedRoles();
    if (this.form.invalid || roles.length === 0 || this.submitting) {
      this.form.markAllAsTouched();
      if (roles.length === 0) this.error = 'Select at least one role.';
      return;
    }

    this.submitting = true;
    this.error = '';
    this.success = '';
    const { email, password } = this.form.getRawValue();
    this.accountsApi.create({ email, password, roles }).pipe(
      finalize(() => this.submitting = false),
    ).subscribe({
      next: (account) => {
        this.accounts = [...this.accounts, account].sort((a, b) => a.email.localeCompare(b.email));
        this.roleDrafts.set(account.id, new Set(account.roles));
        this.form.reset({
          email: '', password: '',
          roles: { ADMIN: false, RECRUITER: true, REVIEWER: false, READ_ONLY: false },
        });
        this.passwordVisible = false;
        this.success = `Account created for ${account.email}.`;
      },
      error: (error: HttpErrorResponse) => {
        this.error = error.error?.message || 'Account creation failed. Please try again.';
      },
    });
  }

  selectedRoles(): AccountRole[] {
    const selected = this.form.controls.roles.getRawValue();
    return this.availableRoles.filter((role) => selected[role]);
  }

  private loadAccounts(): void {
    this.loading = true;
    this.accountsApi.findAll().pipe(finalize(() => this.loading = false)).subscribe({
      next: (accounts) => {
        this.accounts = accounts;
        accounts.forEach((account) => this.roleDrafts.set(account.id, new Set(account.roles)));
      },
      error: () => this.error = 'Accounts could not be loaded. Please try again.',
    });
  }

  roleSelected(account: Account, role: AccountRole): boolean {
    return this.roleDrafts.get(account.id)?.has(role) ?? false;
  }

  toggleAccountRole(account: Account, role: AccountRole, selected: boolean): void {
    const roles = new Set(this.roleDrafts.get(account.id) ?? account.roles);
    selected ? roles.add(role) : roles.delete(role);
    this.roleDrafts.set(account.id, roles);
  }

  saveRoles(account: Account): void {
    const roles = [...(this.roleDrafts.get(account.id) ?? [])];
    if (roles.length === 0) {
      this.error = 'Select at least one role.';
      return;
    }
    this.runAccountAction(account.id, this.accountsApi.updateRoles(account.id, roles),
      `Roles updated for ${account.email}.`);
  }

  toggleStatus(account: Account): void {
    const enabled = !account.enabled;
    if (!enabled && !window.confirm(`Disable ${account.email} and sign out all active sessions?`)) return;
    this.runAccountAction(account.id, this.accountsApi.updateStatus(account.id, enabled),
      `${account.email} is now ${enabled ? 'enabled' : 'disabled'}.`);
  }

  revokeSessions(account: Account): void {
    if (!window.confirm(`Sign out ${account.email} from all active sessions?`)) return;
    this.busyAccountId = account.id;
    this.clearMessages();
    this.accountsApi.revokeSessions(account.id).pipe(
      finalize(() => this.busyAccountId = null),
    ).subscribe({
      next: ({ revokedSessions }) => {
        this.success = `${revokedSessions} active session${revokedSessions === 1 ? '' : 's'} revoked for ${account.email}.`;
      },
      error: (error: HttpErrorResponse) => this.showActionError(error),
    });
  }

  rolesChanged(account: Account): boolean {
    const draft = [...(this.roleDrafts.get(account.id) ?? [])].sort();
    return draft.join(',') !== [...account.roles].sort().join(',');
  }

  private runAccountAction(accountId: number, operation: Observable<Account>, message: string): void {
    this.busyAccountId = accountId;
    this.clearMessages();
    operation.pipe(finalize(() => this.busyAccountId = null)).subscribe({
      next: (updated) => {
        this.accounts = this.accounts.map((account) => account.id === updated.id ? updated : account);
        this.roleDrafts.set(updated.id, new Set(updated.roles));
        this.success = message;
      },
      error: (error: HttpErrorResponse) => this.showActionError(error),
    });
  }

  private clearMessages(): void {
    this.error = '';
    this.success = '';
  }

  private showActionError(error: HttpErrorResponse): void {
    this.error = error.error?.message || 'Account update failed. Please try again.';
  }
}
