import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { AccountAdministrationService } from './account-administration.service';
import { ACCOUNT_ROLES, Account, AccountRole } from './account.models';

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
      next: (accounts) => this.accounts = accounts,
      error: () => this.error = 'Accounts could not be loaded. Please try again.',
    });
  }
}
