package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringAiCvChatCandidateReferenceExtractorTest {
    @Test
    void parsesBoundedDistinctCandidateReferences() {
        assertThat(SpringAiCvChatCandidateReferenceExtractor.parse(
                "CANDIDATE: Ada Lovelace\nCANDIDATE: Grace Hopper\nCANDIDATE: ada lovelace"))
                .containsExactly("Ada Lovelace", "Grace Hopper");
    }

    @Test
    void returnsNoScopeWhenFewerThanTwoReferencesArePresent() {
        assertThat(SpringAiCvChatCandidateReferenceExtractor.parse("NONE")).isEmpty();
        assertThat(SpringAiCvChatCandidateReferenceExtractor.parse("CANDIDATE: Ada")).isEmpty();
    }

    @Test
    void rejectsProviderProseInsteadOfTreatingItAsAName() {
        assertThatThrownBy(() -> SpringAiCvChatCandidateReferenceExtractor.parse(
                "Here are the candidates: Ada and Grace"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
