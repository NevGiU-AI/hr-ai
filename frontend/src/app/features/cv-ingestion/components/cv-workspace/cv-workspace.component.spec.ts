import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { Candidate, CandidateDetails, CvArchiveImportResult, CvImportResult, EvaluationHistoryItem,
  EvaluationResponse, Job } from '../../models/cv-ingestion.models';
import { CvIngestionService } from '../../services/cv-ingestion.service';
import { CvChatService } from '../../services/cv-chat.service';
import { CvWorkspaceComponent } from './cv-workspace.component';
import { AuthService } from '../../../../core/auth/auth.service';

describe('CvWorkspaceComponent', () => {
  let fixture: ComponentFixture<CvWorkspaceComponent>;
  let component: CvWorkspaceComponent;
  let service: jasmine.SpyObj<CvIngestionService>;
  let chatService: jasmine.SpyObj<CvChatService>;
  const auth = { user: { id: 1, email: 'admin@example.com', organizationId: 'tenant-a', roles: ['ADMIN'] } };

  const candidate: Candidate = {
    id: 7, name: 'Ada Lovelace', email: 'ada@example.com', currentTitle: null, cvText: 'Engineer',
  };
  const job: Job = { id: 12, title: 'Backend Engineer', level: 'Senior', summary: 'Build APIs' };

  beforeEach(async () => {
    // Each test starts as an administrator; authorization tests may temporarily change this role list.
    auth.user.roles = ['ADMIN'];
    service = jasmine.createSpyObj<CvIngestionService>('CvIngestionService', [
      'uploadPdf', 'uploadArchive', 'importInitial', 'getCandidates', 'getJobs', 'evaluate', 'eraseCandidate',
      'getCandidateDetails', 'downloadOriginal', 'deleteOriginal', 'updateLegalHold',
      'updateCandidateMetadata', 'reprocessDocument',
      'getEvaluationHistory',
    ]);
    chatService = jasmine.createSpyObj<CvChatService>('CvChatService', [
      'ask', 'searchCandidates', 'compare', 'proposeLanguageScope',
    ]);
    service.getCandidates.and.returnValue(of([candidate]));
    service.getJobs.and.returnValue(of([job]));
    service.getCandidateDetails.and.returnValue(of(candidateDetails()));
    service.getEvaluationHistory.and.returnValue(of([historyItem()]));

    await TestBed.configureTestingModule({
      imports: [CvWorkspaceComponent],
      providers: [
        provideRouter([]),
        { provide: CvIngestionService, useValue: service },
        { provide: CvChatService, useValue: chatService },
        { provide: AuthService, useValue: auth },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(CvWorkspaceComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads candidates and jobs and selects defaults', () => {
    expect(service.getCandidates).toHaveBeenCalled();
    expect(service.getJobs).toHaveBeenCalled();
    expect(component.selectedCandidateId).toBe(7);
    expect(component.selectedJobId).toBe(12);
    expect(service.getCandidateDetails).toHaveBeenCalledOnceWith(7);
    expect(service.getEvaluationHistory).toHaveBeenCalledOnceWith(7);
  });

  it('rejects a non-PDF selection', () => {
    const input = document.createElement('input');
    Object.defineProperty(input, 'files', { value: [new File(['x'], 'candidate.txt')] });
    component.selectPdf({ target: input } as unknown as Event);
    expect(component.pdfFile).toBeNull();
    expect(component.error).toContain('.PDF');
  });

  it('rejects an oversized ZIP selection', () => {
    const file = new File(['x'], 'candidates.zip');
    Object.defineProperty(file, 'size', { value: component.maxArchiveBytes + 1 });
    const input = document.createElement('input');
    Object.defineProperty(input, 'files', { value: [file] });
    component.selectArchive({ target: input } as unknown as Event);
    expect(component.archiveFile).toBeNull();
    expect(component.error).toContain('100 MB');
  });

  it('uploads a PDF, renders its result, and refreshes candidates', () => {
    const imported = importResult('IMPORTED');
    service.uploadPdf.and.returnValue(of(imported));
    component.pdfFile = new File(['%PDF'], 'ada.pdf', { type: 'application/pdf' });
    component.uploadPdf();
    fixture.detectChanges();

    expect(service.uploadPdf).toHaveBeenCalled();
    expect(component.result?.imported).toBe(1);
    expect(service.getCandidates).toHaveBeenCalledTimes(2);
    expect(fixture.nativeElement.textContent).toContain('IMPORTED');
  });

  it('disables conflicting actions while an archive request is active', () => {
    const pending = new Subject<CvArchiveImportResult>();
    service.uploadArchive.and.returnValue(pending);
    component.archiveFile = new File(['zip'], 'candidates.zip');
    component.uploadArchive();
    fixture.detectChanges();

    expect(component.busyAction).toBe('archive');
    const buttons = fixture.debugElement.queryAll(By.css('button'));
    expect(buttons.every((button) => button.nativeElement.disabled)).toBeTrue();
    pending.next(archiveResult([importResult('IMPORTED')]));
    pending.complete();
    expect(component.busyAction).toBeNull();
  });

  it('shows duplicate, review, skipped, and failed archive outcomes', () => {
    const results = ['DUPLICATE', 'NEEDS_REVIEW', 'SKIPPED', 'FAILED'].map((status) =>
      importResult(status as CvImportResult['status']));
    service.importInitial.and.returnValue(of(archiveResult(results)));
    component.importInitial();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('DUPLICATE');
    expect(text).toContain('NEEDS_REVIEW');
    expect(text).toContain('SKIPPED');
    expect(text).toContain('FAILED');
  });

  it('does not evaluate until explicitly requested', () => {
    expect(service.evaluate).not.toHaveBeenCalled();
    const response = evaluationResponse();
    service.evaluate.and.returnValue(of(response));
    component.evaluate();
    expect(service.evaluate).toHaveBeenCalledOnceWith(7, 12);
  });

  it('renders tenant-scoped candidate details and ingestion history', () => {
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Candidate details');
    expect(text).toContain('ada.pdf');
    expect(text).toContain('Original stored');
    expect(text).toContain('Download original');
  });

  it('asks about the selected candidate and renders linked citations', () => {
    chatService.ask.and.returnValue(of({
      answer: 'Ada lists Java experience [CV-2].',
      citations: [{ reference: 'CV-2', candidateId: 7, documentId: 2, pageNumber: null,
        excerpt: 'Java engineer' }],
    }));
    component.chatQuestion = '  Which skills are stated?  ';

    component.askCv();
    fixture.detectChanges();

    expect(chatService.ask).toHaveBeenCalledOnceWith(7, 'Which skills are stated?');
    expect(fixture.nativeElement.textContent).toContain('Ada lists Java experience');
    const citation = fixture.debugElement.query(By.css('.citation-list a'));
    // The full candidate-workspace route prevents <base href="/"> from resolving the link through the default route.
    expect(citation.nativeElement.getAttribute('href')).toBe('/candidates/import#cv-document-2');
    expect(citation.nativeElement.textContent).toContain('CV document 2');
  });

  it('searches partial names and selects one explicit candidate ID', () => {
    chatService.searchCandidates.and.returnValue(of({
      candidates: [{ id: 8, name: 'Fanon Jupkwo', currentTitle: 'Java Engineer' }],
      truncated: false,
    }));
    service.getCandidateDetails.and.returnValue(of({ ...candidateDetails(), id: 8, name: 'Fanon Jupkwo' }));
    component.candidateSearchQuery = '  Fanon  ';

    component.searchCandidates();
    fixture.detectChanges();

    expect(chatService.searchCandidates).toHaveBeenCalledOnceWith('Fanon');
    expect(fixture.nativeElement.textContent).toContain('Fanon Jupkwo');
    component.chooseCandidate(component.candidateSearchResults[0]);

    expect(component.selectedCandidateId).toBe(8);
    expect(component.candidateSearchQuery).toBe('Fanon Jupkwo');
    expect(component.candidateSearchResults).toEqual([]);
    expect(service.getCandidateDetails).toHaveBeenCalledWith(8);
  });

  it('shows when candidate search results were truncated', () => {
    chatService.searchCandidates.and.returnValue(of({
      candidates: [{ id: 8, name: 'Alex Smith', currentTitle: null }],
      truncated: true,
    }));
    component.candidateSearchQuery = 'Alex';

    component.searchCandidates();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('More candidates match');
  });

  it('shows a tenant-safe empty result after a completed search', () => {
    chatService.searchCandidates.and.returnValue(of({ candidates: [], truncated: false }));
    component.candidateSearchQuery = 'Missing';

    component.searchCandidates();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No candidates matched this name in your organization');
  });

  it('does not search names shorter than the backend minimum', () => {
    component.candidateSearchQuery = 'A';

    component.searchCandidates();

    expect(chatService.searchCandidates).not.toHaveBeenCalled();
  });

  it('renders structured candidate-search errors', () => {
    chatService.searchCandidates.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 503, error: { message: 'Candidate search is temporarily unavailable' },
    })));
    component.candidateSearchQuery = 'Fanon';

    component.searchCandidates();
    fixture.detectChanges();

    expect(component.candidateSearchError).toBe('Candidate search is temporarily unavailable');
    expect(fixture.nativeElement.textContent).toContain('Candidate search is temporarily unavailable');
  });

  it('maintains a distinct explicit candidate comparison scope', () => {
    component.addCurrentCandidateToComparison();
    component.addComparisonCandidate({ id: 8, name: 'Grace Hopper', currentTitle: 'Architect' });
    component.addComparisonCandidate({ id: 8, name: 'Grace Hopper', currentTitle: 'Architect' });
    fixture.detectChanges();

    expect(component.comparisonCandidates.map((item) => item.id)).toEqual([7, 8]);
    expect(component.selectedCandidateId).toBe(7);
    expect(fixture.nativeElement.textContent).toContain('2/5 selected');
    expect(fixture.nativeElement.textContent).toContain('Grace Hopper');

    component.removeComparisonCandidate(7);
    expect(component.comparisonCandidates.map((item) => item.id)).toEqual([8]);
  });

  it('proposes tenant-scoped cards from natural language without comparing before confirmation', () => {
    chatService.proposeLanguageScope.and.returnValue(of({
      message: 'Compare Ada Lovelace and Grace',
      references: [
        { requestedName: 'Ada Lovelace', status: 'RESOLVED', truncated: false,
          candidates: [{ id: 7, name: 'Ada Lovelace', currentTitle: 'Engineer' }] },
        { requestedName: 'Grace', status: 'CHOICES', truncated: false, candidates: [
          { id: 8, name: 'Grace Hopper', currentTitle: 'Architect' },
          { id: 9, name: 'Grace Murray', currentTitle: 'Developer' },
        ] },
      ],
    }));
    component.languageComparisonMessage = '  Compare Ada Lovelace and Grace  ';

    component.proposeLanguageScope();
    fixture.detectChanges();

    expect(chatService.proposeLanguageScope).toHaveBeenCalledOnceWith('Compare Ada Lovelace and Grace');
    expect(chatService.compare).not.toHaveBeenCalled();
    expect(component.languageScopeSelections[0]?.id).toBe(7);
    expect(component.languageScopeSelections[1]).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Grace Hopper');
    expect(fixture.nativeElement.textContent).toContain('Confirm candidate scope');
  });

  it('requires explicit confirmation before copying language-first cards into comparison scope', () => {
    component.languageScopeResponse = {
      message: 'Compare Ada and Grace for backend engineering',
      references: [
        { requestedName: 'Ada', status: 'RESOLVED', truncated: false,
          candidates: [{ id: 7, name: 'Ada Lovelace', currentTitle: 'Engineer' }] },
        { requestedName: 'Grace', status: 'CHOICES', truncated: false,
          candidates: [{ id: 8, name: 'Grace Hopper', currentTitle: 'Architect' }] },
      ],
    };
    component.languageScopeSelections = [
      { id: 7, name: 'Ada Lovelace', currentTitle: 'Engineer' },
      null,
    ];

    component.confirmLanguageScope();
    expect(component.comparisonCandidates).toEqual([]);
    expect(component.languageScopeError).toContain('Choose one candidate card');

    component.selectLanguageCandidate(1, { id: 8, name: 'Grace Hopper', currentTitle: 'Architect' });
    component.confirmLanguageScope();

    expect(component.comparisonCandidates.map((item) => item.id)).toEqual([7, 8]);
    expect(component.comparisonQuestion).toBe('Compare Ada and Grace for backend engineering');
    expect(chatService.compare).not.toHaveBeenCalled();
  });

  it('rejects the same candidate being confirmed for two language references', () => {
    const ada = { id: 7, name: 'Ada Lovelace', currentTitle: 'Engineer' };
    component.languageScopeResponse = {
      message: 'Compare Ada and Lovelace',
      references: [
        { requestedName: 'Ada', status: 'CHOICES', candidates: [ada], truncated: false },
        { requestedName: 'Lovelace', status: 'CHOICES', candidates: [ada], truncated: false },
      ],
    };
    component.languageScopeSelections = [ada, ada];

    component.confirmLanguageScope();

    expect(component.comparisonCandidates).toEqual([]);
    expect(component.languageScopeError).toContain('different candidate');
  });

  it('enforces the five-candidate comparison limit without changing the confirmed scope', () => {
    [7, 8, 9, 10, 11].forEach((id) => component.addComparisonCandidate({
      id, name: `Candidate ${id}`, currentTitle: null,
    }));

    component.addComparisonCandidate({ id: 12, name: 'Candidate 12', currentTitle: null });

    expect(component.comparisonCandidates.length).toBe(5);
    expect(component.comparisonCandidates.some((item) => item.id === 12)).toBeFalse();
    expect(component.comparisonError).toBe('Select no more than 5 candidates.');
  });

  it('compares the confirmed candidates and renders candidate-labelled citations', () => {
    component.addComparisonCandidate({ id: 7, name: 'Ada Lovelace', currentTitle: 'Engineer' });
    component.addComparisonCandidate({ id: 8, name: 'Grace Hopper', currentTitle: 'Architect' });
    chatService.compare.and.returnValue(of({
      answer: 'Ada lists Java [CV-2], while Grace lists distributed systems [CV-3].',
      candidates: component.comparisonCandidates,
      citations: [
        { reference: 'CV-2', candidateId: 7, documentId: 2, pageNumber: null, excerpt: 'Java engineer' },
        { reference: 'CV-3', candidateId: 8, documentId: 3, pageNumber: null, excerpt: 'Distributed systems' },
      ],
    }));
    component.comparisonQuestion = '  Compare backend experience  ';

    component.compareCandidates();
    fixture.detectChanges();

    expect(chatService.compare).toHaveBeenCalledOnceWith([7, 8], 'Compare backend experience');
    expect(fixture.nativeElement.textContent).toContain('Ada lists Java');
    expect(fixture.nativeElement.textContent).toContain('Ada Lovelace · CV document 2');
    expect(fixture.nativeElement.textContent).toContain('Grace Hopper · CV document 3');
  });

  it('discards a late comparison response after the recruiter changes the candidate scope', () => {
    const pending = new Subject<never>();
    component.addComparisonCandidate({ id: 7, name: 'Ada Lovelace', currentTitle: 'Engineer' });
    component.addComparisonCandidate({ id: 8, name: 'Grace Hopper', currentTitle: 'Architect' });
    component.comparisonQuestion = 'Compare backend experience';
    chatService.compare.and.returnValue(pending);

    component.compareCandidates();
    component.removeComparisonCandidate(8);
    pending.next({
      answer: 'Stale answer', candidates: [], citations: [],
    } as never);
    pending.complete();

    expect(component.comparisonResponse).toBeNull();
  });

  it('keeps comparison input available after a structured provider error', () => {
    component.addComparisonCandidate({ id: 7, name: 'Ada Lovelace', currentTitle: 'Engineer' });
    component.addComparisonCandidate({ id: 8, name: 'Grace Hopper', currentTitle: 'Architect' });
    component.comparisonQuestion = 'Compare backend experience';
    chatService.compare.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 502, error: { message: 'CV chat provider is unavailable' },
    })));

    component.compareCandidates();
    fixture.detectChanges();

    expect(component.comparisonError).toBe('CV chat provider is unavailable');
    expect(component.comparisonQuestion).toBe('Compare backend experience');
    expect(fixture.nativeElement.textContent).toContain('Retry');
  });

  it('renders a controlled safe response without requiring citations', () => {
    chatService.ask.and.returnValue(of({
      answer: 'The available CV evidence does not provide enough information to answer this question.',
      citations: [],
    }));
    component.chatQuestion = 'Does the CV state a Kubernetes certification?';

    component.askCv();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('does not provide enough information');
    expect(fixture.nativeElement.textContent).toContain('no source citation is required');
  });

  it('prevents duplicate chat submissions while a request is active', () => {
    const pending = new Subject<never>();
    chatService.ask.and.returnValue(pending);
    component.chatQuestion = 'Summarize the stated skills.';

    component.askCv();
    component.askCv();
    fixture.detectChanges();

    expect(chatService.ask).toHaveBeenCalledTimes(1);
    expect(component.chatBusy).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('Reviewing the authorized CV evidence');
  });

  it('shows a structured chat error and keeps the question available for retry', () => {
    chatService.ask.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 502, error: { message: 'CV chat provider is unavailable' },
    })));
    component.chatQuestion = 'Summarize the CV.';

    component.askCv();
    fixture.detectChanges();

    expect(component.chatError).toBe('CV chat provider is unavailable');
    expect(component.chatQuestion).toBe('Summarize the CV.');
    expect(fixture.nativeElement.textContent).toContain('Retry');
  });

  it('does not expose misleading browser status text for an unstructured chat failure', () => {
    chatService.ask.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 500, statusText: 'OK',
    })));
    component.chatQuestion = 'Summarize the CV.';

    component.askCv();

    expect(component.chatError).toBe('The service is temporarily unavailable. Please try again later.');
    expect(component.chatError).not.toContain('500 OK');
  });

  it('does not offer CV chat to a read-only user', () => {
    auth.user.roles = ['READ_ONLY'];
    fixture.detectChanges();

    expect(fixture.debugElement.query(By.css('.cv-chat'))).toBeNull();
    expect(fixture.debugElement.query(By.css('.candidate-comparison'))).toBeNull();
  });

  it('does not submit or record an unchanged metadata correction', () => {
    component.editMetadata();

    component.saveMetadata();
    fixture.detectChanges();

    expect(service.updateCandidateMetadata).not.toHaveBeenCalled();
    expect(component.metadataDraft).toBeNull();
    expect(component.notice).toBe('No changes to save.');
    expect(fixture.nativeElement.textContent).toContain('No changes to save.');
  });

  it('does not evaluate without both selections', () => {
    component.selectedCandidateId = null;
    component.evaluate();
    expect(service.evaluate).not.toHaveBeenCalled();
  });

  it('renders all evaluation metrics and explanation', () => {
    service.evaluate.and.returnValue(of(evaluationResponse()));
    component.evaluate();
    fixture.detectChanges();
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('82');
    expect(text).toContain('Skills match');
    expect(text).toContain('AI confidence');
    expect(text).toContain('Strong backend alignment');
  });

  it('renders the current evaluation and then persisted history immediately after its controls', () => {
    service.evaluate.and.returnValue(of(evaluationResponse()));

    component.evaluate();
    fixture.detectChanges();

    const form = fixture.nativeElement.querySelector('.evaluation-form') as HTMLElement;
    const result = fixture.nativeElement.querySelector('.evaluation-result') as HTMLElement;
    const history = fixture.nativeElement.querySelector('.evaluation-history') as HTMLElement;
    const details = fixture.nativeElement.querySelector('.candidate-details') as HTMLElement;
    expect(form.nextElementSibling).toBe(result);
    expect(result.nextElementSibling).toBe(history);
    expect(history.compareDocumentPosition(details) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('renders persisted evaluation history immediately after the controls when no current result exists', () => {
    component.evaluation = null;
    fixture.detectChanges();

    const form = fixture.nativeElement.querySelector('.evaluation-form') as HTMLElement;
    const history = fixture.nativeElement.querySelector('.evaluation-history') as HTMLElement;
    const details = fixture.nativeElement.querySelector('.candidate-details') as HTMLElement;
    expect(form.nextElementSibling).toBe(history);
    expect(history.compareDocumentPosition(details) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('renders current and stale persisted evaluation history', () => {
    const stale = { ...historyItem(), id: 2, current: false, invalidatedAt: '2026-10-23T09:00:00Z' };
    component.evaluationHistory = [historyItem(), stale];
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Evaluation history');
    expect(text).toContain('Backend Engineer');
    expect(text).toContain('Current');
    expect(text).toContain('Stale');
  });

  it('clears stale evaluation and displays structured provider errors', () => {
    component.evaluation = evaluationResponse();
    service.evaluate.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 502, error: { message: 'Evaluation provider returned invalid JSON' },
    })));
    component.evaluate();
    fixture.detectChanges();
    expect(component.evaluation).toBeNull();
    expect(component.error).toBe('Evaluation provider returned invalid JSON');
  });

  it('does not expose misleading browser status text for unstructured server errors', () => {
    service.evaluate.and.returnValue(throwError(() => new HttpErrorResponse({
      status: 500, statusText: 'OK',
    })));

    component.evaluate();

    expect(component.error).toBe('The service is temporarily unavailable. Please try again later.');
    expect(component.error).not.toContain('500 OK');
  });

  it('confirms and erases a candidate as an administrator', () => {
    spyOn(window, 'confirm').and.returnValue(true);
    service.eraseCandidate.and.returnValue(of(void 0));
    component.evaluation = evaluationResponse();

    component.eraseCandidate();

    expect(window.confirm).toHaveBeenCalled();
    expect(service.eraseCandidate).toHaveBeenCalledOnceWith(7);
    expect(component.evaluation).toBeNull();
    expect(service.getCandidates).toHaveBeenCalledTimes(2);
  });

  it('does not erase when confirmation is cancelled', () => {
    spyOn(window, 'confirm').and.returnValue(false);

    component.eraseCandidate();

    expect(service.eraseCandidate).not.toHaveBeenCalled();
  });

  it('disables candidate erasure while any associated document is under legal hold', () => {
    component.candidateDetails!.documents[0].legalHold = true;
    const confirm = spyOn(window, 'confirm');
    fixture.detectChanges();

    component.eraseCandidate();

    const eraseButton = fixture.debugElement.queryAll(By.css('button'))
      .find((button) => button.nativeElement.textContent.includes('Erase candidate'))!;
    expect(eraseButton.nativeElement.disabled).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('cannot be erased until all legal holds are released');
    expect(confirm).not.toHaveBeenCalled();
    expect(service.eraseCandidate).not.toHaveBeenCalled();
  });

  function importResult(status: CvImportResult['status']): CvImportResult {
    return {
      candidateId: status === 'IMPORTED' || status === 'DUPLICATE' ? 7 : null,
      documentId: 2, originalFilename: `${status}.pdf`, status,
      contentType: 'application/pdf', textLength: 120, warnings: status === 'IMPORTED' ? [] : ['Review outcome'],
    };
  }

  function archiveResult(results: CvImportResult[]): CvArchiveImportResult {
    const count = (status: CvImportResult['status']) => results.filter((item) => item.status === status).length;
    return {
      totalFiles: results.length, imported: count('IMPORTED'), duplicates: count('DUPLICATE'),
      needsReview: count('NEEDS_REVIEW'), skipped: count('SKIPPED'), failed: count('FAILED'), results,
    };
  }

  function evaluationResponse(): EvaluationResponse {
    return {
      explanation: 'Strong backend alignment',
      evaluation: {
        id: 1, candidateId: 7, jobId: 12, skillsMatchScore: 90, experienceRelevanceScore: 8,
        educationFitScore: 7, achievementImpactScore: 8, keywordDensityScore: 75,
        employmentGapScore: 9, readabilityScore: 8, aiConfidenceScore: 88,
        overallFitScore: 82, createdAt: '2026-08-01T00:00:00Z',
      },
    };
  }

  function historyItem(): EvaluationHistoryItem {
    return {
      ...evaluationResponse().evaluation,
      candidateName: 'Ada Lovelace', jobTitle: 'Backend Engineer', aiExplanation: 'Strong backend alignment',
      invalidatedAt: null, current: true,
    };
  }

  function candidateDetails(): CandidateDetails {
    return {
      id: 7, name: 'Ada Lovelace', email: 'ada@example.com', location: 'London', currentTitle: 'Engineer',
      yearsExperience: 5, createdAt: '2026-10-01T00:00:00Z', documents: [{
        id: 2, originalFilename: 'ada.pdf', contentType: 'application/pdf', fileSize: 1024,
        source: 'USER_UPLOAD', status: 'IMPORTED', textLength: 120,
        extractionMethod: 'NATIVE', extractionConfidence: null, extractionPageCount: 0,
        importedAt: '2026-10-01T00:00:00Z',
        reprocessedAt: null,
        originalAvailable: true, storedAt: '2026-10-01T00:00:00Z', retentionUntil: '2027-10-01T00:00:00Z',
        legalHold: false, legalHoldAt: null, downloadAllowed: true, administrationAllowed: true,
      }], revisions: [], correctionAllowed: true,
    };
  }
});
