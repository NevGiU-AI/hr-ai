package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.job.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CvChatService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CvChatService.class);
    private static final String INSUFFICIENT_EVIDENCE_MESSAGE =
            "The available CV evidence does not provide enough information to answer this question.";
    private static final String OUT_OF_SCOPE_MESSAGE =
            "I can only answer job-relevant questions supported by the authorized CV evidence. "
                    + "I cannot provide or infer protected or sensitive personal characteristics.";

    private final CvChatEvidenceService evidenceService;
    private final CvChatQuestionPolicy questionPolicy;
    private final CvChatModel model;
    private final JobRepository jobs;

    public CvChatService(CvChatEvidenceService evidenceService, CvChatQuestionPolicy questionPolicy,
                         CvChatModel model, JobRepository jobs) {
        this.evidenceService = evidenceService;
        this.questionPolicy = questionPolicy;
        this.model = model;
        this.jobs = jobs;
    }

    /**
     * Builds a grounded answer without keeping the evidence-loading transaction open during the provider call.
     * The returned citation objects are reconstructed from authorized server-side evidence, never model-provided text.
     */
    public CvChatResponse ask(Long candidateId, String question, String organizationId) {
        return ask(candidateId, question, organizationId, List.of());
    }

    /** Uses bounded prior turns only as model context; authorization and retrieval use the current explicit scope. */
    public CvChatResponse ask(Long candidateId, String question, String organizationId, List<Message> history) {
        return ask(candidateId, question, null, organizationId, history);
    }

    /** Reauthorizes optional persisted job scope and grounds one candidate against its bounded requirements. */
    public CvChatResponse ask(Long candidateId, String question, Long requestedJobId, String organizationId,
                              List<Message> history) {
        String normalizedQuestion = question.trim();
        String contextualQuestion = CvChatFollowUpContext.contextualQuestion(normalizedQuestion, history);
        // Preserve generic missing/cross-tenant 404 behavior before any model call, without loading CV content.
        evidenceService.authorizeCandidate(candidateId, organizationId);
        CvChatComparisonJobContext jobContext = authorizeJob(requestedJobId, organizationId);
        CvChatQuestionPolicyResult policyResult;
        try {
            // Classify before vector search so unrelated or sensitive questions cannot trigger candidate-data
            // retrieval. The classifier sees only the question; authorization remains enforced in application code.
            policyResult = questionPolicy.classify(contextualQuestion);
        } catch (RuntimeException policyFailure) {
            // Do not log the question or provider detail: either may contain private or adversarial content.
            LOGGER.warn("CV chat question policy failed for candidateId={} organizationId={} failureType={}",
                    candidateId, organizationId, policyFailure.getClass().getSimpleName());
            throw new CvChatException(HttpStatus.BAD_GATEWAY, "CV chat question policy is unavailable");
        }
        if (policyResult != CvChatQuestionPolicyResult.JOB_RELEVANT) {
            // Record only the decision stage and label. The untrusted question is intentionally excluded from logs.
            LOGGER.info("CV chat question rejected for candidateId={} organizationId={} stage={} policyResult={}",
                    candidateId, organizationId, "PRE_RETRIEVAL_POLICY", policyResult);
            return new CvChatResponse(CvChatAnswerKind.OUT_OF_SCOPE, OUT_OF_SCOPE_MESSAGE, List.of());
        }
        // Evidence is loaded in a short read transaction; no JDBC connection is held while OpenAI responds.
        String retrievalIntent = jobContext == null
                ? contextualQuestion
                : contextualQuestion + "\n\nAUTHORIZED APPROVED JOB REQUIREMENTS:\n" + jobContext.promptText();
        List<CvChatEvidence> evidence = evidenceService.find(candidateId, retrievalIntent, organizationId);
        // An empty semantic result is a normal evidence outcome, not a provider failure. Avoid sending unrelated
        // fallback text to the model and return wording controlled entirely by the backend.
        if (evidence.isEmpty()) {
            return new CvChatResponse(CvChatAnswerKind.INSUFFICIENT_EVIDENCE, INSUFFICIENT_EVIDENCE_MESSAGE, List.of());
        }
        String answer;
        try {
            // Preserve the established non-conversational provider contract when no history exists. This also keeps
            // first-turn behavior identical while the follow-up path opts into the history-aware overload.
            if (jobContext != null) {
                answer = model.answer(normalizedQuestion, evidence, jobContext, history);
            } else {
                // Keep both established no-job contracts intact: first turns use the original overload and follow-up
                // turns use only bounded history. The job-aware overload is reserved for an explicitly confirmed job.
                answer = history.isEmpty()
                        ? model.answer(normalizedQuestion, evidence)
                        : model.answer(normalizedQuestion, evidence, history);
            }
        } catch (RuntimeException providerFailure) {
            // Keep provider internals and potentially sensitive prompt fragments out of the API response.
            LOGGER.warn("CV chat provider call failed for candidateId={} organizationId={} failureType={}",
                    candidateId, organizationId, providerFailure.getClass().getSimpleName());
            throw new CvChatException(HttpStatus.BAD_GATEWAY, "CV chat provider is unavailable");
        }
        if (answer == null || answer.isBlank()) {
            throw new CvChatException(HttpStatus.BAD_GATEWAY, "CV chat provider returned an empty response");
        }
        String normalizedAnswer = answer.trim();
        // Only exact control results are trusted. Java supplies the displayed wording so provider prose cannot smuggle
        // uncited claims into a refusal or insufficient-evidence response.
        if (CvChatModel.INSUFFICIENT_EVIDENCE.equals(normalizedAnswer)) {
            return new CvChatResponse(CvChatAnswerKind.INSUFFICIENT_EVIDENCE, INSUFFICIENT_EVIDENCE_MESSAGE, List.of());
        }
        if (CvChatModel.OUT_OF_SCOPE.equals(normalizedAnswer)) {
            // This second policy boundary is defense in depth after authorized evidence retrieval.
            LOGGER.info("CV chat question rejected for candidateId={} organizationId={} stage={}",
                    candidateId, organizationId, "ANSWER_MODEL_POLICY");
            return new CvChatResponse(CvChatAnswerKind.OUT_OF_SCOPE, OUT_OF_SCOPE_MESSAGE, List.of());
        }
        List<CvChatResponse.Citation> citations = CvChatCitations.fromAnswer(normalizedAnswer, evidence);
        if (citations.isEmpty()) {
            // Never expose uncited provider prose. A model may paraphrase the insufficient-evidence control token or
            // produce another unsupported statement even at temperature zero. Treat every citationless result as the
            // same safe application-owned outcome, while unsupported citation-shaped references still fail above.
            LOGGER.info("CV chat answer rejected for candidateId={} organizationId={} stage={} citedReferenceCount={}",
                    candidateId, organizationId, "CITATION_COVERAGE", 0);
            return new CvChatResponse(CvChatAnswerKind.INSUFFICIENT_EVIDENCE, INSUFFICIENT_EVIDENCE_MESSAGE, List.of());
        }
        return new CvChatResponse(CvChatAnswerKind.ANSWER, normalizedAnswer, citations);
    }

    private CvChatComparisonJobContext authorizeJob(Long requestedJobId, String organizationId) {
        if (requestedJobId == null) {
            return null;
        }
        // The persisted ID is authorization scope, not trusted job content. Reload it for every turn and keep
        // missing and cross-tenant IDs indistinguishable, matching the comparison path.
        return jobs.findByIdAndOrganizationId(requestedJobId, organizationId)
                .map(CvChatComparisonJobContext::from)
                .orElseThrow(() -> new CvChatException(HttpStatus.NOT_FOUND, "Approved job not found"));
    }
}
