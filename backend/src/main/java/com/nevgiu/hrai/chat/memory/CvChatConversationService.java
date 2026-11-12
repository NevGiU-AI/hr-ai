package com.nevgiu.hrai.chat.memory;

import com.nevgiu.hrai.candidate.Candidate;
import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.chat.CvChatException;
import com.nevgiu.hrai.job.Job;
import com.nevgiu.hrai.job.JobRepository;
import com.nevgiu.hrai.security.AppUserPrincipal;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
class CvChatConversationService {
    private final CvChatConversationRepository conversations;
    private final CvChatConversationCandidateRepository scopeCandidates;
    private final CandidateRepository candidates;
    private final JobRepository jobs;
    private final ChatMemory memory;
    private final CvChatConversationProperties properties;

    CvChatConversationService(CvChatConversationRepository conversations,
                              CvChatConversationCandidateRepository scopeCandidates,
                              CandidateRepository candidates, JobRepository jobs, ChatMemory memory,
                              CvChatConversationProperties properties) {
        this.conversations = conversations;
        this.scopeCandidates = scopeCandidates;
        this.candidates = candidates;
        this.jobs = jobs;
        this.memory = memory;
        this.properties = properties;
    }

    @Transactional
    CvChatConversationResponse create(CvChatConversationRequests.Create request, AppUserPrincipal actor) {
        var authorizedCandidates = authorizeScope(request.candidateIds(), actor.organizationId());
        Job authorizedJob = authorizeJob(request.jobId(), actor.organizationId());
        Instant now = Instant.now();
        var conversation = conversations.save(new CvChatConversation(
                UUID.randomUUID().toString(), actor.organizationId(), actor.id(),
                authorizedJob == null ? null : authorizedJob.getId(), now, now.plus(properties.ttl())));
        saveScope(conversation, authorizedCandidates);
        return response(conversation, authorizedCandidates, authorizedJob);
    }

    @Transactional(readOnly = true)
    CvChatConversationResponse get(String id, AppUserPrincipal actor) {
        var conversation = ownedActive(id, actor);
        return response(conversation, loadCandidates(conversation), loadJob(conversation));
    }

    /**
     * Reauthorizes the owner, tenant, candidate IDs, and optional job before any persisted history is read.
     * The immutable snapshot lets provider work run outside a database transaction.
     */
    @Transactional(readOnly = true)
    ExecutionScope executionScope(String id, AppUserPrincipal actor) {
        var conversation = ownedActive(id, actor);
        var selected = loadCandidates(conversation);
        var job = loadJob(conversation);
        return new ExecutionScope(conversation.getId(), conversation.getVersion(), conversation.getOrganizationId(),
                selected.stream().map(Candidate::getId).toList(), job == null ? null : job.getId());
    }

    List<Message> history(String id) {
        return List.copyOf(memory.get(id));
    }

    /**
     * Persists a completed turn only if the locked conversation still has the scope version used for generation.
     * A concurrent scope edit therefore yields 409 instead of attaching an answer to stale authorization state.
     */
    @Transactional
    CvChatConversationResponse appendTurn(String id, long expectedVersion, String question, String answer,
                                          AppUserPrincipal actor) {
        var conversation = lockedOwnedActive(id, actor);
        if (conversation.getVersion() != expectedVersion) {
            throw new CvChatException(HttpStatus.CONFLICT, "Conversation scope changed; retry the question");
        }
        memory.add(id, List.of(new UserMessage(question), new AssistantMessage(answer)));
        Instant now = Instant.now();
        conversation.touch(now, now.plus(properties.ttl()));
        conversations.flush();
        return response(conversation, loadCandidates(conversation), loadJob(conversation));
    }

    @Transactional
    CvChatConversationResponse replaceScope(String id, CvChatConversationRequests.ReplaceScope request,
                                            AppUserPrincipal actor) {
        var conversation = ownedActive(id, actor);
        if (conversation.getVersion() != request.version()) {
            throw new CvChatException(HttpStatus.CONFLICT, "Conversation was modified; reload and try again");
        }
        var authorizedCandidates = authorizeScope(request.candidateIds(), actor.organizationId());
        Job authorizedJob = authorizeJob(request.jobId(), actor.organizationId());
        // History belongs to the exact confirmed scope. Clear it before replacing candidates/job so content from an
        // earlier scope can never influence answers after the visible authorization boundary changes.
        memory.clear(id);
        scopeCandidates.deleteAllByIdConversationId(id);
        saveScope(conversation, authorizedCandidates);
        Instant now = Instant.now();
        conversation.replaceJob(authorizedJob == null ? null : authorizedJob.getId(),
                now, now.plus(properties.ttl()));
        conversations.flush();
        return response(conversation, authorizedCandidates, authorizedJob);
    }

    @Transactional
    void delete(String id, AppUserPrincipal actor) {
        var conversation = ownedActive(id, actor);
        // Clear through Spring AI first; the database cascade remains a defense if cleanup is interrupted.
        memory.clear(id);
        conversations.delete(conversation);
    }

    /** Runs each scheduled deletion independently so one failure cannot roll back the whole batch. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void expire(String id, Instant now) {
        conversations.findLockedById(id).filter(value -> !value.getExpiresAt().isAfter(now)).ifPresent(value -> {
            memory.clear(id);
            conversations.delete(value);
        });
    }

    private CvChatConversation ownedActive(String id, AppUserPrincipal actor) {
        return conversations.findByIdAndOrganizationIdAndOwnerUserIdAndExpiresAtAfter(
                        id, actor.organizationId(), actor.id(), Instant.now())
                // Use one indistinguishable response for missing, expired, cross-tenant, and other-user conversations.
                .orElseThrow(() -> new CvChatException(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    private CvChatConversation lockedOwnedActive(String id, AppUserPrincipal actor) {
        CvChatConversation conversation = conversations.findLockedById(id)
                .orElseThrow(() -> new CvChatException(HttpStatus.NOT_FOUND, "Conversation not found"));
        Instant now = Instant.now();
        if (!conversation.getOrganizationId().equals(actor.organizationId())
                || !conversation.getOwnerUserId().equals(actor.id())
                || !conversation.getExpiresAt().isAfter(now)) {
            throw new CvChatException(HttpStatus.NOT_FOUND, "Conversation not found");
        }
        return conversation;
    }

    private List<Candidate> authorizeScope(List<Long> ids, String organizationId) {
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new CvChatException(HttpStatus.BAD_REQUEST, "Candidate IDs must be distinct");
        }
        return ids.stream().map(id -> candidates.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new CvChatException(HttpStatus.NOT_FOUND, "Candidate not found"))).toList();
    }

    private Job authorizeJob(Long jobId, String organizationId) {
        return jobId == null ? null : jobs.findByIdAndOrganizationId(jobId, organizationId)
                .orElseThrow(() -> new CvChatException(HttpStatus.NOT_FOUND, "Job not found"));
    }

    private void saveScope(CvChatConversation conversation, List<Candidate> authorizedCandidates) {
        for (int index = 0; index < authorizedCandidates.size(); index++) {
            scopeCandidates.save(new CvChatConversationCandidate(conversation.getId(),
                    authorizedCandidates.get(index).getId(), conversation.getOrganizationId(), index));
        }
    }

    private List<Candidate> loadCandidates(CvChatConversation conversation) {
        return scopeCandidates.findAllByIdConversationIdOrderByDisplayOrder(conversation.getId()).stream()
                .map(value -> candidates.findByIdAndOrganizationId(value.getCandidateId(), conversation.getOrganizationId())
                        .orElseThrow(() -> new CvChatException(HttpStatus.NOT_FOUND, "Candidate not found")))
                .toList();
    }

    private Job loadJob(CvChatConversation conversation) {
        return authorizeJob(conversation.getConfirmedJobId(), conversation.getOrganizationId());
    }

    private CvChatConversationResponse response(CvChatConversation conversation, List<Candidate> selected, Job job) {
        var cards = selected.stream().map(candidate -> new CvChatConversationResponse.CandidateCard(
                candidate.getId(), candidate.getName(), candidate.getCurrentTitle())).toList();
        var jobCard = job == null ? null : new CvChatConversationResponse.JobCard(
                job.getId(), job.getTitle(), job.getLevel());
        return new CvChatConversationResponse(conversation.getId(), conversation.getVersion(), cards, jobCard,
                conversation.getCreatedAt(), conversation.getUpdatedAt(), conversation.getExpiresAt());
    }

    record ExecutionScope(String conversationId, long version, String organizationId,
                          List<Long> candidateIds, Long jobId) {
    }
}
