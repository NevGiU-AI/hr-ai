package com.nevgiu.hrai.chat.memory;

import com.nevgiu.hrai.candidate.Candidate;
import com.nevgiu.hrai.candidate.CandidateRepository;
import com.nevgiu.hrai.chat.CvChatException;
import com.nevgiu.hrai.job.Job;
import com.nevgiu.hrai.job.JobRepository;
import com.nevgiu.hrai.security.AppUserPrincipal;
import org.springframework.ai.chat.memory.ChatMemory;
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

    @Transactional
    CvChatConversationResponse replaceScope(String id, CvChatConversationRequests.ReplaceScope request,
                                            AppUserPrincipal actor) {
        var conversation = ownedActive(id, actor);
        if (conversation.getVersion() != request.version()) {
            throw new CvChatException(HttpStatus.CONFLICT, "Conversation was modified; reload and try again");
        }
        var authorizedCandidates = authorizeScope(request.candidateIds(), actor.organizationId());
        Job authorizedJob = authorizeJob(request.jobId(), actor.organizationId());
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
}
