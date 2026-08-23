import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from './auth.service';

@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './change-password.component.html',
  styleUrl: './change-password.component.scss',
})
export class ChangePasswordComponent {
  private readonly builder = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  readonly form = this.builder.nonNullable.group({
    currentPassword: ['', Validators.required],
    newPassword: ['', [Validators.required, Validators.minLength(12), Validators.maxLength(128)]],
    confirmation: ['', Validators.required],
  });
  submitting = false;
  error = '';

  submit(): void {
    const value = this.form.getRawValue();
    if (this.form.invalid || value.newPassword !== value.confirmation) {
      this.form.markAllAsTouched();
      this.error = value.newPassword !== value.confirmation ? 'New passwords do not match.' : '';
      return;
    }
    this.submitting = true;
    this.error = '';
    this.auth.changePassword(value.currentPassword, value.newPassword).pipe(
      finalize(() => this.submitting = false),
    ).subscribe({
      next: () => void this.router.navigate(['/login'], { queryParams: { passwordChanged: 'true' } }),
      error: (error: HttpErrorResponse) => this.error = error.error?.message || 'Password change failed.',
    });
  }
}
