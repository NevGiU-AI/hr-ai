import { CommonModule } from '@angular/common';
import { Component, OnInit, inject } from '@angular/core';
import { finalize } from 'rxjs';
import { SecurityAuditEvent } from './security-audit.models';
import { SecurityAuditService } from './security-audit.service';

@Component({
  selector: 'app-security-audit',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './security-audit.component.html',
  styleUrl: './security-audit.component.scss',
})
export class SecurityAuditComponent implements OnInit {
  private readonly api = inject(SecurityAuditService);
  events: SecurityAuditEvent[] = [];
  page = 0;
  totalPages = 0;
  totalElements = 0;
  loading = true;
  error = '';

  ngOnInit(): void { this.load(0); }

  load(page: number): void {
    this.loading = true;
    this.error = '';
    this.api.findAll(page).pipe(finalize(() => this.loading = false)).subscribe({
      next: result => {
        this.events = result.content;
        this.page = result.page;
        this.totalPages = result.totalPages;
        this.totalElements = result.totalElements;
      },
      error: () => this.error = 'Security events could not be loaded. Please try again.',
    });
  }

  identity(email: string | null, id: number | null): string {
    return email ?? (id == null ? 'System / unresolved' : `User #${id}`);
  }
}
