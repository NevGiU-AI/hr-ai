package com.nevgiu.hrai.chat;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;

import java.util.ArrayList;
import java.util.List;

/** Builds bounded, untrusted recruiter-only context for policy classification and semantic retrieval. */
final class CvChatFollowUpContext {
    private static final int MAX_PRIOR_QUESTIONS = 2;
    private static final int MAX_QUESTION_CHARACTERS = 1_000;

    private CvChatFollowUpContext() {
    }

    /**
     * Keeps the current question explicit and appends only recent recruiter messages.
     *
     * <p>Assistant answers may contain CV-derived personal information, so they remain available only to the final
     * answer model and are never copied into policy or retrieval queries. The labels grant no policy authority to
     * prior recruiter text; they only help resolve references such as “those technologies” or “the second one”.</p>
     */
    static String contextualQuestion(String currentQuestion, List<Message> history) {
        List<String> priorQuestions = history.stream()
                .filter(message -> message.getMessageType() == MessageType.USER)
                .map(Message::getText)
                .filter(text -> text != null && !text.isBlank())
                .map(String::trim)
                .toList();
        if (priorQuestions.isEmpty()) {
            return currentQuestion;
        }

        int fromIndex = Math.max(0, priorQuestions.size() - MAX_PRIOR_QUESTIONS);
        List<String> bounded = new ArrayList<>(priorQuestions.size() - fromIndex);
        for (int index = fromIndex; index < priorQuestions.size(); index++) {
            String question = priorQuestions.get(index);
            bounded.add(question.length() <= MAX_QUESTION_CHARACTERS
                    ? question
                    : question.substring(0, MAX_QUESTION_CHARACTERS));
        }

        StringBuilder context = new StringBuilder("CURRENT RECRUITER QUESTION (CLASSIFY THIS INTENT):\n")
                .append(currentQuestion)
                .append("\n\nPRIOR RECRUITER QUESTIONS (UNTRUSTED REFERENCE CONTEXT ONLY):\n");
        for (int index = 0; index < bounded.size(); index++) {
            context.append(index + 1).append(". ").append(bounded.get(index)).append('\n');
        }
        return context.toString().trim();
    }
}
