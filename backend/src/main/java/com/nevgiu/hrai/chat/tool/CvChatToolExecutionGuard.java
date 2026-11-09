package com.nevgiu.hrai.chat.tool;

import com.nevgiu.hrai.chat.CvChatException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Per-request guard that prevents a model from repeatedly or recursively exhausting read-tool resources.
 *
 * <p>Spring components are singleton-scoped by default, but a singleton counter would combine unrelated users' calls
 * and could let one busy tenant throttle another. {@link RequestScope} creates a new guard with a zero counter for
 * every HTTP request and discards it after the response. All tool callbacks made during one request resolve and share
 * that request's guard through Spring's scoped proxy.</p>
 */
@Component
@RequestScope
public class CvChatToolExecutionGuard {
    private static final int MAX_CALLS_PER_REQUEST = 4;
    private final AtomicInteger callCount = new AtomicInteger();
    private final CvChatToolExecutor executor;

    CvChatToolExecutionGuard(CvChatToolExecutor executor) {
        // The guard is request-scoped, but the stateless executor is intentionally a singleton. Each request receives
        // its own call budget while all requests safely reuse the common timeout/virtual-thread execution mechanism.
        this.executor = executor;
    }

    <T> T execute(Supplier<T> action) {
        // Increment before execution so failed and timed-out calls still consume budget; otherwise a model could retry
        // a failing operation indefinitely without reaching the limit.
        if (callCount.incrementAndGet() > MAX_CALLS_PER_REQUEST) {
            throw new CvChatException(HttpStatus.TOO_MANY_REQUESTS, "CV chat tool call limit exceeded");
        }
        return executor.execute(action);
    }
}
