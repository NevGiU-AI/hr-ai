package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.candidate.CandidateIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class CvChatComparisonService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CvChatComparisonService.class);
    private static final String INSUFFICIENT_EVIDENCE_MESSAGE =
            "The available CV evidence does not provide enough information to compare every selected candidate.";
    private static final String OUT_OF_SCOPE_MESSAGE =
            "I can only compare selected candidates using job-relevant, authorized CV evidence. "
                    + "I cannot provide or infer protected or sensitive personal characteristics.";
    private static final int MINIMUM_CANDIDATES = 2;
    private static final int MAXIMUM_CANDIDATES = 5;

    private final CvChatEvidenceService evidenceService;
    private final CvChatQuestionPolicy questionPolicy;
    private final CvChatComparisonModel model;

    public CvChatComparisonService(CvChatEvidenceService evidenceService, CvChatQuestionPolicy questionPolicy,
                                   CvChatComparisonModel model) {
        this.evidenceService = evidenceService;
        this.questionPolicy = questionPolicy;
        this.model = model;
    }

    public CvChatComparisonResponse compare(List<Long> requestedCandidateIds, String question, String organizationId) {
        List<Long> candidateIds = distinctIds(requestedCandidateIds);
        String normalizedQuestion = question.trim();

        // Authorize the entire set before policy or evidence work. One missing/cross-tenant ID fails the whole request
        // with the same generic response, preventing partial disclosure about which IDs belong to this organization.
        List<CandidateIdentity> candidates = candidateIds.stream()
                .map(id -> evidenceService.authorizeCandidate(id, organizationId))
                .toList();
        CvChatQuestionPolicyResult policyResult;
        try {
            policyResult = questionPolicy.classify(normalizedQuestion);
        } catch (RuntimeException failure) {
            LOGGER.warn("CV comparison question policy failed for organizationId={} candidateCount={} failureType={}",
                    organizationId, candidateIds.size(), failure.getClass().getSimpleName());
            throw new CvChatException(HttpStatus.BAD_GATEWAY, "CV chat question policy is unavailable");
        }
        if (policyResult != CvChatQuestionPolicyResult.JOB_RELEVANT) {
            LOGGER.info("CV comparison rejected for organizationId={} candidateCount={} stage={} policyResult={}",
                    organizationId, candidateIds.size(), "PRE_RETRIEVAL_POLICY", policyResult);
            return response(OUT_OF_SCOPE_MESSAGE, candidates, List.of());
        }

        // Normalize once and reuse the exact same search intent for every selected candidate. Normalizing separately
        // would allow provider variation to give otherwise comparable candidates different retrieval queries.
        List<String> retrievalQueries = evidenceService.prepareRetrievalQueries(normalizedQuestion, organizationId);
        List<CvChatEvidence> evidence = new ArrayList<>();
        for (Long candidateId : candidateIds) {
            List<CvChatEvidence> candidateEvidence = evidenceService.find(
                    candidateId, retrievalQueries, organizationId);
            // A fair comparison must have relevant evidence for every selected person; never let the model convert a
            // retrieval miss into a negative claim about that candidate.
            if (candidateEvidence.isEmpty()) {
                return response(INSUFFICIENT_EVIDENCE_MESSAGE, candidates, List.of());
            }
            evidence.addAll(candidateEvidence);
        }

        String answer;
        try {
            answer = model.compare(normalizedQuestion, candidates, List.copyOf(evidence));
        } catch (RuntimeException failure) {
            LOGGER.warn("CV comparison provider failed for organizationId={} candidateCount={} failureType={}",
                    organizationId, candidateIds.size(), failure.getClass().getSimpleName());
            throw new CvChatException(HttpStatus.BAD_GATEWAY, "CV chat provider is unavailable");
        }
        if (answer == null || answer.isBlank()) {
            throw new CvChatException(HttpStatus.BAD_GATEWAY, "CV chat provider returned an empty response");
        }
        String normalizedAnswer = answer.trim();
        if (CvChatModel.OUT_OF_SCOPE.equals(normalizedAnswer)) {
            return response(OUT_OF_SCOPE_MESSAGE, candidates, List.of());
        }
        if (CvChatModel.INSUFFICIENT_EVIDENCE.equals(normalizedAnswer)) {
            return response(INSUFFICIENT_EVIDENCE_MESSAGE, candidates, List.of());
        }

        // Some providers occasionally append the protocol token on its own final line after an otherwise grounded
        // comparison. The token is an application/model control signal, not user-facing prose. Remove only that exact
        // terminal line; never rewrite an occurrence embedded in ordinary answer text.
        String displayAnswer = removeTrailingControlLine(normalizedAnswer, CvChatModel.INSUFFICIENT_EVIDENCE);
        if (displayAnswer.isBlank()) {
            return response(INSUFFICIENT_EVIDENCE_MESSAGE, candidates, List.of());
        }

        List<CvChatResponse.Citation> citations = CvChatCitations.fromAnswer(displayAnswer, evidence);
        Set<Long> citedCandidateIds = citations.stream()
                .map(CvChatResponse.Citation::candidateId)
                .collect(java.util.stream.Collectors.toSet());
        if (!citedCandidateIds.containsAll(candidateIds)) {
            // Retrieval found evidence for every candidate, but the generated comparison did not cite every person.
            // Treat that as insufficient evidence rather than exposing a partial or potentially unfair comparison.
            // The log records only counts and tenant scope—never the question, answer, or CV evidence.
            LOGGER.info("CV comparison rejected for organizationId={} candidateCount={} stage={} citedCandidateCount={}",
                    organizationId, candidateIds.size(), "CITATION_COVERAGE", citedCandidateIds.size());
            return response(INSUFFICIENT_EVIDENCE_MESSAGE, candidates, List.of());
        }
        return response(displayAnswer, candidates, citations);
    }

    private List<Long> distinctIds(List<Long> requestedCandidateIds) {
        // Bean validation protects the HTTP boundary, while this check also protects callers that invoke the service
        // directly (for example, a future messaging adapter or scheduled workflow).
        if (requestedCandidateIds == null
                || requestedCandidateIds.size() < MINIMUM_CANDIDATES
                || requestedCandidateIds.size() > MAXIMUM_CANDIDATES) {
            throw new CvChatException(HttpStatus.BAD_REQUEST, "Select between 2 and 5 candidates");
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>(requestedCandidateIds);
        if (distinct.size() != requestedCandidateIds.size()) {
            throw new CvChatException(HttpStatus.BAD_REQUEST, "Candidate IDs must be distinct");
        }
        return List.copyOf(distinct);
    }

    private CvChatComparisonResponse response(String answer, List<CandidateIdentity> candidates,
                                              List<CvChatResponse.Citation> citations) {
        List<CvChatComparisonResponse.CandidateOption> options = candidates.stream()
                .map(candidate -> new CvChatComparisonResponse.CandidateOption(
                        candidate.id(), candidate.name(), candidate.currentTitle()))
                .toList();
        return new CvChatComparisonResponse(answer, options, citations);
    }

    private String removeTrailingControlLine(String answer, String controlToken) {
        int lastLineBreak = Math.max(answer.lastIndexOf('\n'), answer.lastIndexOf('\r'));
        if (lastLineBreak < 0 || !answer.substring(lastLineBreak + 1).trim().equals(controlToken)) {
            return answer;
        }
        return answer.substring(0, lastLineBreak).trim();
    }
}
