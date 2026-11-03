package com.nevgiu.hrai.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

/** Interprets natural candidate references, then resolves them only through tenant-scoped deterministic queries. */
@Service
public class CvChatLanguageScopeService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CvChatLanguageScopeService.class);

    private final CvChatCandidateReferenceExtractor extractor;
    private final CvChatCandidateResolutionService resolution;

    public CvChatLanguageScopeService(CvChatCandidateReferenceExtractor extractor,
                                      CvChatCandidateResolutionService resolution) {
        this.extractor = extractor;
        this.resolution = resolution;
    }

    public CvChatLanguageScopeResponse propose(String requestedMessage, String organizationId) {
        String message = requestedMessage.strip();
        List<String> references;
        try {
            references = extractor.extract(message);
        } catch (RuntimeException failure) {
            LOGGER.warn("CV chat candidate-reference interpretation failed for organizationId={} failureType={}",
                    organizationId, failure.getClass().getSimpleName());
            throw new CvChatException(HttpStatus.BAD_GATEWAY, "Candidate reference interpretation is unavailable");
        }
        if (references.isEmpty()) {
            return new CvChatLanguageScopeResponse(message, List.of());
        }
        List<CvChatLanguageScopeResponse.Reference> resolved = references.stream()
                .map(reference -> resolve(reference, organizationId))
                .toList();
        return new CvChatLanguageScopeResponse(message, List.copyOf(resolved));
    }

    private CvChatLanguageScopeResponse.Reference resolve(String reference, String organizationId) {
        try {
            CvChatCandidateResolutionResponse exact = resolution.resolve(reference, organizationId);
            return new CvChatLanguageScopeResponse.Reference(reference,
                    exact.status() == CvChatCandidateResolutionResponse.Status.RESOLVED
                            ? CvChatLanguageScopeResponse.Status.RESOLVED
                            : CvChatLanguageScopeResponse.Status.CHOICES,
                    exact.candidates().stream().map(candidate -> new CvChatLanguageScopeResponse.CandidateOption(
                            candidate.id(), candidate.name(), candidate.currentTitle())).toList(),
                    exact.truncated());
        } catch (CvChatException notFound) {
            if (notFound.status() != HttpStatus.NOT_FOUND) {
                throw notFound;
            }
        }

        CvChatCandidateSearchResponse partial = resolution.search(reference, organizationId);
        if (partial.candidates().isEmpty()) {
            return new CvChatLanguageScopeResponse.Reference(reference,
                    CvChatLanguageScopeResponse.Status.NOT_FOUND, List.of(), false);
        }
        return new CvChatLanguageScopeResponse.Reference(reference, CvChatLanguageScopeResponse.Status.CHOICES,
                partial.candidates().stream().map(candidate -> new CvChatLanguageScopeResponse.CandidateOption(
                        candidate.id(), candidate.name(), candidate.currentTitle())).toList(),
                partial.truncated());
    }
}
