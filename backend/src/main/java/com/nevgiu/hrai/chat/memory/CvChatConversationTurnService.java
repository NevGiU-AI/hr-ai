package com.nevgiu.hrai.chat.memory;

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

    CvChatConversationTurnService(CvChatConversationService conversations, CvChatService singleCandidateChat,
                                  CvChatComparisonService comparisonChat) {
        this.conversations = conversations;
        this.singleCandidateChat = singleCandidateChat;
        this.comparisonChat = comparisonChat;
    }

    CvChatConversationTurnResponse ask(String conversationId, String message, AppUserPrincipal actor) {
        String normalizedMessage = message.trim();
        var scope = conversations.executionScope(conversationId, actor);
        var history = conversations.history(conversationId);

        String answer;
        List<CvChatResponse.Citation> citations;
        if (scope.candidateIds().size() == 1) {
            CvChatResponse response = singleCandidateChat.ask(scope.candidateIds().getFirst(), normalizedMessage,
                    scope.organizationId(), history);
            answer = response.answer();
            citations = response.citations();
        } else {
            CvChatComparisonResponse response = comparisonChat.compare(scope.candidateIds(), normalizedMessage,
                    scope.jobId(), scope.organizationId(), history);
            answer = response.answer();
            citations = response.citations();
        }

        CvChatConversationResponse updated = conversations.appendTurn(
                conversationId, scope.version(), normalizedMessage, answer, citations, actor);
        return new CvChatConversationTurnResponse(updated.id(), updated.version(), answer, updated.candidates(),
                updated.job(), citations, updated.expiresAt());
    }
}
