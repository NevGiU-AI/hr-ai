import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environments';
import { CvIngestionService } from './cv-ingestion.service';

describe('CvIngestionService', () => {
  let service: CvIngestionService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(CvIngestionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('uploads a PDF as multipart form data', () => {
    const file = new File(['%PDF-test'], 'candidate.pdf', { type: 'application/pdf' });
    service.uploadPdf(file).subscribe();

    const request = http.expectOne(`${environment.apiUrl}/candidates/import`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body instanceof FormData).toBeTrue();
    expect(((request.request.body as FormData).get('file') as File).name).toBe('candidate.pdf');
    request.flush({ status: 'IMPORTED' });
  });

  it('submits an explicit candidate and job evaluation', () => {
    service.evaluate(7, 12).subscribe();

    const request = http.expectOne(`${environment.apiUrl}/evaluations`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ candidateId: 7, jobId: 12, weights: null });
    request.flush({ evaluation: {}, explanation: 'Good fit' });
  });

  it('retrieves tenant-scoped evaluation history for a candidate', () => {
    service.getEvaluationHistory(7).subscribe();

    const request = http.expectOne(`${environment.apiUrl}/evaluations?candidateId=7`);
    expect(request.request.method).toBe('GET');
    request.flush([]);
  });

  it('submits explicit confirmation when erasing a candidate', () => {
    service.eraseCandidate(7).subscribe();

    const request = http.expectOne(`${environment.apiUrl}/candidates/7`);
    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toEqual({ confirmed: true });
    request.flush(null);
  });

  it('uploads a ZIP as multipart form data', () => {
    const file = new File(['archive'], 'candidates.zip', { type: 'application/zip' });
    service.uploadArchive(file).subscribe();
    const request = http.expectOne(`${environment.apiUrl}/candidates/import/archive`);
    expect(request.request.method).toBe('POST');
    expect(((request.request.body as FormData).get('file') as File).name).toBe('candidates.zip');
    request.flush({ totalFiles: 0, results: [] });
  });

  it('loads the built-in archive without sending a file', () => {
    service.importInitial().subscribe();
    const request = http.expectOne(`${environment.apiUrl}/candidates/import/initial`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush({ totalFiles: 0, results: [] });
  });

  it('retrieves candidates and approved jobs', () => {
    let candidateCount = -1;
    let jobCount = -1;
    service.getCandidates().subscribe((items) => candidateCount = items.length);
    service.getJobs().subscribe((items) => jobCount = items.length);
    http.expectOne(`${environment.apiUrl}/candidates`).flush([]);
    http.expectOne(`${environment.apiUrl}/jobs`).flush([]);
    expect(candidateCount).toBe(0);
    expect(jobCount).toBe(0);
  });

  it('retrieves candidate details and controls the stored original', () => {
    service.getCandidateDetails(7).subscribe();
    http.expectOne(`${environment.apiUrl}/candidates/7/details`).flush({ id: 7, documents: [] });

    service.downloadOriginal(9).subscribe();
    const download = http.expectOne(`${environment.apiUrl}/cv-documents/9/original`);
    expect(download.request.responseType).toBe('blob');
    download.flush(new Blob(['pdf']));

    service.updateLegalHold(9, true).subscribe();
    const hold = http.expectOne(`${environment.apiUrl}/cv-documents/9/legal-hold`);
    expect(hold.request.method).toBe('PUT');
    expect(hold.request.body).toEqual({ held: true });
    hold.flush(null);

    service.deleteOriginal(9).subscribe();
    expect(http.expectOne(`${environment.apiUrl}/cv-documents/9/original`).request.method).toBe('DELETE');

    service.updateCandidateMetadata(7, { name: 'Ada', email: null, location: null,
      currentTitle: null, yearsExperience: null }).subscribe();
    expect(http.expectOne(`${environment.apiUrl}/candidates/7/metadata`).request.method).toBe('PUT');

    service.reprocessDocument(9).subscribe();
    expect(http.expectOne(`${environment.apiUrl}/cv-documents/9/reprocess`).request.method).toBe('POST');
  });

  it('propagates structured backend errors', () => {
    let message = '';
    service.importInitial().subscribe({ error: (error) => message = error.error.message });
    http.expectOne(`${environment.apiUrl}/candidates/import/initial`).flush(
      { message: 'Initial CV import is disabled' }, { status: 403, statusText: 'Forbidden' });
    expect(message).toBe('Initial CV import is disabled');
  });
});
