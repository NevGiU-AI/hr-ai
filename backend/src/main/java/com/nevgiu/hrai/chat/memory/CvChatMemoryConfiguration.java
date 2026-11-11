package com.nevgiu.hrai.chat.memory;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Builds a bounded model-context window on top of Spring AI's PostgreSQL repository. */
@Configuration
class CvChatMemoryConfiguration {
    @Bean
    ChatMemory cvChatMemory(ChatMemoryRepository repository, CvChatConversationProperties properties) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(properties.maxMemoryMessages())
                .build();
    }
}
