package com.nevgiu.hrai.chat.tool;

import com.nevgiu.hrai.chat.CvChatException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class CvChatToolFailureConfigurationTest {

    @Test
    void rethrowsReviewedCvChatFailureInsteadOfReturningItToTheModel() {
        var expected = new CvChatException(HttpStatus.GATEWAY_TIMEOUT, "CV chat tool execution timed out");
        var processor = new CvChatToolFailureConfiguration().toolExecutionExceptionProcessor();
        var springAiFailure = new ToolExecutionException(mock(ToolDefinition.class), expected);

        assertThatThrownBy(() -> processor.process(springAiFailure))
                .isSameAs(expected)
                .isInstanceOfSatisfying(CvChatException.class,
                        failure -> assertThat(failure.status()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT));
    }

    @Test
    void rethrowsReviewedInterruptionInsteadOfReturningItToTheModel() {
        var expected = new CvChatException(HttpStatus.SERVICE_UNAVAILABLE,
                "CV chat tool execution interrupted");
        var processor = new CvChatToolFailureConfiguration().toolExecutionExceptionProcessor();
        var springAiFailure = new ToolExecutionException(mock(ToolDefinition.class), expected);

        assertThatThrownBy(() -> processor.process(springAiFailure))
                .isSameAs(expected)
                .isInstanceOfSatisfying(CvChatException.class, failure -> {
                    assertThat(failure.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(failure).hasMessage("CV chat tool execution interrupted");
                });
    }
}
