package com.nevgiu.hrai.chat;

import java.util.List;

/** Provider boundary kept separate so authorization and citation behavior can be tested deterministically. */
interface CvChatModel {
    String answer(String question, List<CvChatEvidence> evidence);
}
