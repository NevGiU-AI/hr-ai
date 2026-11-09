package com.nevgiu.hrai.chat;

import java.util.Locale;
import java.util.regex.Pattern;

/** Application-owned pre-model guard for explicit attempts to override protected chat workflow boundaries. */
final class CvChatToolRequestPolicy {
    private static final Pattern OVERRIDE_ACTION = Pattern.compile(
            "\\b(ignore|bypass|skip|fake|assume|override|disable)\\b");
    private static final Pattern PROTECTED_BOUNDARY = Pattern.compile(
            "\\b(confirm(?:ation|ed)?|authori[sz](?:ation|ed)?|tenant|organization|role|tool|scope)\\b");

    private CvChatToolRequestPolicy() {
    }

    /**
     * Rejects explicit instructions to weaken workflow controls before untrusted text reaches a model or callback.
     * This narrow deterministic rule complements, but does not replace, semantic policy evaluation and adversarial
     * tests. Ordinary recruiter requests do not need to mention or modify these application-owned controls.
     */
    static boolean requestsWorkflowOverride(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        return OVERRIDE_ACTION.matcher(normalized).find() && PROTECTED_BOUNDARY.matcher(normalized).find();
    }
}
