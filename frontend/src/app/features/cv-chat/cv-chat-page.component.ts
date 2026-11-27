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
  CvChatResponseLanguage,
  CvChatTimelineMessage,
  CvChatToolDiscoveryResponse,
} from '../cv-ingestion/models/cv-chat.models';
import { CvChatService } from '../cv-ingestion/services/cv-chat.service';
import { CvIngestionService } from '../cv-ingestion/services/cv-ingestion.service';
import { cvChatInterfaceCopy } from './cv-chat-interface-copy';
import { cvChatQuestionLanguage } from './cv-chat-question-language';

@Component({
  selector: 'app-cv-chat-page',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  templateUrl: './cv-chat-page.component.html',
  styleUrl: './cv-chat-page.component.scss',
})
export class CvChatPageComponent implements OnInit {
  readonly copy = cvChatInterfaceCopy(typeof navigator === 'undefined' ? 'en' : navigator.language);

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
  naturalResultGroups: { requestedName: string | null; candidates: CvChatCandidateOption[]; truncated: boolean }[] = [];
  naturalBusy = false;
  pendingQuestion = '';
  searchBusy = false;
  pageBusy = false;
  sendBusy = false;
  sendFeedback = '';
  sendNeedsHistoryCheck = false;
  scopeEditing = false;
  message = '';
  controlledResponseLanguage: CvChatResponseLanguage | null = null;
  error = '';
  resumeError = '';
  private retryConversationId: string | null = null;
  private readonly unavailableConversationIds = new Set<string>();

  get canRetryResume(): boolean {
    return this.retryConversationId !== null;
  }

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
    this.naturalResultGroups = [];
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
          this.naturalResultGroups = [];
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
    // The editor shows a draft scope, not the scope the server would use for a message.
    if (!this.conversation || !content || content.length > 2000 || this.sendBusy || this.scopeEditing
        || this.sendNeedsHistoryCheck) return;
    const conversationId = this.conversation.id;
    this.sendBusy = true;
    this.sendFeedback = '';
    this.error = '';
    this.timeline = [...this.timeline, { role: 'USER', content }];
    this.message = '';
    // An explicit choice wins. If the question gives no clear signal, leave the header to the browser.
    const replyLanguage = this.controlledResponseLanguage ?? cvChatQuestionLanguage(content);
    this.chat.askConversation(conversationId, content, replyLanguage)
      .pipe(finalize(() => this.sendBusy = false)).subscribe({
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
          role: 'ASSISTANT', kind: turn.kind, content: turn.answer, citations: turn.citations,
        }];
        this.upsertConversation(this.conversation);
      },
      error: (error) => {
        if (this.conversation?.id !== conversationId) return;
        this.timeline = this.timeline.slice(0, -1);
        this.message = content;
        // A transport or server error does not prove the write failed. Never replay the POST automatically.
        this.sendNeedsHistoryCheck = error.status === 0 || error.status === 408 || error.status >= 500;
        this.sendFeedback = this.sendNeedsHistoryCheck
          ? this.copy.uncertainSend
          : this.apiError(error, this.copy.sendFailed);
      },
    });
  }

  checkHistoryAfterSend(): void {
    if (!this.conversation || !this.sendNeedsHistoryCheck || this.pageBusy || this.scopeEditing) return;
    this.loadConversation(this.conversation.id, true);
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
    if (!this.conversation || this.pageBusy || this.sendBusy || this.sendNeedsHistoryCheck) return;
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
    this.resumeError = '';
    this.retryConversationId = null;
  }

  resumeConversation(id: string): void {
    if (!this.pageBusy) this.loadConversation(id);
  }

  retryResume(): void {
    if (this.retryConversationId) this.resumeConversation(this.retryConversationId);
  }

  citationFragment(citation: CvChatCitation): string {
    return citation.documentId === null ? 'candidate-details-title' : `cv-document-${citation.documentId}`;
  }

  private loadConversation(id: string, checkingSend = false): void {
    this.pageBusy = true;
    this.resumeError = '';
    this.retryConversationId = null;
    forkJoin({ conversation: this.chat.getConversation(id), history: this.chat.getConversationHistory(id) })
      .pipe(finalize(() => this.pageBusy = false))
      .subscribe({
        next: ({ conversation, history }) => {
          if (checkingSend && this.conversation?.id !== id) return;
          this.conversation = conversation;
          this.timeline = history.messages;
          this.selectedCandidates = conversation.candidates;
          this.selectedJobId = conversation.job?.id ?? null;
          this.persistConversationId(conversation.id);
          this.upsertConversation(conversation);
          if (checkingSend) {
            this.sendNeedsHistoryCheck = false;
            this.sendFeedback = this.copy.historyRefreshed;
          }
        },
        error: (error) => {
          if (checkingSend) {
            if (this.conversation?.id !== id) return;
            this.sendFeedback = error.status === 404
              ? this.copy.historyMissing
              : this.copy.historyCheckFailed;
            return;
          }
          if (error.status === 404) {
            // A missing conversation should not be restored again on refresh. Leave other drafts and IDs alone.
            if (typeof localStorage !== 'undefined'
                && localStorage.getItem(ACTIVE_CV_CHAT_CONVERSATION_STORAGE_KEY) === id) {
              localStorage.removeItem(ACTIVE_CV_CHAT_CONVERSATION_STORAGE_KEY);
            }
            this.unavailableConversationIds.add(id);
            this.recentConversations = this.recentConversations.filter((item) => item.id !== id);
            this.resumeError = this.copy.resumeMissing;
            return;
          }
          // Retry only the read requests. Do not discard the saved ID or anything already on screen.
          if (error.status === 0 || error.status === 429 || error.status >= 500) {
            this.retryConversationId = id;
            this.resumeError = this.copy.resumeTemporary;
            return;
          }
          this.resumeError = this.apiError(error, this.copy.resumeFailed);
        },
      });
  }

  private applyNamedDiscovery(request: string, response: CvChatToolDiscoveryResponse): void {
    // Keep a match under every reference it answers; selection still deduplicates by candidate ID across groups.
    this.applyDiscovery(request, response.kind, response.message, response.candidateScope?.references ?? []);
  }

  private loadConversationList(): void {
    this.chat.listConversations().subscribe({
      // The initial list and automatic resume run independently; a late list must not restore a stale entry.
      next: (conversations) => this.recentConversations = conversations.filter(
        (item) => !this.unavailableConversationIds.has(item.id)),
      error: (error) => this.error = this.apiError(error, 'Recent conversations could not be loaded.'),
    });
  }

  private upsertConversation(conversation: CvChatConversation): void {
    this.recentConversations = [conversation,
      ...this.recentConversations.filter((value) => value.id !== conversation.id)]
      .sort((left, right) => right.updatedAt.localeCompare(left.updatedAt));
  }

  private applyOrganizationDiscovery(request: string, response: CvChatOrganizationDiscoveryResponse): void {
    this.applyDiscovery(request, response.kind, response.message, [
      { requestedName: null, candidates: response.candidates, truncated: false },
    ]);
  }

  private applyDiscovery(request: string, kind: string, message: string,
                         groups: typeof this.naturalResultGroups): void {
    this.discoveryTimeline = [...this.discoveryTimeline, { role: 'ASSISTANT', content: message }];
    if (kind !== 'CANDIDATE_CONFIRMATION_REQUIRED') return;
    this.naturalResultGroups = groups.filter((group) => group.candidates.length).map((group) => ({
      ...group,
      candidates: [...new Map(group.candidates.map((candidate) => [candidate.id, candidate])).values()],
    }));
    this.pendingQuestion = request;
  }

  private resetLocalState(): void {
    this.conversation = null;
    this.timeline = [];
    this.discoveryTimeline = [];
    this.selectedCandidates = [];
    this.selectedJobId = null;
    this.searchResults = [];
    this.naturalResultGroups = [];
    this.pendingQuestion = '';
    this.message = '';
    this.sendFeedback = '';
    this.sendNeedsHistoryCheck = false;
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
