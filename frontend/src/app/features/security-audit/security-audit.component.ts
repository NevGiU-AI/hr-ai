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

  actorIdentity(event: SecurityAuditEvent): string {
    if (event.actorEmail != null || event.actorUserId != null) {
      return this.identity(event.actorEmail, event.actorUserId);
    }

    return this.isPreAuthenticationEvent(event.eventType)
      ? 'Unauthenticated client'
      : 'System / unresolved';
  }

  /**
   * Formats the logical target without pretending that every security event targets an application account.
   * Account-management events use the dedicated target user columns, whereas candidate erasure keeps only a safe
   * candidate database ID after the personal candidate record has been permanently removed.
   */
  targetIdentity(event: SecurityAuditEvent): string {
    if (event.targetEmail != null || event.targetUserId != null) {
      return this.identity(event.targetEmail, event.targetUserId);
    }

    if (this.isCandidateEvent(event.eventType)) {
      // Candidate audit events deliberately avoid the user-account target columns. Their safe candidate database ID
      // is retained in the bounded details field after the candidate record itself has been erased.
      const candidateId = event.details?.match(/(?:^|;)candidateId=(\d+)(?:;|$)/)?.[1];
      if (candidateId) return `Candidate #${candidateId}`;
    }

    // Preserve the established fallback for true system events and older/malformed candidate audit records.
    return 'System / unresolved';
  }

  /** Converts the compact erasure audit counter into operator-friendly text without changing stored history. */
  eventDetails(event: SecurityAuditEvent): string {
    if (event.eventType === 'CANDIDATE_ERASED') {
      // Accept both the original key and the clearer key used by newer backend events so historical rows improve too.
      const originalCount = event.details
        ?.match(/(?:^|;)(?:originals|storedOriginalsDeleted)=(\d+)(?:;|$)/)?.[1];
      if (originalCount != null) {
        const count = Number(originalCount);
        if (count === 0) return 'No stored original file was associated';
        return `${count} stored original file${count === 1 ? '' : 's'} deleted`;
      }
    }

    if (event.eventType === 'CANDIDATE_METADATA_CORRECTED') {
      return 'Candidate metadata corrected';
    }

    if (event.eventType === 'CV_DOCUMENT_REPROCESSED') {
      const documentId = event.details?.match(/(?:^|;)documentId=(\d+)(?:;|$)/)?.[1];
      const invalidated = event.details?.match(/(?:^|;)evaluationsInvalidated=(\d+)(?:;|$)/)?.[1];
      if (documentId && invalidated != null) {
        const count = Number(invalidated);
        return `Document #${documentId} reprocessed; ${count} prior evaluation${count === 1 ? '' : 's'} marked stale`;
      }
    }

    if (event.eventType === 'CV_DOCUMENT_REPROCESS_FAILED') {
      const documentId = event.details?.match(/(?:^|;)documentId=(\d+)(?:;|$)/)?.[1];
      return documentId ? `Document #${documentId} could not be reprocessed` : 'Document reprocessing failed';
    }

    return event.details || '—';
  }

  private isPreAuthenticationEvent(eventType: string): boolean {
    return ['LOGIN_FAILED', 'LOGIN_THROTTLED', 'ACCOUNT_LOCKED'].includes(eventType);
  }

  private isCandidateEvent(eventType: string): boolean {
    // Candidate lifecycle events store a safe candidate ID in details rather than misusing account-target columns.
    return ['CANDIDATE_ERASED', 'CANDIDATE_ERASURE_DENIED', 'CANDIDATE_ERASURE_FAILED',
      'CANDIDATE_METADATA_CORRECTED', 'CV_DOCUMENT_REPROCESSED', 'CV_DOCUMENT_REPROCESS_FAILED'].includes(eventType);
  }
}
