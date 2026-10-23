import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize, forkJoin, switchMap } from 'rxjs';
import { AuthService } from '../../../../core/auth/auth.service';
import {
  ApiError,
  Candidate,
  CandidateDetails,
  CandidateDocumentDetails,
  CandidateMetadataUpdate,
  CvArchiveImportResult,
  CvImportResult,
  EvaluationHistoryItem,
  EvaluationResponse,
  Job,
} from '../../models/cv-ingestion.models';
import { CvIngestionService } from '../../services/cv-ingestion.service';

@Component({
  selector: 'app-cv-workspace',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './cv-workspace.component.html',
  styleUrl: './cv-workspace.component.scss',
})
export class CvWorkspaceComponent implements OnInit {
  private readonly service = inject(CvIngestionService);
  private readonly auth = inject(AuthService);

  pdfFile: File | null = null;
  archiveFile: File | null = null;
  result: CvArchiveImportResult | null = null;
  candidates: Candidate[] = [];
  jobs: Job[] = [];
  selectedCandidateId: number | null = null;
  selectedJobId: number | null = null;
  evaluation: EvaluationResponse | null = null;
  evaluationHistory: EvaluationHistoryItem[] = [];
  candidateDetails: CandidateDetails | null = null;
  metadataDraft: CandidateMetadataUpdate | null = null;
  busyAction: 'pdf' | 'archive' | 'initial' | 'evaluation' | 'erasure' | 'details' | 'document' | 'metadata' | null = null;
  error = '';
  notice = '';

  readonly maxPdfBytes = 20 * 1024 * 1024;
  readonly maxArchiveBytes = 100 * 1024 * 1024;

  get isAdministrator(): boolean {
    return this.auth.user?.roles.includes('ADMIN') ?? false;
  }

  get candidateUnderLegalHold(): boolean {
    return this.candidateDetails?.documents.some((document) => document.legalHold) ?? false;
  }

  ngOnInit(): void {
    this.refreshSelections();
  }

  selectPdf(event: Event): void {
    this.pdfFile = this.selectedFile(event, '.pdf', this.maxPdfBytes);
  }

  selectArchive(event: Event): void {
    this.archiveFile = this.selectedFile(event, '.zip', this.maxArchiveBytes);
  }

  uploadPdf(): void {
    if (!this.pdfFile || this.busyAction) return;
    this.start('pdf');
    this.service.uploadPdf(this.pdfFile).pipe(finalize(() => this.busyAction = null)).subscribe({
      next: (item) => {
        this.result = this.asArchiveResult(item);
        this.pdfFile = null;
        this.refreshSelections(item.candidateId);
      },
      error: (error) => this.handleError(error),
    });
  }

  uploadArchive(): void {
    if (!this.archiveFile || this.busyAction) return;
    this.start('archive');
    this.service.uploadArchive(this.archiveFile).pipe(finalize(() => this.busyAction = null)).subscribe({
      next: (result) => {
        this.result = result;
        this.archiveFile = null;
        this.refreshSelections(this.firstCandidateId(result));
      },
      error: (error) => this.handleError(error),
    });
  }

  importInitial(): void {
    if (this.busyAction) return;
    this.start('initial');
    this.service.importInitial().pipe(finalize(() => this.busyAction = null)).subscribe({
      next: (result) => {
        this.result = result;
        this.refreshSelections(this.firstCandidateId(result));
      },
      error: (error) => this.handleError(error),
    });
  }

  evaluate(): void {
    if (!this.selectedCandidateId || !this.selectedJobId || this.busyAction) return;
    this.start('evaluation');
    this.evaluation = null;
    this.service.evaluate(this.selectedCandidateId, this.selectedJobId)
      .pipe(finalize(() => this.busyAction = null))
      .subscribe({
        next: (result) => {
          this.evaluation = result;
          this.loadEvaluationHistory();
        },
        error: (error) => this.handleError(error),
      });
  }

  selectCandidate(candidateId: number | null): void {
    this.selectedCandidateId = candidateId;
    this.evaluation = null;
    this.loadCandidateDetails();
  }

  loadCandidateDetails(): void {
    if (!this.selectedCandidateId) {
      this.candidateDetails = null;
      this.evaluationHistory = [];
      return;
    }
    this.start('details');
    forkJoin({
      details: this.service.getCandidateDetails(this.selectedCandidateId),
      evaluations: this.service.getEvaluationHistory(this.selectedCandidateId),
    })
      .pipe(finalize(() => this.busyAction = null))
      .subscribe({
        next: ({ details, evaluations }) => {
          this.candidateDetails = details;
          this.evaluationHistory = evaluations;
          this.metadataDraft = null;
        },
        error: (error) => this.handleError(error),
      });
  }

  editMetadata(): void {
    const details = this.candidateDetails;
    if (!details?.correctionAllowed || this.busyAction) return;
    this.notice = '';
    this.metadataDraft = {
      name: details.name,
      email: details.email,
      location: details.location,
      currentTitle: details.currentTitle,
      yearsExperience: details.yearsExperience,
    };
  }

  saveMetadata(): void {
    if (!this.selectedCandidateId || !this.metadataDraft?.name.trim() || this.busyAction) return;
    if (this.metadataIsUnchanged(this.metadataDraft)) {
      this.error = '';
      this.notice = 'No changes to save.';
      this.metadataDraft = null;
      return;
    }
    this.start('metadata');
    this.service.updateCandidateMetadata(this.selectedCandidateId, this.metadataDraft).pipe(
      switchMap(() => this.service.getCandidateDetails(this.selectedCandidateId!)),
      finalize(() => this.busyAction = null),
    ).subscribe({
      next: (details) => {
        this.candidateDetails = details;
        this.metadataDraft = null;
        this.refreshSelections(details.id);
      },
      error: (error) => this.handleError(error),
    });
  }

  reprocessDocument(document: CandidateDocumentDetails): void {
    if (!this.candidateDetails?.correctionAllowed || !document.originalAvailable || this.busyAction) return;
    if (!window.confirm('Reprocess this stored original? Existing evaluations will be marked stale.')) return;
    this.start('document');
    this.service.reprocessDocument(document.id).pipe(
      switchMap(() => forkJoin({
        details: this.service.getCandidateDetails(this.selectedCandidateId!),
        evaluations: this.service.getEvaluationHistory(this.selectedCandidateId!),
      })),
      finalize(() => this.busyAction = null),
    ).subscribe({
      next: ({ details, evaluations }) => {
        this.candidateDetails = details;
        this.evaluationHistory = evaluations;
        this.evaluation = null;
      },
      error: (error) => this.handleError(error),
    });
  }

  downloadOriginal(item: CandidateDocumentDetails): void {
    if (!item.downloadAllowed || this.busyAction) return;
    this.start('document');
    this.service.downloadOriginal(item.id).pipe(finalize(() => this.busyAction = null)).subscribe({
      next: (content) => {
        const url = URL.createObjectURL(content);
        const link = document.createElement('a');
        link.href = url;
        link.download = item.originalFilename;
        link.click();
        // Object URLs retain the downloaded Blob until explicitly released by the page.
        URL.revokeObjectURL(url);
      },
      error: (error) => this.handleError(error),
    });
  }

  deleteOriginal(document: CandidateDocumentDetails): void {
    if (!document.administrationAllowed || !document.originalAvailable || this.busyAction) return;
    if (!window.confirm(`Delete the stored original for ${document.originalFilename}? Candidate data remains available.`)) return;
    this.start('document');
    // Refresh within the same observable chain so the busy state covers both mutation and the authoritative reload.
    this.service.deleteOriginal(document.id).pipe(
      switchMap(() => this.service.getCandidateDetails(this.selectedCandidateId!)),
      finalize(() => this.busyAction = null),
    ).subscribe({
      next: (details) => this.candidateDetails = details,
      error: (error) => this.handleError(error),
    });
  }

  updateLegalHold(document: CandidateDocumentDetails): void {
    if (!document.administrationAllowed || !document.originalAvailable || this.busyAction) return;
    this.start('document');
    // Do not flip local state optimistically: retention cleanup may act immediately when an overdue hold is released.
    this.service.updateLegalHold(document.id, !document.legalHold).pipe(
      switchMap(() => this.service.getCandidateDetails(this.selectedCandidateId!)),
      finalize(() => this.busyAction = null),
    )
      .subscribe({
        next: (details) => this.candidateDetails = details,
        error: (error) => this.handleError(error),
      });
  }

  eraseCandidate(): void {
    // Mirror the backend policy in the UI; the backend remains authoritative against stale or crafted requests.
    if (!this.isAdministrator || !this.selectedCandidateId || this.candidateUnderLegalHold || this.busyAction) return;
    const candidate = this.candidates.find((item) => item.id === this.selectedCandidateId);
    const label = candidate?.name || `candidate #${this.selectedCandidateId}`;
    if (!window.confirm(
      `Permanently erase ${label}, including CV documents, stored originals, and evaluations? This cannot be undone.`,
    )) return;

    const erasedId = this.selectedCandidateId;
    this.start('erasure');
    this.service.eraseCandidate(erasedId).pipe(finalize(() => this.busyAction = null)).subscribe({
      next: () => {
        this.selectedCandidateId = null;
        this.evaluation = null;
        this.evaluationHistory = [];
        this.refreshSelections();
      },
      error: (error) => this.handleError(error),
    });
  }

  trackResult(_: number, item: CvImportResult): string {
    return `${item.originalFilename}-${item.documentId ?? item.status}`;
  }

  trackEvaluation(_: number, item: EvaluationHistoryItem): number {
    return item.id;
  }

  private loadEvaluationHistory(): void {
    if (!this.selectedCandidateId) {
      this.evaluationHistory = [];
      return;
    }
    this.service.getEvaluationHistory(this.selectedCandidateId).subscribe({
      next: (items) => this.evaluationHistory = items,
      error: (error) => this.handleError(error),
    });
  }

  private refreshSelections(preferredCandidateId?: number | null): void {
    this.service.getCandidates().subscribe({
      next: (candidates) => {
        this.candidates = candidates;
        if (preferredCandidateId && candidates.some((item) => item.id === preferredCandidateId)) {
          this.selectedCandidateId = preferredCandidateId;
        } else if (!this.selectedCandidateId || !candidates.some((item) => item.id === this.selectedCandidateId)) {
          this.selectedCandidateId = candidates[0].id;
        }
        if (!candidates.length) this.selectedCandidateId = null;
        this.loadCandidateDetails();
      },
      error: (error) => this.handleError(error),
    });
    this.service.getJobs().subscribe({
      next: (jobs) => {
        this.jobs = jobs;
        if (!this.selectedJobId && jobs.length) this.selectedJobId = jobs[0].id;
      },
      error: (error) => this.handleError(error),
    });
  }

  private selectedFile(event: Event, extension: string, limit: number): File | null {
    this.error = '';
    this.notice = '';
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    if (!file) return null;
    if (!file.name.toLowerCase().endsWith(extension)) {
      this.error = `Choose a ${extension.toUpperCase()} file.`;
      input.value = '';
      return null;
    }
    if (file.size > limit) {
      this.error = `${file.name} exceeds the ${Math.round(limit / 1024 / 1024)} MB limit.`;
      input.value = '';
      return null;
    }
    return file;
  }

  private start(action: typeof this.busyAction): void {
    this.error = '';
    this.notice = '';
    this.evaluation = action === 'evaluation' ? null : this.evaluation;
    this.busyAction = action;
  }

  private metadataIsUnchanged(update: CandidateMetadataUpdate): boolean {
    const details = this.candidateDetails;
    if (!details) return false;
    const optional = (value: string | null): string | null => value?.trim() || null;
    return details.name === update.name.trim()
      && details.email === optional(update.email)
      && details.location === optional(update.location)
      && details.currentTitle === optional(update.currentTitle)
      && details.yearsExperience === update.yearsExperience;
  }

  private handleError(error: HttpErrorResponse): void {
    this.notice = '';
    const apiError = error.error as Partial<ApiError> | undefined;
    this.error = apiError?.message || error.message || 'The request could not be completed.';
  }

  private firstCandidateId(result: CvArchiveImportResult): number | null {
    return result.results.find((item) => item.candidateId !== null)?.candidateId ?? null;
  }

  private asArchiveResult(item: CvImportResult): CvArchiveImportResult {
    return {
      totalFiles: 1,
      imported: item.status === 'IMPORTED' ? 1 : 0,
      duplicates: item.status === 'DUPLICATE' ? 1 : 0,
      needsReview: item.status === 'NEEDS_REVIEW' ? 1 : 0,
      skipped: item.status === 'SKIPPED' ? 1 : 0,
      failed: item.status === 'FAILED' ? 1 : 0,
      results: [item],
    };
  }
}
