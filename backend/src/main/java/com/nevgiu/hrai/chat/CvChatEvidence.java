package com.nevgiu.hrai.chat;

/** Internal evidence fragment. Content is personal data and must never be written to logs or telemetry. */
record CvChatEvidence(String reference, Long candidateId, Long documentId, Integer pageNumber, String excerpt) { }
