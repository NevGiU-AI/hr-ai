package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class CvChatDiscoveryTextTest {
    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void localizesNamedClarificationWithoutAddingScope() {
        LocaleContextHolder.setLocale(Locale.FRANCE);
        var kind = CvChatToolOrchestrationOutcome.Kind.CLARIFICATION_REQUIRED;
        var response = CvChatToolDiscoveryResponse.from(new CvChatToolOrchestrationOutcome(kind, "fallback"), null);

        assertThat(response.kind()).isEqualTo(kind);
        assertThat(response.candidateScope()).isNull();
        assertThat(response.message()).startsWith("Précisez les noms");
    }

    @Test
    void localizesOrganizationConfirmationWithoutChangingTheShortlist() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("pt-PT"));
        var kind = CvChatToolOrchestrationOutcome.Kind.CANDIDATE_CONFIRMATION_REQUIRED;
        var card = new CvChatOrganizationDiscoveryResponse.CandidateOption(7L, "Ada", null, List.of("CV-7"));
        var response = CvChatOrganizationDiscoveryResponse.localized(kind, "fallback", List.of(card), true);

        assertThat(response.kind()).isEqualTo(kind);
        assertThat(response.candidates()).containsExactly(card);
        assertThat(response.truncated()).isTrue();
        assertThat(response.message()).contains("Confirme até cinco candidaturas");
    }

    @Test
    void keepsTheOriginalEnglishMessageForUnsupportedLocales() {
        LocaleContextHolder.setLocale(Locale.GERMANY);
        var kind = CvChatToolOrchestrationOutcome.Kind.REFUSED;
        assertThat(CvChatDiscoveryText.localize(kind, false, "English fallback")).isEqualTo("English fallback");
    }
}
