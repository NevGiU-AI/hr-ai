package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringAiCvChatCandidateReferenceExtractorTest {
    @Test
    void promptTreatsPartialNamesSeparatedByAConjunctionAsDistinctReferences() {
        assertThat(SpringAiCvChatCandidateReferenceExtractor.SYSTEM_PROMPT)
                .contains("first name, surname, nickname, or incomplete name")
                .contains("Compare Ada and Grace for backend engineering")
                .contains("CANDIDATE: Ada\nCANDIDATE: Grace");
    }

    @Test
    void parsesBoundedDistinctCandidateReferences() {
        assertThat(SpringAiCvChatCandidateReferenceExtractor.parse(
                "CANDIDATE: Ada Lovelace\nCANDIDATE: Grace Hopper\nCANDIDATE: ada lovelace"))
                .containsExactly("Ada Lovelace", "Grace Hopper");
    }

    @Test
    void acceptsOneReferenceAndReturnsNoScopeOnlyWhenNoneArePresent() {
        assertThat(SpringAiCvChatCandidateReferenceExtractor.parse("NONE")).isEmpty();
        assertThat(SpringAiCvChatCandidateReferenceExtractor.parse("CANDIDATE: Ada")).containsExactly("Ada");
    }

    @Test
    void rejectsProviderProseInsteadOfTreatingItAsAName() {
        assertThatThrownBy(() -> SpringAiCvChatCandidateReferenceExtractor.parse(
                "Here are the candidates: Ada and Grace"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
