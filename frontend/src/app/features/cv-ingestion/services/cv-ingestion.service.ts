import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../../environments/environments';
import { Candidate, CandidateDetails, CandidateMetadataUpdate, CvArchiveImportResult, CvImportResult,
  EvaluationHistoryItem, EvaluationResponse, Job } from '../models/cv-ingestion.models';

@Injectable({ providedIn: 'root' })
export class CvIngestionService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = environment.apiUrl;

  uploadPdf(file: File): Observable<CvImportResult> {
    return this.http.post<CvImportResult>(`${this.apiUrl}/candidates/import`, this.formData(file));
  }

  uploadArchive(file: File): Observable<CvArchiveImportResult> {
    return this.http.post<CvArchiveImportResult>(`${this.apiUrl}/candidates/import/archive`, this.formData(file));
  }

  importInitial(): Observable<CvArchiveImportResult> {
    return this.http.post<CvArchiveImportResult>(`${this.apiUrl}/candidates/import/initial`, {});
  }

  getCandidates(): Observable<Candidate[]> {
    return this.http.get<Candidate[]>(`${this.apiUrl}/candidates`);
  }

  getCandidateDetails(candidateId: number): Observable<CandidateDetails> {
    return this.http.get<CandidateDetails>(`${this.apiUrl}/candidates/${candidateId}/details`);
  }

  updateCandidateMetadata(candidateId: number, update: CandidateMetadataUpdate): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/candidates/${candidateId}/metadata`, update);
  }

  reprocessDocument(documentId: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/cv-documents/${documentId}/reprocess`, {});
  }

  downloadOriginal(documentId: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/cv-documents/${documentId}/original`, { responseType: 'blob' });
  }

  deleteOriginal(documentId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/cv-documents/${documentId}/original`);
  }

  updateLegalHold(documentId: number, held: boolean): Observable<void> {
    return this.http.put<void>(`${this.apiUrl}/cv-documents/${documentId}/legal-hold`, { held });
  }

  getJobs(): Observable<Job[]> {
    return this.http.get<Job[]>(`${this.apiUrl}/jobs`);
  }

  evaluate(candidateId: number, jobId: number): Observable<EvaluationResponse> {
    return this.http.post<EvaluationResponse>(`${this.apiUrl}/evaluations`, { candidateId, jobId, weights: null });
  }

  getEvaluationHistory(candidateId: number): Observable<EvaluationHistoryItem[]> {
    return this.http.get<EvaluationHistoryItem[]>(`${this.apiUrl}/evaluations?candidateId=${candidateId}`);
  }

  eraseCandidate(candidateId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/candidates/${candidateId}`, { body: { confirmed: true } });
  }

  private formData(file: File): FormData {
    const data = new FormData();
    data.append('file', file, file.name);
    return data;
  }
}
