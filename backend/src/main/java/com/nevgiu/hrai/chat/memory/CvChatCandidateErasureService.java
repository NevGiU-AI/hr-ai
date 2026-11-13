package com.nevgiu.hrai.chat.memory;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

/** Removes every conversation that could retain generated personal data about a candidate being erased. */
@Service
public class CvChatCandidateErasureService {
    private final CvChatConversationCandidateRepository scopeCandidates;
    private final CvChatConversationRepository conversations;
    private final ChatMemory memory;

    CvChatCandidateErasureService(CvChatConversationCandidateRepository scopeCandidates,
                                  CvChatConversationRepository conversations, ChatMemory memory) {
        this.scopeCandidates = scopeCandidates;
        this.conversations = conversations;
        this.memory = memory;
    }

    /** Called inside the candidate-erasure transaction before the candidate row is removed. */
    public void eraseCandidateConversations(Long candidateId, String organizationId) {
        var conversationIds = scopeCandidates.findConversationIdsByCandidateIdAndOrganizationId(
                candidateId, organizationId);
        conversationIds.forEach(memory::clear);
        conversations.deleteAllById(conversationIds);
        conversations.flush();
    }
}
