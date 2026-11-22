package com.nevgiu.hrai.chat.tool;

import com.nevgiu.hrai.chat.CvChatException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CvChatToolExecutionGuardTest {

    @Test
    void rejectsCallsBeyondThePerRequestLimit() {
        CvChatToolExecutor executor = mock(CvChatToolExecutor.class);
        when(executor.execute(org.mockito.ArgumentMatchers.<java.util.function.Supplier<Integer>>any()))
                .thenAnswer(invocation -> invocation.<java.util.function.Supplier<Integer>>getArgument(0).get());
        CvChatToolExecutionGuard guard = new CvChatToolExecutionGuard(executor);

        for (int call = 1; call <= 10; call++) {
            int expected = call;
            assertThat(guard.execute(() -> expected)).isEqualTo(expected);
        }
        assertThatThrownBy(() -> guard.execute(() -> 11))
                .isInstanceOf(CvChatException.class)
                .hasMessage("CV chat tool call limit exceeded");
    }

    @Test
    void interruptsToolWorkThatExceedsTheReviewedTimeout() {
        CvChatToolExecutor executor = new CvChatToolExecutor(Duration.ofMillis(20));
        try {
            assertThatThrownBy(() -> executor.execute(() -> {
                try {
                    Thread.sleep(Duration.ofSeconds(5));
                    return "unexpected";
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return "interrupted";
                }
            })).isInstanceOf(CvChatException.class)
                    .hasMessage("CV chat tool execution timed out");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void givesBoundedSemanticDiscoveryItsReviewedLongerTimeout() {
        CvChatToolExecutor executor = mock(CvChatToolExecutor.class);
        when(executor.execute(org.mockito.ArgumentMatchers.<Supplier<Integer>>any(),
                org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(20))))
                .thenAnswer(invocation -> invocation.<Supplier<Integer>>getArgument(0).get());
        CvChatToolExecutionGuard guard = new CvChatToolExecutionGuard(executor);

        assertThat(guard.executeSemanticDiscovery(() -> 42)).isEqualTo(42);

        verify(executor).execute(org.mockito.ArgumentMatchers.<Supplier<Integer>>any(),
                org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(20)));
    }
}
