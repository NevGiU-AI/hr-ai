package com.nevgiu.hrai.chat.tool;

import com.nevgiu.hrai.chat.CvChatException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** Per-request guard that prevents a model from repeatedly or recursively exhausting read-tool resources. */
@Component
@RequestScope
public class CvChatToolExecutionGuard {
    private static final int MAX_CALLS_PER_REQUEST = 4;
    private final AtomicInteger callCount = new AtomicInteger();
    private final CvChatToolExecutor executor;

    CvChatToolExecutionGuard(CvChatToolExecutor executor) {
        this.executor = executor;
    }

    <T> T execute(Supplier<T> action) {
        if (callCount.incrementAndGet() > MAX_CALLS_PER_REQUEST) {
            throw new CvChatException(HttpStatus.TOO_MANY_REQUESTS, "CV chat tool call limit exceeded");
        }
        return executor.execute(action);
    }
}
