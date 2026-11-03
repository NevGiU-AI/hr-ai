package com.nevgiu.hrai.chat;

import java.util.List;

/** Extracts untrusted human-readable candidate references without resolving or authorizing candidate records. */
interface CvChatCandidateReferenceExtractor {
    List<String> extract(String message);
}
