package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringAiCvChatQuestionPolicyTest {

    @Test
    void explicitlyTreatsShortProfessionalQuestionsAsJobRelevant() {
        // This exact staging question previously produced a false refusal. Keep it in the policy contract so future
        // prompt edits cannot silently remove the terse-question guidance or its concrete regression example.
        assertThat(SpringAiCvChatQuestionPolicy.SYSTEM_PROMPT)
                .contains("\"Is it a Java engineer ?\" -> JOB_RELEVANT")
                .contains("\"Has the candidate worked as an engineer for a religious organization?\" -> JOB_RELEVANT")
                .contains("An employer's identity or sector is professional context")
                .contains("short yes/no questions and grammatically imperfect wording");
    }

    @Test
    void acceptsOnlyApplicationOwnedPolicyLabels() {
        assertThat(SpringAiCvChatQuestionPolicy.parse(" JOB_RELEVANT "))
                .isEqualTo(CvChatQuestionPolicyResult.JOB_RELEVANT);
        assertThat(SpringAiCvChatQuestionPolicy.parse("out_of_scope"))
                .isEqualTo(CvChatQuestionPolicyResult.OUT_OF_SCOPE);
        assertThat(SpringAiCvChatQuestionPolicy.parse("SENSITIVE"))
                .isEqualTo(CvChatQuestionPolicyResult.SENSITIVE);
    }

    @Test
    void rejectsEmptyOrArbitraryProviderOutput() {
        assertThatThrownBy(() -> SpringAiCvChatQuestionPolicy.parse(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CV chat question policy returned an empty response");
        assertThatThrownBy(() -> SpringAiCvChatQuestionPolicy.parse("Religion is sensitive"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("CV chat question policy returned an unsupported result");
    }
}
