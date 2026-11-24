package com.nevgiu.hrai.chat.tool;

import com.nevgiu.hrai.chat.CvChatException;
import jakarta.annotation.PreDestroy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/**
 * Runs read tools on virtual threads so the caller can stop waiting after a reviewed timeout without occupying a
 * platform thread. This singleton owns only the executor; tenant, authentication, and call-count state remain in
 * request-scoped collaborators.
 */
@Component
class CvChatToolExecutor {
    // Hikari's default pool has ten connections. Keep tool work at or below that conservative baseline so an
    // unbounded virtual-thread executor cannot create an equally unbounded wave of repository/provider calls. The
    // later concurrency benchmark may tune this reviewed application limit against measured downstream capacity.
    private static final int DEFAULT_MAX_CONCURRENT_EXECUTIONS = 10;
    private static final Duration DEFAULT_CAPACITY_WAIT = Duration.ofMillis(100);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Duration timeout;
    private final Semaphore concurrencyPermits;
    private final Duration capacityWait;

    CvChatToolExecutor() {
        this(Duration.ofSeconds(3), DEFAULT_MAX_CONCURRENT_EXECUTIONS, DEFAULT_CAPACITY_WAIT);
    }

    CvChatToolExecutor(Duration timeout) {
        this(timeout, DEFAULT_MAX_CONCURRENT_EXECUTIONS, DEFAULT_CAPACITY_WAIT);
    }

    CvChatToolExecutor(Duration timeout, int maxConcurrentExecutions, Duration capacityWait) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("Tool timeout must be positive");
        }
        if (maxConcurrentExecutions < 1) {
            throw new IllegalArgumentException("Maximum concurrent tool executions must be positive");
        }
        if (capacityWait == null || capacityWait.isNegative() || capacityWait.isZero()) {
            throw new IllegalArgumentException("Tool capacity wait must be positive");
        }
        this.timeout = timeout;
        // Fair acquisition prevents a sustained stream of newer requests from repeatedly overtaking an older waiter.
        this.concurrencyPermits = new Semaphore(maxConcurrentExecutions, true);
        this.capacityWait = capacityWait;
    }

    <T> T execute(Supplier<T> action) {
        return execute(action, timeout);
    }

    /** Runs one reviewed operation with a capability-specific timeout while reusing the same virtual-thread pool. */
    <T> T execute(Supplier<T> action, Duration operationTimeout) {
        // Future.get provides a hard upper bound for the tool caller. Cancellation interrupts the virtual thread on a
        // best-effort basis; repository/driver code must still honor interruption for the underlying work to stop.
        Future<T> future = executor.submit(() -> executeWithinConcurrencyLimit(action));
        try {
            return future.get(operationTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException failure) {
            future.cancel(true);
            throw new CvChatException(HttpStatus.GATEWAY_TIMEOUT, "CV chat tool execution timed out");
        } catch (InterruptedException failure) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new CvChatException(HttpStatus.SERVICE_UNAVAILABLE, "CV chat tool execution interrupted");
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            throw new CvChatException(HttpStatus.BAD_GATEWAY, "CV chat tool execution failed");
        }
    }

    private <T> T executeWithinConcurrencyLimit(Supplier<T> action) {
        boolean acquired = false;
        try {
            // Wait on the virtual thread, not the request thread. This keeps active downstream work bounded without
            // occupying a platform thread and includes capacity waiting inside the caller's reviewed tool timeout.
            acquired = concurrencyPermits.tryAcquire(capacityWait.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new CvChatException(HttpStatus.SERVICE_UNAVAILABLE,
                        "CV chat tool execution capacity is unavailable");
            }
            return action.get();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new CvChatException(HttpStatus.SERVICE_UNAVAILABLE, "CV chat tool execution interrupted");
        } finally {
            // Release only after the action actually exits. A caller timeout requests cancellation, but work that
            // ignores interruption must continue holding its permit so it cannot silently exceed the downstream cap.
            if (acquired) {
                concurrencyPermits.release();
            }
        }
    }

    @PreDestroy
    void shutdown() {
        // Do not leave virtual-thread tasks alive while Spring is stopping or replacing the application context.
        executor.shutdownNow();
    }
}
