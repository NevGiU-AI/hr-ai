import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
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
import {
  CvChatCandidateOption,
  CvChatCitation,
  CvChatComparisonResponse,
  CvChatLanguageScopeResponse,
  CvChatResponse,
  CvChatToolOrchestrationOutcome,
} from '../../models/cv-chat.models';
import { CvChatService } from '../../services/cv-chat.service';
import { CvIngestionService } from '../../services/cv-ingestion.service';

@Component({
  selector: 'app-cv-workspace',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './cv-workspace.component.html',
  styleUrl: './cv-workspace.component.scss',
})
export class CvWorkspaceComponent implements OnInit {
  private readonly service = inject(CvIngestionService);
  private readonly chatService = inject(CvChatService);
  private readonly auth = inject(AuthService);
  private pendingComparisonCitationFragment: string | null = null;

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
  chatQuestion = '';
  chatResponse: CvChatResponse | null = null;
  chatError = '';
  chatBusy = false;
  candidateSearchQuery = '';
  candidateSearchResults: CvChatCandidateOption[] = [];
  candidateSearchTruncated = false;
  candidateSearchResultQuery = '';
  candidateSearchBusy = false;
  candidateSearchError = '';
  comparisonCandidates: CvChatCandidateOption[] = [];
  comparisonQuestion = '';
  comparisonResponse: CvChatComparisonResponse | null = null;
  comparisonError = '';
  comparisonBusy = false;
  languageComparisonMessage = '';
  // Evaluation and language-first comparison are independent workflows. Evaluation may default to the first
  // approved job, but comparison must not silently inherit it: job context is opt-in for each comparison request.
  languageComparisonJobId: number | null = null;
  languageScopeResponse: CvChatLanguageScopeResponse | null = null;
  languageScopeSelections: Array<CvChatCandidateOption | null> = [];
  languageScopeBusy = false;
  languageScopeError = '';
  languageWorkflowOutcome: CvChatToolOrchestrationOutcome | null = null;
  private languageWorkflowRequest = 0;
  busyAction: 'pdf' | 'archive' | 'initial' | 'evaluation' | 'erasure' | 'details' | 'document' | 'metadata' | null = null;
  error = '';
  notice = '';

  readonly maxPdfBytes = 20 * 1024 * 1024;
  readonly maxArchiveBytes = 100 * 1024 * 1024;
  readonly minimumComparisonCandidates = 2;
  readonly maximumComparisonCandidates = 5;

  get isAdministrator(): boolean {
    return this.auth.user?.roles.includes('ADMIN') ?? false;
  }

  get canUseCvChat(): boolean {
    return this.auth.user?.roles.some((role) => ['ADMIN', 'RECRUITER', 'REVIEWER'].includes(role)) ?? false;
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
    if (candidateId !== this.selectedCandidateId) this.resetChat();
    this.selectedCandidateId = candidateId;
    this.evaluation = null;
    this.loadCandidateDetails();
  }

  searchCandidates(): void {
    const query = this.candidateSearchQuery.trim();
    if (query.length < 2 || this.candidateSearchBusy) return;

    this.candidateSearchBusy = true;
    this.candidateSearchError = '';
    this.candidateSearchResults = [];
    this.candidateSearchTruncated = false;
    this.candidateSearchResultQuery = '';
    this.chatService.searchCandidates(query)
      .pipe(finalize(() => this.candidateSearchBusy = false))
      .subscribe({
        next: (response) => {
          this.candidateSearchResults = response.candidates;
          this.candidateSearchTruncated = response.truncated;
          this.candidateSearchResultQuery = query;
        },
        error: (error: HttpErrorResponse) => {
          this.candidateSearchError = this.apiErrorMessage(error, 'Candidate search could not complete.');
        },
      });
  }

  chooseCandidate(candidate: CvChatCandidateOption): void {
    // Search only discovers choices. This explicit action establishes the ID used by details, evaluation, and chat.
    this.candidateSearchQuery = candidate.name;
    this.candidateSearchResults = [];
    this.candidateSearchTruncated = false;
    this.candidateSearchResultQuery = '';
    this.selectCandidate(candidate.id);
  }

  addComparisonCandidate(candidate: CvChatCandidateOption): void {
    if (this.isComparisonCandidateSelected(candidate.id)) return;
    if (this.comparisonCandidates.length >= this.maximumComparisonCandidates) {
      this.comparisonError = `Select no more than ${this.maximumComparisonCandidates} candidates.`;
      return;
    }
    this.comparisonCandidates = [...this.comparisonCandidates, candidate];
    this.resetComparisonResult();
  }

  isComparisonCandidateSelected(candidateId: number | null): boolean {
    return candidateId !== null && this.comparisonCandidates.some((candidate) => candidate.id === candidateId);
  }

  addCurrentCandidateToComparison(): void {
    const candidate = this.candidates.find((item) => item.id === this.selectedCandidateId);
    if (!candidate) return;
    this.addComparisonCandidate({
      id: candidate.id,
      name: candidate.name,
      currentTitle: candidate.currentTitle,
    });
  }

  removeComparisonCandidate(candidateId: number): void {
    const next = this.comparisonCandidates.filter((candidate) => candidate.id !== candidateId);
    if (next.length === this.comparisonCandidates.length) return;
    this.comparisonCandidates = next;
    this.resetComparisonResult();
  }

  clearComparison(): void {
    this.comparisonCandidates = [];
    this.comparisonQuestion = '';
    this.resetComparisonResult();
  }

  proposeLanguageScope(): void {
    const message = this.languageComparisonMessage.trim();
    if (!this.canUseCvChat || message.length < 2 || message.length > 2000 || this.languageScopeBusy) return;

    this.languageScopeBusy = true;
    this.languageScopeError = '';
    this.languageWorkflowOutcome = null;
    this.languageScopeResponse = null;
    this.languageScopeSelections = [];
    const request = ++this.languageWorkflowRequest;
    this.chatService.discoverWithTools(message)
      .pipe(
        finalize(() => {
          if (request === this.languageWorkflowRequest) this.languageScopeBusy = false;
        }),
      )
      .subscribe({
        next: (response) => {
          if (!this.isCurrentLanguageRequest(request, message)) return;
          this.languageWorkflowOutcome = { kind: response.kind, message: response.message };
          if (response.kind !== 'CANDIDATE_CONFIRMATION_REQUIRED') return;

          const candidateScope = response.candidateScope;
          // Fail closed when an old or malformed backend confirms discovery without the cards from that exact request.
          // Never recover by making the old second extraction call: it could resolve a different candidate scope.
          if (!candidateScope || candidateScope.message !== message || candidateScope.references.length < 2) {
            this.languageScopeError = 'Candidate cards were not returned for this request. Please try again.';
            return;
          }
          this.languageScopeResponse = candidateScope;
          // Partial-name tool results remain explicit choices, even when only one card currently matches. No evidence
          // is retrieved until the recruiter selects every card and presses the separate confirmation button.
          this.languageScopeSelections = candidateScope.references.map((reference) =>
            reference.status === 'RESOLVED' && reference.candidates.length === 1
              ? reference.candidates[0]
              : null);
        },
        error: (error: HttpErrorResponse) => {
          if (this.isCurrentLanguageRequest(request, message)) {
            this.languageScopeError = this.apiErrorMessage(error, 'Candidate references could not be interpreted.');
          }
        },
      });
  }

  selectLanguageCandidate(referenceIndex: number, candidate: CvChatCandidateOption): void {
    this.languageScopeSelections[referenceIndex] = candidate;
    this.languageScopeSelections = [...this.languageScopeSelections];
    // Invalidates any confirmed-scope result already in flight for the previous card selection.
    this.languageWorkflowRequest++;
    this.languageScopeError = '';
    this.languageWorkflowOutcome = null;
  }

  selectLanguageComparisonJob(jobId: number | null): void {
    if (this.languageComparisonJobId === jobId) return;
    this.languageComparisonJobId = jobId;
    // A late response planned against the previous job choice must not populate the visible comparison scope.
    this.languageWorkflowRequest++;
    this.languageWorkflowOutcome = null;
    this.languageScopeError = '';
  }

  isLanguageCandidateSelected(referenceIndex: number, candidateId: number): boolean {
    return this.languageScopeSelections[referenceIndex]?.id === candidateId;
  }

  confirmLanguageScope(): void {
    const response = this.languageScopeResponse;
    if (!response || response.references.length < this.minimumComparisonCandidates) return;
    const selected = this.languageScopeSelections.filter(
      (candidate): candidate is CvChatCandidateOption => candidate !== null);
    if (selected.length !== response.references.length) {
      this.languageScopeError = 'Choose one candidate card for every referenced name.';
      return;
    }
    const unique = new Map(selected.map((candidate) => [candidate.id, candidate]));
    if (unique.size !== selected.length) {
      this.languageScopeError = 'Each referenced name must resolve to a different candidate.';
      return;
    }
    if (unique.size > this.maximumComparisonCandidates) {
      this.languageScopeError = `Select no more than ${this.maximumComparisonCandidates} candidates.`;
      return;
    }

    const candidates = [...unique.values()];
    const candidateIds = candidates.map((candidate) => candidate.id);
    const jobId = this.languageComparisonJobId;
    const request = ++this.languageWorkflowRequest;
    this.languageScopeBusy = true;
    this.languageScopeError = '';
    this.languageWorkflowOutcome = null;
    this.chatService.planConfirmedScope(response.message, candidateIds, jobId)
      .pipe(finalize(() => {
        if (request === this.languageWorkflowRequest) this.languageScopeBusy = false;
      }))
      .subscribe({
        next: (outcome) => {
          if (!this.isCurrentConfirmedScope(request, response.message, candidateIds, jobId)) return;
          this.languageWorkflowOutcome = outcome;
          if (outcome.kind !== 'CONFIRMED_SCOPE_READY') return;

          // A ready decision establishes the same explicit verified-ID scope used by picker-first comparison. It
          // still does not retrieve CV evidence; the recruiter reviews the visible scope and submits separately.
          this.comparisonCandidates = candidates;
          this.comparisonQuestion = response.message;
          this.resetComparisonResult();
        },
        error: (error: HttpErrorResponse) => {
          if (this.isCurrentConfirmedScope(request, response.message, candidateIds, jobId)) {
            this.languageScopeError = this.apiErrorMessage(error, 'Confirmed candidate scope could not be planned.');
          }
        },
      });
  }

  private isCurrentLanguageRequest(request: number, message: string): boolean {
    return request === this.languageWorkflowRequest && this.languageComparisonMessage.trim() === message;
  }

  private isCurrentConfirmedScope(
    request: number,
    message: string,
    candidateIds: number[],
    jobId: number | null,
  ): boolean {
    const currentIds = this.languageScopeSelections
      .filter((candidate): candidate is CvChatCandidateOption => candidate !== null)
      .map((candidate) => candidate.id);
    return request === this.languageWorkflowRequest
      && this.languageScopeResponse?.message === message
      && currentIds.join(',') === candidateIds.join(',')
      && this.languageComparisonJobId === jobId;
  }

  languageScopeReady(): boolean {
    const references = this.languageScopeResponse?.references ?? [];
    return references.length >= this.minimumComparisonCandidates
      && references.length <= this.maximumComparisonCandidates
      && this.languageScopeSelections.length === references.length
      && this.languageScopeSelections.every((candidate) => candidate !== null);
  }

  compareCandidates(): void {
    const candidateIds = this.comparisonCandidates.map((candidate) => candidate.id);
    const question = this.comparisonQuestion.trim();
    if (!this.canUseCvChat
      || candidateIds.length < this.minimumComparisonCandidates
      || candidateIds.length > this.maximumComparisonCandidates
      || !question
      || question.length > 2000
      || !!this.busyAction
      || this.comparisonBusy) return;

    // Capture the exact confirmed scope. A response arriving after the recruiter changes that scope is discarded.
    const scopeKey = candidateIds.join(',');
    this.comparisonBusy = true;
    this.comparisonError = '';
    this.comparisonResponse = null;
    this.chatService.compare(candidateIds, question)
      .pipe(finalize(() => this.comparisonBusy = false))
      .subscribe({
        next: (response) => {
          if (this.comparisonScopeKey() !== scopeKey) return;
          this.comparisonCandidates = response.candidates;
          this.comparisonResponse = response;
        },
        error: (error: HttpErrorResponse) => {
          if (this.comparisonScopeKey() !== scopeKey) return;
          this.comparisonError = this.apiErrorMessage(error, 'Candidate comparison could not complete.');
        },
      });
  }

  askCv(): void {
    const candidateId = this.selectedCandidateId;
    const question = this.chatQuestion.trim();
    if (!candidateId || !this.canUseCvChat || !question || question.length > 2000 || this.chatBusy) return;

    this.chatBusy = true;
    this.chatError = '';
    this.chatResponse = null;
    this.chatService.ask(candidateId, question).pipe(finalize(() => this.chatBusy = false)).subscribe({
      next: (response) => {
        // Ignore a late response if application state changes the selected candidate before completion.
        if (this.selectedCandidateId === candidateId) this.chatResponse = response;
      },
      error: (error: HttpErrorResponse) => {
        if (this.selectedCandidateId !== candidateId) return;
        this.chatError = this.apiErrorMessage(error, 'CV chat could not complete the request.');
      },
    });
  }

  citationLabel(citation: CvChatCitation): string {
    if (citation.documentId === null) return `Candidate ${citation.candidateId} CV evidence`;
    return citation.pageNumber === null
      ? `CV document ${citation.documentId}`
      : `CV document ${citation.documentId}, page ${citation.pageNumber}`;
  }

  citationFragment(citation: CvChatCitation): string {
    return citation.documentId === null ? 'candidate-details-title' : `cv-document-${citation.documentId}`;
  }

  comparisonCandidateName(candidateId: number): string {
    return this.comparisonCandidates.find((candidate) => candidate.id === candidateId)?.name
      || `Candidate #${candidateId}`;
  }

  openComparisonCitation(citation: CvChatCitation, event: Event): void {
    event.preventDefault();
    this.pendingComparisonCitationFragment = this.citationFragment(citation);
    if (this.selectedCandidateId === citation.candidateId) {
      this.scrollToPendingComparisonCitation();
      return;
    }
    // Comparison sources can belong to a candidate other than the currently open details panel. Load the owning
    // candidate first so the target document fragment exists before attempting to scroll to it.
    this.selectCandidate(citation.candidateId);
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
          this.scrollToPendingComparisonCitation();
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
        this.removeComparisonCandidate(erasedId);
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
        const availableIds = new Set(candidates.map((candidate) => candidate.id));
        const availableComparisonCandidates = this.comparisonCandidates.filter(
          (candidate) => availableIds.has(candidate.id),
        );
        if (availableComparisonCandidates.length !== this.comparisonCandidates.length) {
          this.comparisonCandidates = availableComparisonCandidates;
          this.resetComparisonResult();
        }
        const previousCandidateId = this.selectedCandidateId;
        if (preferredCandidateId && candidates.some((item) => item.id === preferredCandidateId)) {
          this.selectedCandidateId = preferredCandidateId;
        } else if (!this.selectedCandidateId || !candidates.some((item) => item.id === this.selectedCandidateId)) {
          this.selectedCandidateId = candidates[0].id;
        }
        if (!candidates.length) this.selectedCandidateId = null;
        if (this.selectedCandidateId !== previousCandidateId) this.resetChat();
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

  private resetChat(): void {
    this.chatQuestion = '';
    this.chatResponse = null;
    this.chatError = '';
  }

  private resetComparisonResult(): void {
    this.comparisonResponse = null;
    this.comparisonError = '';
  }

  private comparisonScopeKey(): string {
    return this.comparisonCandidates.map((candidate) => candidate.id).join(',');
  }

  private scrollToPendingComparisonCitation(): void {
    const fragment = this.pendingComparisonCitationFragment;
    if (!fragment) return;
    this.pendingComparisonCitationFragment = null;
    // Angular must finish rendering the newly loaded candidate details before the fragment can be resolved.
    setTimeout(() => {
      window.history.replaceState(null, '', `${window.location.pathname}${window.location.search}#${fragment}`);
      document.getElementById(fragment)?.scrollIntoView({ block: 'start' });
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
    this.error = this.apiErrorMessage(error, 'The request could not be completed.');
  }

  private apiErrorMessage(error: HttpErrorResponse, fallback: string): string {
    const apiError = error.error as Partial<ApiError> | undefined;
    if (apiError?.message) {
      return apiError.message;
    }
    if (error.status === 0) {
      return 'The service could not be reached. Check your connection and try again.';
    }
    if (error.status >= 500) {
      // Browser-generated HttpErrorResponse messages can misleadingly combine an error code with status text "OK".
      return 'The service is temporarily unavailable. Please try again later.';
    }
    return fallback;
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
