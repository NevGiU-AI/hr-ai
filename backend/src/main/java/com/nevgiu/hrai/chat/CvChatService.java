package com.nevgiu.hrai.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CvChatService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CvChatService.class);

    // Only backend-issued document and OCR-page references are recognized; arbitrary model links are never trusted.
    private static final Pattern CITATION = Pattern.compile("\\[(CV-(?:\\d+(?:-P\\d+)?|CANDIDATE-\\d+))\\]");

    private final CvChatEvidenceService evidenceService;
    private final CvChatModel model;

    public CvChatService(CvChatEvidenceService evidenceService, CvChatModel model) {
        this.evidenceService = evidenceService;
        this.model = model;
    }

    /**
     * Builds a grounded answer without keeping the evidence-loading transaction open during the provider call.
     * The returned citation objects are reconstructed from authorized server-side evidence, never model-provided text.
     */
    public CvChatResponse ask(Long candidateId, String question, String organizationId) {
        // Evidence is loaded in a short read transaction; no JDBC connection is held while OpenAI responds.
        List<CvChatEvidence> evidence = evidenceService.find(candidateId, organizationId);
        String answer;
        try {
            answer = model.answer(question.trim(), evidence);
        } catch (RuntimeException providerFailure) {
            // Keep provider internals and potentially sensitive prompt fragments out of the API response.
            LOGGER.warn("CV chat provider call failed for candidateId={} organizationId={} failureType={}",
                    candidateId, organizationId, providerFailure.getClass().getSimpleName());
            throw new CvChatException(HttpStatus.BAD_GATEWAY, "CV chat provider is unavailable");
        }
        if (answer == null || answer.isBlank()) {
            throw new CvChatException(HttpStatus.BAD_GATEWAY, "CV chat provider returned an empty response");
        }
        return new CvChatResponse(answer, citedEvidence(answer, evidence));
    }

    private List<CvChatResponse.Citation> citedEvidence(String answer, List<CvChatEvidence> evidence) {
        // Linked maps preserve evidence/citation order while collapsing a reference repeated throughout the answer.
        Map<String, CvChatEvidence> allowed = new LinkedHashMap<>();
        evidence.forEach(item -> allowed.put(item.reference(), item));
        Map<String, CvChatEvidence> cited = new LinkedHashMap<>();
        Matcher matcher = CITATION.matcher(answer);
        while (matcher.find()) {
            CvChatEvidence item = allowed.get(matcher.group(1));
            if (item == null) {
                // A syntactically valid but unknown reference is a hallucinated citation and invalidates the answer.
                throw new CvChatException(HttpStatus.BAD_GATEWAY,
                        "CV chat provider returned an unsupported citation");
            }
            cited.putIfAbsent(item.reference(), item);
        }
        if (cited.isEmpty()) {
            throw new CvChatException(HttpStatus.BAD_GATEWAY,
                    "CV chat provider returned an answer without evidence citations");
        }
        return cited.values().stream().map(item -> new CvChatResponse.Citation(item.reference(),
                item.candidateId(), item.documentId(), item.pageNumber(), item.excerpt())).toList();
    }
}
