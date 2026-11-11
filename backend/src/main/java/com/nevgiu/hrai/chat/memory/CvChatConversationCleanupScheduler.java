package com.nevgiu.hrai.chat.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;

/** Removes expired authorization scope and its subordinate Spring AI memory in bounded batches. */
@Service
class CvChatConversationCleanupScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(CvChatConversationCleanupScheduler.class);
    private final CvChatConversationRepository conversations;
    private final CvChatConversationService service;
    private final CvChatConversationProperties properties;

    CvChatConversationCleanupScheduler(CvChatConversationRepository conversations,
                                       CvChatConversationService service,
                                       CvChatConversationProperties properties) {
        this.conversations = conversations;
        this.service = service;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.cv-chat.conversation.cleanup-cron:0 29 4 * * *}")
    void expireConversations() {
        Instant now = Instant.now();
        var ids = conversations.findExpiredIds(now, PageRequest.of(0, properties.cleanupBatchSize()));
        for (String id : ids) {
            try {
                service.expire(id, now);
            } catch (RuntimeException failure) {
                // Never log prompts, messages, candidate identities, or provider data.
                LOGGER.error("Unable to expire CV chat conversation conversationId={} failureType={}",
                        id, failure.getClass().getSimpleName());
            }
        }
    }
}
