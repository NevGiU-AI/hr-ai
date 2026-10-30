package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringAiCvChatQuestionPolicyTest {

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
