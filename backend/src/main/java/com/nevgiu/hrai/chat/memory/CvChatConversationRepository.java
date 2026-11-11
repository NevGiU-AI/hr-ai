package com.nevgiu.hrai.chat.memory;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface CvChatConversationRepository extends JpaRepository<CvChatConversation, String> {
    Optional<CvChatConversation> findByIdAndOrganizationIdAndOwnerUserIdAndExpiresAtAfter(
            String id, String organizationId, Long ownerUserId, Instant now);

    @Query("select conversation.id from CvChatConversation conversation "
            + "where conversation.expiresAt <= :now order by conversation.expiresAt, conversation.id")
    List<String> findExpiredIds(Instant now, Pageable pageable);

    /** Serializes cleanup across replicas and rechecks expiry after acquiring the database lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select conversation from CvChatConversation conversation where conversation.id = :id")
    Optional<CvChatConversation> findLockedById(String id);
}
