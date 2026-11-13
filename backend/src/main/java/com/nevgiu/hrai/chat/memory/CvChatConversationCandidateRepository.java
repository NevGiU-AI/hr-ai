package com.nevgiu.hrai.chat.memory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

interface CvChatConversationCandidateRepository
        extends JpaRepository<CvChatConversationCandidate, CvChatConversationCandidateId> {
    List<CvChatConversationCandidate> findAllByIdConversationIdOrderByDisplayOrder(String conversationId);

    @Query("select candidate.id.conversationId from CvChatConversationCandidate candidate "
            + "where candidate.id.candidateId = :candidateId and candidate.organizationId = :organizationId")
    List<String> findConversationIdsByCandidateIdAndOrganizationId(Long candidateId, String organizationId);

    /** Flushes the full-scope removal before replacement inserts can reuse the same candidate/order keys. */
    @Modifying(flushAutomatically = true)
    @Query("delete from CvChatConversationCandidate candidate where candidate.id.conversationId = :conversationId")
    void deleteAllByIdConversationId(String conversationId);
}
