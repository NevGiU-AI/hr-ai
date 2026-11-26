package com.nevgiu.hrai.chat;

/**
 * Application-assigned outcome for a successful chat response; HTTP failures are separate.
 * The service assigns this where it chooses the response so consumers do not have to infer it from
 * display wording or citation count. An operational answer and an insufficient-evidence response
 * can both have no CV citations, but only the former reports facts from application records.
 */
public enum CvChatAnswerKind {
    ANSWER, INSUFFICIENT_EVIDENCE, OUT_OF_SCOPE, TOOL_STATUS
}
