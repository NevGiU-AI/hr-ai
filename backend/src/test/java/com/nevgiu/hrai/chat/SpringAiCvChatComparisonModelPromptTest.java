package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.candidate.CandidateIdentity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiCvChatComparisonModelPromptTest {
    @Test
    void keepsCandidateSelectionAndEvidenceInsideTheApplicationPolicyBoundary() {
        String normalizedPrompt = SpringAiCvChatComparisonModel.SYSTEM_PROMPT.replaceAll("\\s+", " ");

        assertThat(normalizedPrompt)
                .contains("Compare only the explicitly selected candidates")
                .contains("Never introduce, search for, or infer another candidate")
                .contains("Evidence references are supplied in square brackets")
                .contains("[CV-12]")
                .contains("[CV-27-P2]")
                .contains("Never emit an unbracketed reference")
                .contains(CvChatModel.INSUFFICIENT_EVIDENCE)
                .contains(CvChatModel.OUT_OF_SCOPE);
    }

    @Test
    void suppliesEveryAuthorizedEvidenceReferenceInValidatorCompatibleBrackets() {
        String prompt = SpringAiCvChatComparisonModel.buildUserPrompt(
                "Compare backend experience",
                List.of(
                        new CandidateIdentity(7L, "Ada Lovelace", "Engineer"),
                        new CandidateIdentity(8L, "Grace Hopper", "Architect")),
                List.of(
                        new CvChatEvidence("CV-10", 7L, 10L, null, "Java experience"),
                        new CvChatEvidence("CV-11-P2", 8L, 11L, 2, "Compiler experience")));

        assertThat(prompt)
                .contains("CANDIDATE-7 [CV-10]\nJava experience")
                .contains("CANDIDATE-8 [CV-11-P2]\nCompiler experience")
                .doesNotContain("CANDIDATE-7 CV-10")
                .doesNotContain("CANDIDATE-8 CV-11-P2");
    }
}
