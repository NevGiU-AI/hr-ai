package com.nevgiu.hrai.chat;

/** Classifies an untrusted question before any candidate evidence is retrieved or disclosed. */
interface CvChatQuestionPolicy {

    CvChatQuestionPolicyResult classify(String question);
}
