package com.nevgiu.hrai.chat.tool;

import com.nevgiu.hrai.chat.CvChatException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
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
    // Discovery supports up to five explicitly referenced candidates, so one request must permit five bounded
    // searches. Calls beyond that reviewed product limit are rejected before more work reaches repositories.
    // A document-processing question may legitimately call both reviewed tools for five confirmed candidates.
    private static final int MAX_CALLS_PER_REQUEST = 10;
    // Organization-wide evidence discovery includes one bounded provider normalization call and up to two pgvector
    // searches. Keep its latency budget separate so lightweight repository tools retain the executor's 3-second cap.
    private static final Duration SEMANTIC_DISCOVERY_TIMEOUT = Duration.ofSeconds(20);
    private final AtomicInteger callCount = new AtomicInteger();
    private final Map<CvChatToolCapability, AtomicInteger> capabilityCallCounts;
    private final CvChatToolExecutor executor;

    CvChatToolExecutionGuard(CvChatToolExecutor executor) {
        // The guard is request-scoped, but the stateless executor is intentionally a singleton. Each request receives
        // its own call budget while all requests safely reuse the common timeout/virtual-thread execution mechanism.
        this.executor = executor;
        // Populate every counter before callbacks can run. Atomic values then support concurrent increments without
        // mutating the EnumMap structure if a provider dispatches more than one tool call at the same time.
        EnumMap<CvChatToolCapability, AtomicInteger> counters = new EnumMap<>(CvChatToolCapability.class);
        for (CvChatToolCapability capability : CvChatToolCapability.values()) {
            counters.put(capability, new AtomicInteger());
        }
        this.capabilityCallCounts = counters;
    }

    <T> T execute(CvChatToolCapability capability, Supplier<T> action) {
        // Increment before execution so failed and timed-out calls still consume budget; otherwise a model could retry
        // a failing operation indefinitely without reaching the limit.
        consumeCallBudget(capability);
        return executor.execute(action);
    }

    /** Applies the same request call budget but the reviewed semantic-discovery latency budget. */
    <T> T executeSemanticDiscovery(Supplier<T> action) {
        consumeCallBudget(CvChatToolCapability.ORGANIZATION_DISCOVERY);
        return executor.execute(action, SEMANTIC_DISCOVERY_TIMEOUT);
    }

    private void consumeCallBudget(CvChatToolCapability capability) {
        // The global ceiling bounds total work for mixed-tool requests. The capability budget independently prevents
        // retries or recursive calls from spending that entire allowance on a tool whose product flow permits fewer
        // calls. Both counters are request-local because this guard is request-scoped.
        if (callCount.incrementAndGet() > MAX_CALLS_PER_REQUEST) {
            throw new CvChatException(HttpStatus.TOO_MANY_REQUESTS, "CV chat tool call limit exceeded");
        }
        int capabilityCalls = capabilityCallCounts.get(capability).incrementAndGet();
        if (capabilityCalls > capability.maxCallsPerRequest()) {
            throw new CvChatException(HttpStatus.TOO_MANY_REQUESTS, "CV chat tool capability call limit exceeded");
        }
    }
}
