package com.nevgiu.hrai.chat;

import com.nevgiu.hrai.candidate.CandidateIdentity;
import com.nevgiu.hrai.job.Job;
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
                .contains("the supplied evidence does not show")
                .contains("based on the supplied evidence")
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

    @Test
    void suppliesOnlyBoundedComparisonRelevantApprovedJobFields() {
        Job job = new Job();
        job.setId(12L);
        job.setTitle("Backend Engineer");
        job.setRequiredQualifications("Java and Spring Boot");
        job.setSalaryRange("private salary");
        job.setBenefits("private benefits");

        String prompt = SpringAiCvChatComparisonModel.buildUserPrompt(
                "Compare for this approved job",
                List.of(new CandidateIdentity(7L, "Ada Lovelace", "Engineer")),
                List.of(new CvChatEvidence("CV-10", 7L, 10L, null, "Java experience")),
                CvChatComparisonJobContext.from(job));

        assertThat(prompt)
                .contains("AUTHORIZED APPROVED JOB REQUIREMENTS")
                .contains("APPROVED-JOB-12")
                .contains("Title: Backend Engineer")
                .contains("Required qualifications: Java and Spring Boot")
                .doesNotContain("private salary")
                .doesNotContain("private benefits");
    }

    @Test
    void boundsTheCompleteApprovedJobContextAcrossSeveralLongFields() {
        Job job = new Job();
        job.setId(12L);
        job.setTitle("Backend Engineer");
        String longValue = "requirement ".repeat(500);
        job.setSummary(longValue);
        job.setResponsibilities(longValue);
        job.setRequiredQualifications(longValue);
        job.setPreferredQualifications(longValue);
        job.setSoftSkills(longValue);

        String context = CvChatComparisonJobContext.from(job).promptText();

        assertThat(context)
                .hasSizeLessThanOrEqualTo(10_000)
                .contains("APPROVED-JOB-12")
                .contains("Required qualifications:");
    }
}
