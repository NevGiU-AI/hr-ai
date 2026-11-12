import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environments';
import { CvChatService } from './cv-chat.service';

describe('CvChatService', () => {
  let service: CvChatService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(CvChatService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks about one explicit candidate without sending tenant or role context', () => {
    service.ask(7, 'Which skills are stated?').subscribe();

    const request = http.expectOne(`${environment.apiUrl}/chat/candidates/7`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ message: 'Which skills are stated?' });
    request.flush({ answer: 'Java [CV-2]', citations: [] });
  });

  it('searches by partial name without sending tenant or role context', () => {
    service.searchCandidates('Fanon').subscribe();

    const request = http.expectOne(`${environment.apiUrl}/chat/candidates/search`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ query: 'Fanon' });
    request.flush({ candidates: [], truncated: false });
  });

  it('compares only the explicit candidate IDs without sending tenant context', () => {
    service.compare([7, 8], 'Compare backend experience').subscribe();

    const request = http.expectOne(`${environment.apiUrl}/chat/candidates/compare`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      candidateIds: [7, 8],
      message: 'Compare backend experience',
    });
    request.flush({ answer: 'Comparison [CV-2] [CV-3]', candidates: [], citations: [] });
  });

  it('includes the explicitly confirmed approved job in the final comparison', () => {
    service.compare([7, 8], 'Compare for this approved job', 12).subscribe();

    const request = http.expectOne(`${environment.apiUrl}/chat/candidates/compare`);
    expect(request.request.body).toEqual({
      candidateIds: [7, 8],
      message: 'Compare for this approved job',
      jobId: 12,
    });
    request.flush({ answer: 'Comparison [CV-2] [CV-3]', candidates: [], citations: [] });
  });

  it('runs one-pass tool discovery without sending browser-owned tenant or role context', () => {
    service.discoverWithTools('Compare Ada and Grace').subscribe();

    const request = http.expectOne(`${environment.apiUrl}/chat/tools/discover`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ message: 'Compare Ada and Grace' });
    request.flush({
      kind: 'CANDIDATE_CONFIRMATION_REQUIRED',
      message: 'Confirm candidate cards.',
      candidateScope: {
        message: 'Compare Ada and Grace',
        references: [
          { requestedName: 'Ada', status: 'CHOICES', candidates: [], truncated: false },
          { requestedName: 'Grace', status: 'CHOICES', candidates: [], truncated: false },
        ],
      },
    });
  });

  it('plans a confirmed scope and omits the optional job when none is selected', () => {
    service.planConfirmedScope('Compare Ada and Grace', [7, 8], null).subscribe();

    const request = http.expectOne(`${environment.apiUrl}/chat/tools/confirmed-scope`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ message: 'Compare Ada and Grace', candidateIds: [7, 8] });
    request.flush({ kind: 'CONFIRMED_SCOPE_READY', message: 'Scope is ready.' });
  });

  it('includes an explicitly selected approved job in confirmed-scope planning', () => {
    service.planConfirmedScope('Compare Ada and Grace', [7, 8], 12).subscribe();

    const request = http.expectOne(`${environment.apiUrl}/chat/tools/confirmed-scope`);
    expect(request.request.body).toEqual({
      message: 'Compare Ada and Grace',
      candidateIds: [7, 8],
      jobId: 12,
    });
    request.flush({ kind: 'CONFIRMED_SCOPE_READY', message: 'Scope is ready.' });
  });

  it('creates and continues an owner-scoped conversation without browser-owned tenant fields', () => {
    service.createConversation([7, 8], 12).subscribe();
    const create = http.expectOne(`${environment.apiUrl}/chat/conversations`);
    expect(create.request.body).toEqual({ candidateIds: [7, 8], jobId: 12 });
    create.flush({ id: 'conversation-1' });

    service.askConversation('conversation-1', 'Which one has stronger Java evidence?').subscribe();
    const ask = http.expectOne(`${environment.apiUrl}/chat/conversations/conversation-1/messages`);
    expect(ask.request.method).toBe('POST');
    expect(ask.request.body).toEqual({ message: 'Which one has stronger Java evidence?' });
    ask.flush({ answer: 'Grounded answer' });
  });

  it('loads bounded conversation history and deletes the conversation', () => {
    service.getConversationHistory('conversation-1').subscribe();
    const history = http.expectOne(`${environment.apiUrl}/chat/conversations/conversation-1/messages`);
    expect(history.request.method).toBe('GET');
    history.flush({ messages: [] });

    service.deleteConversation('conversation-1').subscribe();
    const deletion = http.expectOne(`${environment.apiUrl}/chat/conversations/conversation-1`);
    expect(deletion.request.method).toBe('DELETE');
    deletion.flush(null);
  });
});
