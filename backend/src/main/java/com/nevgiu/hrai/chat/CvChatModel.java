package com.nevgiu.hrai.chat;

import java.util.List;

/**
 * Provider boundary kept separate so authorization and citation behavior can be tested deterministically.
 * Implementations receive only the evidence already authorized and minimized by {@link CvChatEvidenceService}.
 */
interface CvChatModel {
    String answer(String question, List<CvChatEvidence> evidence);
}
