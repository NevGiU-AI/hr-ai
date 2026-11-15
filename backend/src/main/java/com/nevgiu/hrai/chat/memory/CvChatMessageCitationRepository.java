package com.nevgiu.hrai.chat.memory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface CvChatMessageCitationRepository extends JpaRepository<CvChatMessageCitation, Long> {
    List<CvChatMessageCitation> findAllByMessageIdOrderByDisplayOrder(Long messageId);
}
