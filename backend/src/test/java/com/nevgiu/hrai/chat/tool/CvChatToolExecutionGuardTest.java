package com.nevgiu.hrai.chat.tool;

import com.nevgiu.hrai.chat.CvChatException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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

        for (int call = 1; call <= 5; call++) {
            int expected = call;
            assertThat(guard.execute(CvChatToolCapability.DOCUMENT_STATUS, () -> expected)).isEqualTo(expected);
        }
        for (int call = 6; call <= 10; call++) {
            int expected = call;
            assertThat(guard.execute(CvChatToolCapability.INGESTION_WARNINGS, () -> expected)).isEqualTo(expected);
        }
        assertThatThrownBy(() -> guard.execute(CvChatToolCapability.CANDIDATE_SEARCH, () -> 11))
                .isInstanceOf(CvChatException.class)
                .hasMessage("CV chat tool call limit exceeded");
    }

    @Test
    void rejectsRepeatedCapabilityCallsBeforeTheGlobalLimit() {
        CvChatToolExecutor executor = mock(CvChatToolExecutor.class);
        when(executor.execute(org.mockito.ArgumentMatchers.<Supplier<Integer>>any()))
                .thenAnswer(invocation -> invocation.<Supplier<Integer>>getArgument(0).get());
        CvChatToolExecutionGuard guard = new CvChatToolExecutionGuard(executor);

        assertThat(guard.execute(CvChatToolCapability.JOB_REQUIREMENTS, () -> 1)).isEqualTo(1);
        assertThatThrownBy(() -> guard.execute(CvChatToolCapability.JOB_REQUIREMENTS, () -> 2))
                .isInstanceOf(CvChatException.class)
                .hasMessage("CV chat tool capability call limit exceeded");
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
    void preservesCallerInterruptionAsReviewedServiceUnavailable() {
        CvChatToolExecutor executor = new CvChatToolExecutor(Duration.ofSeconds(5));
        // A CountDownLatch is a one-use gate. Its initial count of one keeps the tool's virtual thread blocked in
        // await() until this test explicitly calls countDown(). This is more deterministic than sleeping: the tool
        // cannot finish before Future.get observes that the calling JUnit thread has been interrupted.
        CountDownLatch keepToolRunning = new CountDownLatch(1);
        try {
            // Interrupt the caller, not the virtual thread running the tool. Future.get must convert this condition to
            // the reviewed 503 response while also restoring the interrupt flag expected by cooperative Java code.
            Thread.currentThread().interrupt();

            assertThatThrownBy(() -> executor.execute(() -> {
                try {
                    // This await runs on the executor's virtual thread. Cancellation interrupts it after the caller's
                    // Future.get fails, while countDown() in cleanup guarantees the gate is open even if cancellation
                    // behavior changes or the assertion fails early.
                    keepToolRunning.await();
                    return "unexpected";
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return "interrupted";
                }
            })).isInstanceOfSatisfying(CvChatException.class, failure -> {
                assertThat(failure.status()).isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(failure).hasMessage("CV chat tool execution interrupted");
            });
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            // CvChatToolExecutor correctly restores the caller's interrupt flag. Clear it so this JUnit worker can run
            // later tests normally, then release/cancel any virtual-thread work owned by this focused test.
            Thread.interrupted();
            keepToolRunning.countDown();
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

    @Test
    void rejectsSaturatedConcurrencyBeforeRunningMoreDownstreamWorkAndRecoversCapacity() throws Exception {
        CvChatToolExecutor executor = new CvChatToolExecutor(
                Duration.ofSeconds(5), 1, Duration.ofMillis(20));
        CountDownLatch firstActionStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstAction = new CountDownLatch(1);
        AtomicReference<String> firstResult = new AtomicReference<>();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicBoolean rejectedActionRan = new AtomicBoolean();

        Thread firstCaller = Thread.ofVirtual().start(() -> {
            try {
                firstResult.set(executor.execute(() -> {
                    firstActionStarted.countDown();
                    try {
                        releaseFirstAction.await();
                        return "first";
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("First action was unexpectedly interrupted", interrupted);
                    }
                }));
            } catch (Throwable failure) {
                firstFailure.set(failure);
            }
        });

        try {
            // The latch proves that the sole permit is held before the second call attempts acquisition; no sleeps or
            // scheduler timing assumptions are needed for this saturation regression.
            firstActionStarted.await();

            assertThatThrownBy(() -> executor.execute(() -> {
                rejectedActionRan.set(true);
                return "unexpected";
            })).isInstanceOfSatisfying(CvChatException.class, failure -> {
                assertThat(failure.status()).isEqualTo(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(failure).hasMessage("CV chat tool execution capacity is unavailable");
            });
            assertThat(rejectedActionRan).isFalse();

            releaseFirstAction.countDown();
            firstCaller.join();
            assertThat(firstFailure).hasValue(null);
            assertThat(firstResult).hasValue("first");

            // Completion returns the permit, so saturation is temporary rather than a permanent executor leak.
            assertThat(executor.execute(() -> "after-release")).isEqualTo("after-release");
        } finally {
            releaseFirstAction.countDown();
            firstCaller.join();
            executor.shutdown();
        }
    }

    @Test
    void timedOutWorkKeepsItsPermitUntilTheUnderlyingActionActuallyExits() throws Exception {
        CvChatToolExecutor executor = new CvChatToolExecutor(
                Duration.ofMillis(50), 1, Duration.ofMillis(20));
        CountDownLatch actionStarted = new CountDownLatch(1);
        CountDownLatch cancellationObserved = new CountDownLatch(1);
        CountDownLatch allowActionExit = new CountDownLatch(1);
        AtomicBoolean concurrentActionRan = new AtomicBoolean();

        try {
            assertThatThrownBy(() -> executor.execute(() -> {
                actionStarted.countDown();
                // Simulate downstream code that notices cancellation but cannot stop immediately. The executor must
                // keep this action inside the concurrency ceiling until the dependency call really returns.
                boolean interrupted = false;
                while (true) {
                    try {
                        allowActionExit.await();
                        break;
                    } catch (InterruptedException cancellation) {
                        interrupted = true;
                        cancellationObserved.countDown();
                    }
                }
                if (interrupted) {
                    Thread.currentThread().interrupt();
                }
                return "eventually-finished";
            })).isInstanceOf(CvChatException.class)
                    .hasMessage("CV chat tool execution timed out");

            actionStarted.await();
            cancellationObserved.await();
            assertThatThrownBy(() -> executor.execute(() -> {
                concurrentActionRan.set(true);
                return "unexpected";
            })).isInstanceOf(CvChatException.class)
                    .hasMessage("CV chat tool execution capacity is unavailable");
            assertThat(concurrentActionRan).isFalse();

            allowActionExit.countDown();
            assertThat(executor.execute(() -> "after-real-exit")).isEqualTo("after-real-exit");
        } finally {
            allowActionExit.countDown();
            executor.shutdown();
        }
    }
}
