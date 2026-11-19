import { CommonModule } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';
import { ACTIVE_CV_CHAT_CONVERSATION_STORAGE_KEY } from '../../core/storage/application-storage-keys';
import { ApiError, Job } from '../cv-ingestion/models/cv-ingestion.models';
import {
  CvChatCandidateOption,
  CvChatCitation,
  CvChatConversation,
  CvChatOrganizationDiscoveryResponse,
  CvChatTimelineMessage,
  CvChatToolDiscoveryResponse,
} from '../cv-ingestion/models/cv-chat.models';
import { CvChatService } from '../cv-ingestion/services/cv-chat.service';
import { CvIngestionService } from '../cv-ingestion/services/cv-ingestion.service';

@Component({
  selector: 'app-cv-chat-page',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './cv-chat-page.component.html',
  styleUrl: './cv-chat-page.component.scss',
})
export class CvChatPageComponent implements OnInit {
  /** Presentation-only greeting; it is never submitted to or persisted by the chat backend. */
  readonly welcomeMessage = "Hey! I'm NevGiU—short for ‘Never Give Up.’ "
    + "I'll always do my best to help you. 😊 How can I help with your candidates today?";

  private readonly chat = inject(CvChatService);
  private readonly candidates = inject(CvIngestionService);

  conversation: CvChatConversation | null = null;
  recentConversations: CvChatConversation[] = [];
  /** Pre-confirmation presentation only; durable history begins after explicit candidate confirmation. */
  discoveryTimeline: CvChatTimelineMessage[] = [];
  timeline: CvChatTimelineMessage[] = [];
  selectedCandidates: CvChatCandidateOption[] = [];
  jobs: Job[] = [];
  selectedJobId: number | null = null;
  searchQuery = '';
  searchResults: CvChatCandidateOption[] = [];
  discoveryMode: 'NAMES' | 'ORGANIZATION' = 'NAMES';
  naturalRequest = '';
  naturalResults: CvChatCandidateOption[] = [];
  naturalBusy = false;
  pendingQuestion = '';
  searchBusy = false;
  pageBusy = false;
  sendBusy = false;
  scopeEditing = false;
  message = '';
  error = '';

  ngOnInit(): void {
    this.candidates.getJobs().subscribe({ next: (jobs) => this.jobs = jobs });
    this.loadConversationList();
    const storedId = typeof localStorage === 'undefined'
      ? null
      : localStorage.getItem(ACTIVE_CV_CHAT_CONVERSATION_STORAGE_KEY);
    if (storedId) this.loadConversation(storedId);
  }

  search(): void {
    const query = this.searchQuery.trim();
    if (query.length < 2 || this.searchBusy) return;
    this.searchBusy = true;
    this.error = '';
    this.chat.searchCandidates(query).pipe(finalize(() => this.searchBusy = false)).subscribe({
      next: (response) => this.searchResults = response.candidates,
      error: (error) => this.error = this.apiError(error, 'Candidate search could not complete.'),
    });
  }

  discoverNaturally(): void {
    const request = this.naturalRequest.trim();
    if (request.length < 2 || request.length > 2000 || this.naturalBusy) return;
    this.naturalBusy = true;
    this.error = '';
    this.naturalResults = [];
    this.pendingQuestion = '';
    // A new natural-language request starts a new proposed scope. Never carry unconfirmed cards from an older request
    // into the next confirmation, because the recruiter could otherwise approve candidates they did not just ask for.
    this.selectedCandidates = [];
    this.discoveryTimeline = [...this.discoveryTimeline, { role: 'USER', content: request }];
    this.naturalRequest = '';
    if (this.discoveryMode === 'ORGANIZATION') {
      this.chat.discoverOrganizationCandidates(request)
        .pipe(finalize(() => this.naturalBusy = false))
        .subscribe({
          next: (response) => this.applyOrganizationDiscovery(request, response),
          error: (error: HttpErrorResponse) => {
            this.naturalRequest = request;
            this.error = this.apiError(error, 'Candidate discovery could not complete.');
          },
        });
      return;
    }
    this.chat.discoverWithTools(request)
      .pipe(finalize(() => this.naturalBusy = false))
      .subscribe({
        next: (response) => this.applyNamedDiscovery(request, response),
        error: (error: HttpErrorResponse) => {
          this.naturalRequest = request;
          this.error = this.apiError(error, 'Candidate discovery could not complete.');
        },
      });
  }

  addCandidate(candidate: CvChatCandidateOption): void {
    if (this.selectedCandidates.some((value) => value.id === candidate.id) || this.selectedCandidates.length >= 5) {
      return;
    }
    this.selectedCandidates = [...this.selectedCandidates, candidate];
  }

  removeCandidate(candidateId: number): void {
    this.selectedCandidates = this.selectedCandidates.filter((candidate) => candidate.id !== candidateId);
  }

  isSelected(candidateId: number): boolean {
    return this.selectedCandidates.some((candidate) => candidate.id === candidateId);
  }

  createConversation(): void {
    if (!this.selectedCandidates.length || this.pageBusy) return;
    this.pageBusy = true;
    this.error = '';
    this.chat.createConversation(this.selectedCandidates.map((candidate) => candidate.id), this.selectedJobId)
      .pipe(finalize(() => this.pageBusy = false))
      .subscribe({
        next: (conversation) => {
          this.conversation = conversation;
          this.timeline = [];
          this.persistConversationId(conversation.id);
          this.upsertConversation(conversation);
          const confirmedQuestion = this.pendingQuestion;
          this.pendingQuestion = '';
          this.discoveryTimeline = [];
          this.naturalResults = [];
          if (confirmedQuestion) {
            this.message = confirmedQuestion;
            this.send();
          }
        },
        error: (error) => this.error = this.apiError(error, 'Conversation could not be created.'),
      });
  }

  send(): void {
    const content = this.message.trim();
    if (!this.conversation || !content || content.length > 2000 || this.sendBusy) return;
    const conversationId = this.conversation.id;
    this.sendBusy = true;
    this.error = '';
    this.timeline = [...this.timeline, { role: 'USER', content }];
    this.message = '';
    this.chat.askConversation(conversationId, content).pipe(finalize(() => this.sendBusy = false)).subscribe({
      next: (turn) => {
        if (this.conversation?.id !== conversationId) return;
        this.conversation = {
          ...this.conversation,
          version: turn.version,
          candidates: turn.candidates,
          job: turn.job,
          expiresAt: turn.expiresAt,
          updatedAt: new Date().toISOString(),
        };
        this.timeline = [...this.timeline, {
          role: 'ASSISTANT', content: turn.answer, citations: turn.citations,
        }];
        this.upsertConversation(this.conversation);
      },
      error: (error) => {
        this.timeline = this.timeline.slice(0, -1);
        this.message = content;
        this.error = this.apiError(error, 'Message could not be sent.');
      },
    });
  }

  deleteConversation(): void {
    if (!this.conversation || this.pageBusy) return;
    const id = this.conversation.id;
    this.pageBusy = true;
    this.chat.deleteConversation(id).pipe(finalize(() => this.pageBusy = false)).subscribe({
      next: () => {
        this.recentConversations = this.recentConversations.filter((value) => value.id !== id);
        this.resetLocalState();
      },
      error: (error) => this.error = this.apiError(error, 'Conversation could not be deleted.'),
    });
  }

  beginScopeEdit(): void {
    if (!this.conversation || this.pageBusy || this.sendBusy) return;
    this.selectedCandidates = [...this.conversation.candidates];
    this.selectedJobId = this.conversation.job?.id ?? null;
    this.searchQuery = '';
    this.searchResults = [];
    this.error = '';
    this.scopeEditing = true;
  }

  cancelScopeEdit(): void {
    if (!this.conversation || this.pageBusy) return;
    this.selectedCandidates = [...this.conversation.candidates];
    this.selectedJobId = this.conversation.job?.id ?? null;
    this.searchQuery = '';
    this.searchResults = [];
    this.scopeEditing = false;
  }

  saveScope(): void {
    if (!this.conversation || !this.scopeEditing || !this.selectedCandidates.length
        || !this.scopeHasChanges() || this.pageBusy) return;
    const conversationId = this.conversation.id;
    this.pageBusy = true;
    this.error = '';
    this.chat.replaceConversationScope(
      conversationId,
      this.conversation.version,
      this.selectedCandidates.map((candidate) => candidate.id),
      this.selectedJobId,
    ).pipe(finalize(() => this.pageBusy = false)).subscribe({
      next: (conversation) => {
        if (this.conversation?.id !== conversationId) return;
        this.conversation = conversation;
        // The backend clears visible history and bounded model memory atomically with every scope replacement.
        // Mirror that security boundary immediately so stale answers never remain under the newly displayed scope.
        this.timeline = [];
        this.selectedCandidates = [...conversation.candidates];
        this.selectedJobId = conversation.job?.id ?? null;
        this.scopeEditing = false;
        this.upsertConversation(conversation);
      },
      error: (error) => this.error = this.apiError(error, 'Conversation scope could not be updated.'),
    });
  }

  scopeHasChanges(): boolean {
    if (!this.conversation) return false;
    const persistedIds = this.conversation.candidates.map((candidate) => candidate.id);
    const editedIds = this.selectedCandidates.map((candidate) => candidate.id);
    return (this.conversation.job?.id ?? null) !== this.selectedJobId
      || persistedIds.length !== editedIds.length
      || persistedIds.some((candidateId, index) => candidateId !== editedIds[index]);
  }

  startAnother(): void {
    this.resetLocalState();
  }

  resumeConversation(id: string): void {
    if (!this.pageBusy) this.loadConversation(id);
  }

  citationFragment(citation: CvChatCitation): string {
    return citation.documentId === null ? 'candidate-details-title' : `cv-document-${citation.documentId}`;
  }

  private loadConversation(id: string): void {
    this.pageBusy = true;
    forkJoin({ conversation: this.chat.getConversation(id), history: this.chat.getConversationHistory(id) })
      .pipe(finalize(() => this.pageBusy = false))
      .subscribe({
        next: ({ conversation, history }) => {
          this.conversation = conversation;
          this.timeline = history.messages;
          this.selectedCandidates = conversation.candidates;
          this.selectedJobId = conversation.job?.id ?? null;
          this.persistConversationId(conversation.id);
          this.upsertConversation(conversation);
        },
        error: (error) => {
          this.resetLocalState();
          this.error = this.apiError(error, 'The previous conversation is unavailable. Start a new one.');
        },
      });
  }

  private applyNamedDiscovery(request: string, response: CvChatToolDiscoveryResponse): void {
    const candidates = response.candidateScope?.references.flatMap((reference) => reference.candidates) ?? [];
    this.applyDiscovery(request, response.kind, response.message, candidates);
  }

  private loadConversationList(): void {
    this.chat.listConversations().subscribe({
      next: (conversations) => this.recentConversations = conversations,
      error: (error) => this.error = this.apiError(error, 'Recent conversations could not be loaded.'),
    });
  }

  private upsertConversation(conversation: CvChatConversation): void {
    this.recentConversations = [conversation,
      ...this.recentConversations.filter((value) => value.id !== conversation.id)]
      .sort((left, right) => right.updatedAt.localeCompare(left.updatedAt));
  }

  private applyOrganizationDiscovery(request: string, response: CvChatOrganizationDiscoveryResponse): void {
    this.applyDiscovery(request, response.kind, response.message, response.candidates);
  }

  private applyDiscovery(request: string, kind: string, message: string,
                         candidates: CvChatCandidateOption[]): void {
    this.discoveryTimeline = [...this.discoveryTimeline, { role: 'ASSISTANT', content: message }];
    if (kind !== 'CANDIDATE_CONFIRMATION_REQUIRED') return;
    this.naturalResults = [...new Map(candidates.map((candidate) => [candidate.id, candidate])).values()];
    this.pendingQuestion = request;
  }

  private resetLocalState(): void {
    this.conversation = null;
    this.timeline = [];
    this.discoveryTimeline = [];
    this.selectedCandidates = [];
    this.selectedJobId = null;
    this.searchResults = [];
    this.naturalResults = [];
    this.pendingQuestion = '';
    this.message = '';
    this.scopeEditing = false;
    if (typeof localStorage !== 'undefined') {
      localStorage.removeItem(ACTIVE_CV_CHAT_CONVERSATION_STORAGE_KEY);
    }
  }

  private persistConversationId(id: string): void {
    if (typeof localStorage !== 'undefined') {
      localStorage.setItem(ACTIVE_CV_CHAT_CONVERSATION_STORAGE_KEY, id);
    }
  }

  private apiError(error: HttpErrorResponse, fallback: string): string {
    return (error.error as ApiError | null)?.message || error.message || fallback;
  }
}
