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
});
