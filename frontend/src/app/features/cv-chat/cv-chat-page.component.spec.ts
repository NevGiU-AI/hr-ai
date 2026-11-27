import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../environments/environments';
import { ACTIVE_CV_CHAT_CONVERSATION_STORAGE_KEY } from '../../core/storage/application-storage-keys';
import { CvChatPageComponent } from './cv-chat-page.component';

describe('CvChatPageComponent', () => {
  let fixture: ComponentFixture<CvChatPageComponent>;
  let component: CvChatPageComponent;
  let http: HttpTestingController;
  let browserLanguage: jasmine.Spy;

  beforeEach(async () => {
    browserLanguage = spyOnProperty(navigator, 'language', 'get').and.returnValue('en-GB');
    localStorage.clear();
    await TestBed.configureTestingModule({
      imports: [CvChatPageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
    fixture = TestBed.createComponent(CvChatPageComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    http.expectOne(`${environment.apiUrl}/jobs`).flush([]);
    http.expectOne(`${environment.apiUrl}/chat/conversations`).flush([]);
  });

  afterEach(() => { http.verify(); localStorage.clear(); });

  it('uses the browser locale for recovery even when a different reply language is selected', () => {
    browserLanguage.and.returnValue('fr-FR');
    const frenchFixture = TestBed.createComponent(CvChatPageComponent);
    const frenchPage = frenchFixture.componentInstance;
    frenchPage.controlledResponseLanguage = 'pt';
    frenchPage.resumeConversation('conversation-fr');
    const url = `${environment.apiUrl}/chat/conversations/conversation-fr`;
    http.expectOne(`${url}/messages`);
    http.expectOne(url).flush(null, { status: 502, statusText: 'Bad Gateway' });
    frenchFixture.detectChanges();
    http.expectOne(`${environment.apiUrl}/jobs`).flush([]);
    http.expectOne(`${environment.apiUrl}/chat/conversations`).flush([]);
    const alert: HTMLElement = frenchFixture.nativeElement.querySelector('[role="alert"]');
    expect(frenchFixture.nativeElement.querySelector('h1')?.textContent).toBe('Chat CV');
    expect(frenchFixture.nativeElement.querySelector('.welcome-message')?.textContent).toContain('Bonjour !');
    expect(alert.textContent).toContain('Impossible de charger la conversation pour le moment.');
    expect(alert.querySelector('button')?.textContent).toContain('Réessayer de charger la conversation');
  });

  it('shows the application-owned NevGiU welcome before a conversation is created', () => {
    expect(fixture.nativeElement.querySelector('.welcome-message').textContent)
      .toContain("Hey! I'm NevGiU—short for ‘Never Give Up.’");
  });

  it('lists and resumes an owner-scoped recent conversation with persisted citation cards', () => {
    const conversation = {
      id: 'conversation-1', version: 2, candidates: [{ id: 7, name: 'Ada', currentTitle: 'Engineer' }], job: null,
      createdAt: '2026-11-12T00:00:00Z', updatedAt: '2026-11-13T00:00:00Z', expiresAt: '2026-11-20T00:00:00Z',
    };
    component.recentConversations = [conversation];
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.recent-item').click();

    http.expectOne(`${environment.apiUrl}/chat/conversations/conversation-1`).flush(conversation);
    http.expectOne(`${environment.apiUrl}/chat/conversations/conversation-1/messages`).flush({ messages: [
      { role: 'USER', content: 'Which Java version?', citations: [] },
      { role: 'ASSISTANT', content: 'Java 17 [CV-7].', citations: [
        { reference: 'CV-7', candidateId: 7, documentId: 7, pageNumber: null, excerpt: '' },
      ] },
      { role: 'ASSISTANT', content: 'Les éléments ne suffisent pas.', kind: 'INSUFFICIENT_EVIDENCE',
        responseLanguage: 'fr', citations: [] },
    ] });

    expect(component.conversation?.id).toBe('conversation-1');
    expect(component.timeline[1].citations?.[0].reference).toBe('CV-7');
    fixture.detectChanges();
    const labels: NodeListOf<HTMLElement> = fixture.nativeElement.querySelectorAll('.message-outcome');
    expect(Array.from(labels, (label) => label.textContent?.trim())).toEqual(['Insufficient evidence']);
    expect(fixture.nativeElement.querySelector('.message-content[lang="fr"]')?.textContent)
      .toContain('Les éléments ne suffisent pas.');
    expect(fixture.nativeElement.querySelectorAll('.message-content[lang]').length).toBe(1);
  });

  it('retries a transient conversation resume without forgetting the saved ID', () => {
    const id = 'conversation-1';
    const url = `${environment.apiUrl}/chat/conversations/${id}`;
    const conversation = {
      id, version: 1, candidates: [], job: null, createdAt: '', updatedAt: '', expiresAt: '',
    };
    localStorage.setItem(ACTIVE_CV_CHAT_CONVERSATION_STORAGE_KEY, id);
    component.resumeConversation(id);
    http.expectOne(url).flush(conversation);
    http.expectOne(`${url}/messages`).flush(null, { status: 502, statusText: 'Bad Gateway' });

    expect(localStorage.getItem(ACTIVE_CV_CHAT_CONVERSATION_STORAGE_KEY)).toBe(id);
    fixture.detectChanges();
    const retry: HTMLButtonElement = fixture.nativeElement.querySelector('[role="alert"] button');
    expect(retry.textContent).toContain('Retry loading conversation');
    retry.click();

    http.expectOne(url).flush(conversation);
    http.expectOne(`${url}/messages`).flush({ messages: [{ role: 'USER', content: 'Saved question' }] });
    expect(component.conversation?.id).toBe(id);
    expect(component.timeline[0].content).toBe('Saved question');
    expect(component.resumeError).toBe('');
    expect(component.canRetryResume).toBe(false);
  });

  it('forgets an unavailable saved conversation without clearing the current discovery draft', () => {
    const id = 'conversation-1';
    const url = `${environment.apiUrl}/chat/conversations/${id}`;
    localStorage.setItem(ACTIVE_CV_CHAT_CONVERSATION_STORAGE_KEY, id);
    component.pendingQuestion = 'Compare backend experience';
    component.resumeConversation(id);
    const history = http.expectOne(`${url}/messages`);
    http.expectOne(url).flush(null, { status: 404, statusText: 'Not Found' });

    expect(history.cancelled).toBe(true);
    expect(localStorage.getItem(ACTIVE_CV_CHAT_CONVERSATION_STORAGE_KEY)).toBeNull();
    expect(component.pendingQuestion).toBe('Compare backend experience');
    expect(component.canRetryResume).toBe(false);
    expect(component.resumeError).toContain('no longer available');
  });

  it('checks persisted history after an uncertain send without replaying the question', () => {
    const url = `${environment.apiUrl}/chat/conversations/conversation-1`;
    const conversation = {
      id: 'conversation-1', version: 1, candidates: [], job: null, createdAt: '', updatedAt: '', expiresAt: '',
    };
    component.conversation = conversation;
    component.message = 'Saved despite timeout?';
    component.send();
    http.expectOne({ method: 'POST', url: `${url}/messages` })
      .flush(null, { status: 504, statusText: 'Gateway Timeout' });
    expect(component.message).toBe('Saved despite timeout?');
    expect(component.sendNeedsHistoryCheck).toBeTrue();
    component.send();
    http.expectNone({ method: 'POST', url: `${url}/messages` });
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.composer [role="status"] button').click();
    http.expectOne({ method: 'GET', url }).flush({ ...conversation, version: 2 });
    http.expectOne({ method: 'GET', url: `${url}/messages` }).flush({ messages: [
      { role: 'USER', content: 'Saved despite timeout?' },
      { role: 'ASSISTANT', content: 'Persisted answer' },
    ] });
    expect(component.timeline[1].content).toBe('Persisted answer');
    expect(component.sendNeedsHistoryCheck).toBeFalse();
    expect(component.sendFeedback).toContain('do not send it again');
    http.expectNone({ method: 'POST', url: `${url}/messages` });
  });

  it('keeps an editable draft after a rejected send without requiring a history check', () => {
    component.conversation = {
      id: 'conversation-1', version: 1, candidates: [], job: null, createdAt: '', updatedAt: '', expiresAt: '',
    };
    component.message = 'Draft question';
    component.send();
    http.expectOne(`${environment.apiUrl}/chat/conversations/conversation-1/messages`)
      .flush({ message: 'Invalid question' }, { status: 400, statusText: 'Bad Request' });
    expect(component.message).toBe('Draft question');
    expect(component.sendFeedback).toBe('Message could not be sent.');
    expect(component.sendNeedsHistoryCheck).toBeFalse();
    expect(component.timeline).toEqual([]);
  });

  it('creates an explicit candidate-scoped conversation and renders its scope', () => {
    component.selectedCandidates = [{ id: 7, name: 'Ada', currentTitle: 'Engineer' }];
    component.createConversation();
    const request = http.expectOne(`${environment.apiUrl}/chat/conversations`);
    expect(request.request.body).toEqual({ candidateIds: [7] });
    request.flush({
      id: 'conversation-1', version: 0, candidates: component.selectedCandidates, job: null,
      createdAt: '2026-11-12T00:00:00Z', updatedAt: '2026-11-12T00:00:00Z', expiresAt: '2026-11-19T00:00:00Z',
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.scope-panel').textContent).toContain('Ada');
    expect(fixture.nativeElement.querySelector('.welcome-message').textContent)
      .toContain("Hey! I'm NevGiU—short for ‘Never Give Up.’");
    expect(localStorage.getItem('hr-ai-active-cv-chat-conversation')).toBe('conversation-1');
  });

  it('adds the recruiter turn and grounded cited answer to the timeline', () => {
    component.conversation = {
      id: 'conversation-1', version: 0, candidates: [{ id: 7, name: 'Ada', currentTitle: null }], job: null,
      createdAt: '', updatedAt: '', expiresAt: '',
    };
    component.message = 'Which Java versions?';
    component.send();
    const request = http.expectOne(`${environment.apiUrl}/chat/conversations/conversation-1/messages`);
    request.flush({
      conversationId: 'conversation-1', version: 1, answer: 'Java 17 [CV-7].',
      candidates: component.conversation.candidates, job: null,
      citations: [{ reference: 'CV-7', candidateId: 7, documentId: 7, pageNumber: null, excerpt: 'Java 17' }],
      expiresAt: '2026-11-19T00:00:00Z',
    });
    expect(component.timeline.map((item) => item.role)).toEqual(['USER', 'ASSISTANT']);
    expect(component.timeline[1].citations?.[0].reference).toBe('CV-7');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.citations a').getAttribute('href'))
      .toBe('/candidates/import?candidateId=7#cv-document-7');
  });

  it('sends the selected controlled response language without changing the question payload', () => {
    component.conversation = {
      id: 'conversation-1', version: 0, candidates: [{ id: 7, name: 'Ada', currentTitle: null }], job: null,
      createdAt: '', updatedAt: '', expiresAt: '',
    };
    fixture.detectChanges();
    const select: HTMLSelectElement = fixture.nativeElement.querySelector('#cv-chat-response-language');
    select.selectedIndex = 3;
    select.dispatchEvent(new Event('change'));

    component.message = 'Quelle est son expérience avec Kubernetes ?';
    component.send();

    const request = http.expectOne(`${environment.apiUrl}/chat/conversations/conversation-1/messages`);
    expect(request.request.headers.get('Accept-Language')).toBe('pt');
    expect(request.request.body).toEqual({ message: 'Quelle est son expérience avec Kubernetes ?' });
    request.flush({
      conversationId: 'conversation-1', version: 1, kind: 'INSUFFICIENT_EVIDENCE',
      answer: 'A informação disponível não é suficiente.', responseLanguage: 'pt',
      candidates: component.conversation.candidates, job: null, citations: [], expiresAt: '',
    });
    expect(component.timeline.at(-1)?.kind).toBe('INSUFFICIENT_EVIDENCE');
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.message-content[lang="pt"]')?.textContent)
      .toContain('A informação disponível não é suficiente.');
  });

  it('uses the question language in Automatic mode and leaves ambiguous requests to the browser', () => {
    component.conversation = {
      id: 'conversation-1', version: 0, candidates: [], job: null, createdAt: '', updatedAt: '', expiresAt: '',
    };
    const url = `${environment.apiUrl}/chat/conversations/conversation-1/messages`;
    component.message = 'Quelle était son expérience professionnelle chez KLx ?';
    component.send();
    const french = http.expectOne(url);
    expect(french.request.headers.get('Accept-Language')).toBe('fr');
    french.flush({
      conversationId: 'conversation-1', version: 1, answer: 'Réponse.', candidates: [], job: null,
      citations: [], expiresAt: '',
    });

    component.message = 'OCR?';
    component.send();
    const ambiguous = http.expectOne(url);
    expect(ambiguous.request.headers.has('Accept-Language')).toBeFalse();
    ambiguous.flush({
      conversationId: 'conversation-1', version: 2, answer: 'Operational status.', candidates: [], job: null,
      citations: [], expiresAt: '',
    });
  });

  it('uses natural language to discover organization candidates before confirmation', () => {
    component.discoveryMode = 'ORGANIZATION';
    component.naturalRequest = 'Find strong AI developers in this organization';
    component.discoverNaturally();

    const request = http.expectOne(`${environment.apiUrl}/chat/tools/organization-discover`);
    request.flush({
      kind: 'CANDIDATE_CONFIRMATION_REQUIRED', message: 'Confirm the shortlist.', truncated: false,
      candidates: [{ id: 8, name: 'Grace', currentTitle: 'AI Engineer', evidenceReferences: ['CV-18'] }],
    });

    expect(component.naturalResultGroups.map((group) => group.requestedName)).toEqual([null]);
    expect(component.pendingQuestion).toBe('Find strong AI developers in this organization');
    expect(component.discoveryTimeline.map((item) => item.role)).toEqual(['USER', 'ASSISTANT']);
    expect(component.naturalRequest).toBe('');

    fixture.detectChanges();
    const confirmationCards = fixture.nativeElement.querySelector('[aria-label="Candidate confirmation cards"]');
    expect(confirmationCards.textContent).toContain('Grace');
    expect(fixture.nativeElement.querySelector('.discovery-composer .result')).toBeNull();
    expect(fixture.nativeElement.querySelector('.scope-confirmation')).toBeNull();
    http.expectNone(`${environment.apiUrl}/chat/conversations`);

    confirmationCards.querySelector('button.result').click();
    fixture.detectChanges();
    expect(component.selectedCandidates.map((candidate) => candidate.id)).toEqual([8]);
    expect(fixture.nativeElement.querySelector('.scope-confirmation').textContent)
      .toContain('Confirm conversation scope');
  });

  it('groups name matches in reference order without selecting candidates or duplicating the scope', () => {
    component.selectedCandidates = [{ id: 99, name: 'Previous request', currentTitle: null }];
    component.naturalRequest = 'Compare Ada and Grace for Java';
    component.discoverNaturally();

    const request = http.expectOne(`${environment.apiUrl}/chat/tools/discover`);
    request.flush({
      kind: 'CANDIDATE_CONFIRMATION_REQUIRED', message: 'Confirm cards.',
      candidateScope: { message: component.naturalRequest, references: [
        { requestedName: 'Ada', status: 'CHOICES', truncated: true, candidates: [
          { id: 7, name: 'Ada Lovelace', currentTitle: 'Engineer' },
          { id: 9, name: 'Ada Byron', currentTitle: null },
        ] },
        { requestedName: 'Grace', status: 'CHOICES', truncated: false,
          candidates: [{ id: 7, name: 'Ada Lovelace', currentTitle: 'Engineer' },
            { id: 8, name: 'Grace', currentTitle: 'Architect' }] },
      ] },
    });

    expect(component.selectedCandidates).toEqual([]);
    fixture.detectChanges();
    const groups = fixture.nativeElement.querySelectorAll('.candidate-match-group') as NodeListOf<HTMLElement>;
    expect(Array.from(groups).map((group) => group.querySelector('h3')?.textContent))
      .toEqual(['Matches for Ada', 'Matches for Grace']);
    expect(groups[0].textContent).toContain('Ada Byron');
    expect(groups[0].textContent).toContain('More matches exist');
    expect(groups[1].textContent).not.toContain('Ada Byron');
    groups[0].querySelector('button')?.click();
    fixture.detectChanges();
    expect(groups[1].querySelector('button')?.disabled).toBeTrue();
    expect(component.selectedCandidates.map((candidate) => candidate.id)).toEqual([7]);
    http.expectNone(`${environment.apiUrl}/chat/conversations`);
  });

  it('keeps clarification in the timeline without creating or widening candidate scope', () => {
    component.selectedCandidates = [{ id: 99, name: 'Previous request', currentTitle: null }];
    component.naturalRequest = 'Compare them';
    component.discoverNaturally();

    http.expectOne(`${environment.apiUrl}/chat/tools/discover`).flush({
      kind: 'CLARIFICATION_REQUIRED', message: 'Please name the candidates.', candidateScope: null,
    });

    fixture.detectChanges();
    expect(component.selectedCandidates).toEqual([]);
    expect(component.naturalResultGroups).toEqual([]);
    expect(component.pendingQuestion).toBe('');
    expect(component.discoveryTimeline.at(-1)?.content).toBe('Please name the candidates.');
    expect(fixture.nativeElement.querySelector('[aria-label="Candidate confirmation cards"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('.scope-confirmation')).toBeNull();
    http.expectNone(`${environment.apiUrl}/chat/conversations`);
  });

  it('restores the conversational request when discovery fails so the recruiter can retry', () => {
    component.naturalRequest = 'Compare Ada and Grace';
    component.discoverNaturally();

    http.expectOne(`${environment.apiUrl}/chat/tools/discover`).flush(
      { message: 'Provider unavailable' }, { status: 502, statusText: 'Bad Gateway' },
    );

    expect(component.naturalRequest).toBe('Compare Ada and Grace');
    expect(component.error).toBe('Candidate discovery could not complete.');
    expect(component.pendingQuestion).toBe('');
  });

  it('submits the confirmed natural-language request after creating durable scope', () => {
    component.naturalRequest = 'Compare Ada and Grace for Java';
    component.pendingQuestion = component.naturalRequest;
    component.selectedCandidates = [
      { id: 7, name: 'Ada', currentTitle: 'Engineer' },
      { id: 8, name: 'Grace', currentTitle: 'Architect' },
    ];

    component.createConversation();
    http.expectOne(`${environment.apiUrl}/chat/conversations`).flush({
      id: 'conversation-1', version: 0, candidates: component.selectedCandidates, job: null,
      createdAt: '', updatedAt: '', expiresAt: '',
    });

    const question = http.expectOne(`${environment.apiUrl}/chat/conversations/conversation-1/messages`);
    expect(question.request.body).toEqual({ message: 'Compare Ada and Grace for Java' });
    question.flush({
      conversationId: 'conversation-1', version: 1, answer: 'Ada and Grace both use Java.',
      candidates: component.selectedCandidates, job: null, citations: [], expiresAt: '',
    });

    expect(component.timeline.map((item) => item.role)).toEqual(['USER', 'ASSISTANT']);
    expect(component.discoveryTimeline).toEqual([]);
  });

  it('replaces explicit conversation scope and clears the stale timeline', () => {
    component.conversation = {
      id: 'conversation-1', version: 3, candidates: [{ id: 7, name: 'Ada', currentTitle: 'Engineer' }], job: null,
      createdAt: '', updatedAt: '', expiresAt: '',
    };
    component.timeline = [{ role: 'ASSISTANT', content: 'Old scoped answer.' }];
    component.jobs = [{ id: 12, title: 'Backend Engineer', level: 'Senior' } as never];

    component.beginScopeEdit();
    component.addCandidate({ id: 8, name: 'Grace', currentTitle: 'Architect' });
    component.selectedJobId = 12;
    component.saveScope();

    const request = http.expectOne(`${environment.apiUrl}/chat/conversations/conversation-1/scope`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ version: 3, candidateIds: [7, 8], jobId: 12 });
    request.flush({
      ...component.conversation,
      version: 4,
      candidates: component.selectedCandidates,
      job: { id: 12, title: 'Backend Engineer', level: 'Senior' },
      updatedAt: '2026-11-18T00:00:00Z',
    });

    expect(component.conversation?.version).toBe(4);
    expect(component.timeline).toEqual([]);
    expect(component.scopeEditing).toBeFalse();
  });

  it('cancels scope editing without changing the persisted conversation', () => {
    component.conversation = {
      id: 'conversation-1', version: 3, candidates: [{ id: 7, name: 'Ada', currentTitle: 'Engineer' }], job: null,
      createdAt: '', updatedAt: '', expiresAt: '',
    };

    component.beginScopeEdit();
    component.addCandidate({ id: 8, name: 'Grace', currentTitle: 'Architect' });
    component.message = 'Draft question';
    fixture.detectChanges();
    const page: HTMLElement = fixture.nativeElement;
    expect(page.querySelector('.scope-editor')?.textContent).toContain('Save scope and clear history');
    expect(page.querySelector<HTMLButtonElement>('.composer button[type="submit"]')?.disabled).toBeTrue();
    component.send();
    http.expectNone(`${environment.apiUrl}/chat/conversations/conversation-1/messages`);

    component.cancelScopeEdit();
    fixture.detectChanges();
    expect(component.message).toBe('Draft question');
    expect(page.querySelector<HTMLButtonElement>('.composer button[type="submit"]')?.disabled).toBeFalse();

    expect(component.selectedCandidates.map((candidate) => candidate.id)).toEqual([7]);
    expect(component.scopeEditing).toBeFalse();
    http.expectNone(`${environment.apiUrl}/chat/conversations/conversation-1/scope`);
  });

  it('does not replace an unchanged scope because replacement clears conversation history', () => {
    component.conversation = {
      id: 'conversation-1', version: 3, candidates: [{ id: 7, name: 'Ada', currentTitle: 'Engineer' }], job: null,
      createdAt: '', updatedAt: '', expiresAt: '',
    };

    component.beginScopeEdit();
    expect(component.scopeHasChanges()).toBeFalse();
    component.saveScope();

    http.expectNone(`${environment.apiUrl}/chat/conversations/conversation-1/scope`);
  });

});
