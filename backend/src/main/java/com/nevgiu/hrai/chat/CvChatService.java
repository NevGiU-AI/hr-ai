package com.nevgiu.hrai.chat;

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

    public CvChatService(CvChatEvidenceService evidenceService, CvChatQuestionPolicy questionPolicy,
                         CvChatModel model) {
        this.evidenceService = evidenceService;
        this.questionPolicy = questionPolicy;
        this.model = model;
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
        String normalizedQuestion = question.trim();
        // Preserve generic missing/cross-tenant 404 behavior before any model call, without loading CV content.
        evidenceService.authorizeCandidate(candidateId, organizationId);
        CvChatQuestionPolicyResult policyResult;
        try {
            // Classify before vector search so unrelated or sensitive questions cannot trigger candidate-data
            // retrieval. The classifier sees only the question; authorization remains enforced in application code.
            policyResult = questionPolicy.classify(normalizedQuestion);
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
            return new CvChatResponse(OUT_OF_SCOPE_MESSAGE, List.of());
        }
        // Evidence is loaded in a short read transaction; no JDBC connection is held while OpenAI responds.
        List<CvChatEvidence> evidence = evidenceService.find(candidateId, normalizedQuestion, organizationId);
        // An empty semantic result is a normal evidence outcome, not a provider failure. Avoid sending unrelated
        // fallback text to the model and return wording controlled entirely by the backend.
        if (evidence.isEmpty()) {
            return new CvChatResponse(INSUFFICIENT_EVIDENCE_MESSAGE, List.of());
        }
        String answer;
        try {
            // Preserve the established non-conversational provider contract when no history exists. This also keeps
            // first-turn behavior identical while the follow-up path opts into the history-aware overload.
            answer = history.isEmpty()
                    ? model.answer(normalizedQuestion, evidence)
                    : model.answer(normalizedQuestion, evidence, history);
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
            return new CvChatResponse(INSUFFICIENT_EVIDENCE_MESSAGE, List.of());
        }
        if (CvChatModel.OUT_OF_SCOPE.equals(normalizedAnswer)) {
            // This second policy boundary is defense in depth after authorized evidence retrieval.
            LOGGER.info("CV chat question rejected for candidateId={} organizationId={} stage={}",
                    candidateId, organizationId, "ANSWER_MODEL_POLICY");
            return new CvChatResponse(OUT_OF_SCOPE_MESSAGE, List.of());
        }
        return new CvChatResponse(answer, CvChatCitations.fromAnswer(answer, evidence));
    }
}
