import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { environment } from '../../../environments/environments';
import { CvChatPageComponent } from './cv-chat-page.component';

describe('CvChatPageComponent', () => {
  let fixture: ComponentFixture<CvChatPageComponent>;
  let component: CvChatPageComponent;
  let http: HttpTestingController;

  beforeEach(async () => {
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
  });

  afterEach(() => { http.verify(); localStorage.clear(); });

  it('shows the application-owned NevGiU welcome before a conversation is created', () => {
    expect(fixture.nativeElement.querySelector('.welcome-message').textContent)
      .toContain("Hey! I'm NevGiU—short for ‘Never Give Up.’");
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

  it('uses natural language to discover organization candidates before confirmation', () => {
    component.discoveryMode = 'ORGANIZATION';
    component.naturalRequest = 'Find strong AI developers in this organization';
    component.discoverNaturally();

    const request = http.expectOne(`${environment.apiUrl}/chat/tools/organization-discover`);
    request.flush({
      kind: 'CANDIDATE_CONFIRMATION_REQUIRED', message: 'Confirm the shortlist.', truncated: false,
      candidates: [{ id: 8, name: 'Grace', currentTitle: 'AI Engineer', evidenceReferences: ['CV-18'] }],
    });

    expect(component.naturalResults.map((candidate) => candidate.id)).toEqual([8]);
    expect(component.pendingQuestion).toBe('Find strong AI developers in this organization');
  });

  it('uses the reviewed name-discovery tools for natural-language candidate selection', () => {
    component.naturalRequest = 'Compare Ada and Grace for Java';
    component.discoverNaturally();

    const request = http.expectOne(`${environment.apiUrl}/chat/tools/discover`);
    request.flush({
      kind: 'CANDIDATE_CONFIRMATION_REQUIRED', message: 'Confirm cards.',
      candidateScope: { message: component.naturalRequest, references: [
        { requestedName: 'Ada', status: 'CHOICES', truncated: false,
          candidates: [{ id: 7, name: 'Ada', currentTitle: 'Engineer' }] },
        { requestedName: 'Grace', status: 'CHOICES', truncated: false,
          candidates: [{ id: 8, name: 'Grace', currentTitle: 'Architect' }] },
      ] },
    });

    expect(component.naturalResults.map((candidate) => candidate.id)).toEqual([7, 8]);
  });

  it('accepts one partial-name discovery result for single-candidate chat', () => {
    component.naturalRequest = 'Fanon';
    component.discoverNaturally();

    const request = http.expectOne(`${environment.apiUrl}/chat/tools/discover`);
    request.flush({
      kind: 'CANDIDATE_CONFIRMATION_REQUIRED', message: 'Confirm cards.',
      candidateScope: { message: component.naturalRequest, references: [{
        requestedName: 'Fanon', status: 'CHOICES', truncated: false,
        candidates: [{ id: 7, name: 'Fanon Jupkwo', currentTitle: 'Java Engineer' }],
      }] },
    });

    expect(component.naturalResults).toEqual([
      { id: 7, name: 'Fanon Jupkwo', currentTitle: 'Java Engineer' },
    ]);
    expect(component.pendingQuestion).toBe('Fanon');
  });
});
