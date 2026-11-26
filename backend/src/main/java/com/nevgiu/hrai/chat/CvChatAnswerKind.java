package com.nevgiu.hrai.chat;

/** Application-assigned outcome for a successful chat response; HTTP failures are separate. */
public enum CvChatAnswerKind {
    ANSWER, INSUFFICIENT_EVIDENCE, OUT_OF_SCOPE, TOOL_STATUS
}
