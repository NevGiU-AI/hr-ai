package com.nevgiu.hrai.chat;

import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reconstructs API citations exclusively from the evidence authorized by application code. */
final class CvChatCitations {
    // Only backend-issued document and OCR-page references are recognized; arbitrary model links are never trusted.
    private static final Pattern CITATION = Pattern.compile("\\[(CV-(?:\\d+(?:-P\\d+)?|CANDIDATE-\\d+))\\]");

    private CvChatCitations() { }

    static List<CvChatResponse.Citation> fromAnswer(String answer, List<CvChatEvidence> evidence) {
        // Linked maps preserve evidence/citation order while collapsing a reference repeated throughout the answer.
        Map<String, CvChatEvidence> allowed = new LinkedHashMap<>();
        evidence.forEach(item -> allowed.put(item.reference(), item));
        Map<String, CvChatEvidence> cited = new LinkedHashMap<>();
        Matcher matcher = CITATION.matcher(answer);
        while (matcher.find()) {
            CvChatEvidence item = allowed.get(matcher.group(1));
            if (item == null) {
                throw new CvChatException(HttpStatus.BAD_GATEWAY,
                        "CV chat provider returned an unsupported citation");
            }
            cited.putIfAbsent(item.reference(), item);
        }
        // No citation is an unusable grounding result, but it is safe to downgrade to application-owned
        // insufficient evidence. Callers discard the complete provider answer. A citation-shaped reference that is
        // not in authorized evidence still throws above because it may be fabricated or cross-scope.
        return cited.values().stream().map(item -> new CvChatResponse.Citation(item.reference(),
                item.candidateId(), item.documentId(), item.pageNumber(), item.excerpt())).toList();
    }
}
