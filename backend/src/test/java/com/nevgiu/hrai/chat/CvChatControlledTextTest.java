package com.nevgiu.hrai.chat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class CvChatControlledTextTest {
    @AfterEach
    void clearLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void rendersFrenchInsufficientEvidenceWithoutChangingTheOutcome() {
        LocaleContextHolder.setLocale(Locale.CANADA_FRENCH);

        var response = CvChatResponse.controlled(CvChatAnswerKind.INSUFFICIENT_EVIDENCE, "English fallback");

        assertThat(response.kind()).isEqualTo(CvChatAnswerKind.INSUFFICIENT_EVIDENCE);
        assertThat(response.responseLanguage()).isEqualTo("fr");
        assertThat(response.answer()).contains("ne suffisent pas");
        assertThat(response.citations()).isEmpty();
    }

    @Test
    void rendersPortugueseComparisonAndRefusal() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("pt-PT"));

        assertThat(CvChatControlledText.resolve(CvChatAnswerKind.INSUFFICIENT_EVIDENCE, true, "fallback"))
                .satisfies(text -> {
                    assertThat(text.language()).isEqualTo("pt");
                    assertThat(text.text()).contains("comparar todas as pessoas selecionadas");
                });
        var refusal = CvChatResponse.controlled(CvChatAnswerKind.OUT_OF_SCOPE, "fallback");
        assertThat(refusal.responseLanguage()).isEqualTo("pt");
        assertThat(refusal.answer()).contains("características pessoais protegidas ou sensíveis");
    }

    @Test
    void fallsBackToEnglishForUnsupportedLocales() {
        LocaleContextHolder.setLocale(Locale.GERMANY);
        var response = CvChatResponse.controlled(CvChatAnswerKind.OUT_OF_SCOPE, "English fallback");
        assertThat(response.responseLanguage()).isEqualTo("en");
        assertThat(response.answer()).isEqualTo("English fallback");
    }
}
