package com.nevgiu.hrai.chat.tool;

import com.nevgiu.hrai.chat.CvChatException;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;
import org.springframework.ai.tool.execution.ToolExecutionExceptionProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/** Preserves reviewed application failures raised inside Spring AI tool callbacks. */
@Configuration
class CvChatToolFailureConfiguration {

    @Bean
    ToolExecutionExceptionProcessor toolExecutionExceptionProcessor() {
        // Spring AI normally turns a callback exception into text that the model may inspect and continue from. A
        // CvChatException already contains the application-reviewed HTTP status and privacy-safe message, so return it
        // to the HTTP boundary instead. This is essential for 504 timeouts and also prevents a model from retrying or
        // reinterpreting application-owned authorization, validation, and call-budget failures.
        return DefaultToolExecutionExceptionProcessor.builder()
                .rethrowExceptions(List.of(CvChatException.class))
                .build();
    }
}
