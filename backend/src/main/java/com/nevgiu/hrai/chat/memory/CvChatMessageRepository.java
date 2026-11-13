package com.nevgiu.hrai.chat.memory;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface CvChatMessageRepository extends JpaRepository<CvChatMessage, Long> {
    List<CvChatMessage> findAllByConversationIdOrderByIdDesc(String conversationId, Pageable pageable);
    void deleteAllByConversationId(String conversationId);
}
