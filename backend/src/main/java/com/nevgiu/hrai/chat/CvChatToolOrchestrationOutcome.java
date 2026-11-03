package com.nevgiu.hrai.chat;

/**
 * Structured workflow decision returned by the opt-in tool orchestrator.
 *
 * <p>The model never returns candidate identifiers or authorization facts. Those remain application-owned state so a
 * prompt-injected message or fabricated model field cannot expand the confirmed tenant scope.</p>
 */
public record CvChatToolOrchestrationOutcome(Kind kind, String message) {
    public enum Kind {
        CANDIDATE_CONFIRMATION_REQUIRED,
        CLARIFICATION_REQUIRED,
        CONFIRMED_SCOPE_READY,
        REFUSED
    }
}
