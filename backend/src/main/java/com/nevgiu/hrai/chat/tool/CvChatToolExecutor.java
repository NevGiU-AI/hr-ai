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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

/** Runs read tools on virtual threads so a bounded timeout can interrupt a slow database operation. */
@Component
class CvChatToolExecutor {
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final Duration timeout;

    CvChatToolExecutor() {
        this(Duration.ofSeconds(3));
    }

    CvChatToolExecutor(Duration timeout) {
        this.timeout = timeout;
    }

    <T> T execute(Supplier<T> action) {
        Future<T> future = executor.submit(action::get);
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
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

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }
}
