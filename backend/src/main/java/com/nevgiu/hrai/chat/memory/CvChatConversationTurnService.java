package com.nevgiu.hrai.chat.memory;

import com.nevgiu.hrai.chat.CvChatAnswerKind;
import com.nevgiu.hrai.chat.CvChatComparisonResponse;
import com.nevgiu.hrai.chat.CvChatComparisonService;
import com.nevgiu.hrai.chat.CvChatResponse;
import com.nevgiu.hrai.chat.CvChatService;
import com.nevgiu.hrai.security.AppUserPrincipal;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Connects durable conversation scope to grounded chat without making model memory an authorization source.
 *
 * <p>No transaction spans provider I/O. Scope is authorized first, bounded history is then read, and the completed
 * turn is persisted only after a locked version check proves that the confirmed scope did not change meanwhile.</p>
 */
@Service
class CvChatConversationTurnService {
    private final CvChatConversationService conversations;
    private final CvChatService singleCandidateChat;
    private final CvChatComparisonService comparisonChat;
    private final CvChatDocumentToolService documentTools;

    CvChatConversationTurnService(CvChatConversationService conversations, CvChatService singleCandidateChat,
                                  CvChatComparisonService comparisonChat, CvChatDocumentToolService documentTools) {
        this.conversations = conversations;
        this.singleCandidateChat = singleCandidateChat;
        this.comparisonChat = comparisonChat;
        this.documentTools = documentTools;
    }

    CvChatConversationTurnResponse ask(String conversationId, String message, AppUserPrincipal actor) {
        String normalizedMessage = message.trim();
        var scope = conversations.executionScope(conversationId, actor);
        var history = conversations.history(conversationId);

        CvChatAnswerKind kind;
        String answer;
        String responseLanguage = null;
        List<CvChatResponse.Citation> citations;
        // Document processing state is application data, not evidence extracted from the CV. Route it before the
        // semantic RAG services so questions about OCR, import, reprocessing, or warnings do not search CV chunks and
        // cannot produce fabricated CV citations. Optional.empty() means this is a normal professional question and
        // preserves the existing single-candidate/comparison paths unchanged.
        var operationalAnswer = documentTools.answer(normalizedMessage, scope.candidates());
        if (operationalAnswer.isPresent()) {
            kind = CvChatAnswerKind.TOOL_STATUS;
            answer = operationalAnswer.get();
            // CV citations prove claims from extracted candidate evidence. An operational status answer instead comes
            // from tenant-authorized database fields, so attaching a CV citation would misrepresent its provenance.
            citations = List.of();
        } else if (scope.candidateIds().size() == 1) {
            CvChatResponse response = singleCandidateChat.ask(scope.candidateIds().getFirst(), normalizedMessage,
                    scope.jobId(), scope.organizationId(), history);
            kind = response.kind();
            answer = response.answer();
            citations = response.citations();
            responseLanguage = response.responseLanguage();
        } else {
            CvChatComparisonResponse response = comparisonChat.compare(scope.candidateIds(), normalizedMessage,
                    scope.jobId(), scope.organizationId(), history);
            kind = response.kind();
            answer = response.answer();
            citations = response.citations();
            responseLanguage = response.responseLanguage();
        }

        // Persist operational and grounded turns through the same optimistic version check. A concurrent candidate or
        // job-scope edit therefore cannot attach either kind of answer to an obsolete authorization snapshot.
        CvChatConversationResponse updated = conversations.appendTurn(
                conversationId, scope.version(), normalizedMessage, answer, citations, kind, actor);
        return new CvChatConversationTurnResponse(updated.id(), updated.version(), kind, answer, updated.candidates(),
                updated.job(), citations, updated.expiresAt(), responseLanguage);
    }
}
