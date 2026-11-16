package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CvChatExplicitCriteriaTest {
    @Test
    void removesDiscoveryBoilerplateAndPreservesNamedProfessionalTerms() {
        assertThat(CvChatExplicitCriteria.from("Find the best Python devs with Spring Boot and C++"))
                .containsExactly("python", "spring", "boot", "c++");
    }

    @Test
    void matchesOnlyCompleteTermsIgnoringCase() {
        var criteria = CvChatExplicitCriteria.from("Find Java devs");

        assertThat(CvChatExplicitCriteria.countMatches("JAVA and Spring.", criteria)).isEqualTo(1);
        assertThat(CvChatExplicitCriteria.countMatches("JavaScript and TypeScript", criteria)).isZero();
    }
}
