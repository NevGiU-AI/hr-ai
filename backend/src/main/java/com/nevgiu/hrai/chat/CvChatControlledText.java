package com.nevgiu.hrai.chat;

import org.springframework.context.i18n.LocaleContextHolder;

/** Translates application-owned outcomes only; it does not detect or translate provider answer text. */
record CvChatControlledText(String language, String text) {
    static CvChatControlledText resolve(CvChatAnswerKind kind, boolean comparison, String english) {
        // Spring resolves the HTTP request locale. Service calls without a request context default to English.
        var context = LocaleContextHolder.getLocaleContext();
        var locale = context == null ? null : context.getLocale();
        String language = locale == null ? "en" : locale.getLanguage();
        return switch (language) {
            case "fr" -> new CvChatControlledText("fr", kind == CvChatAnswerKind.INSUFFICIENT_EVIDENCE
                    ? (comparison ? "Les éléments disponibles dans les CV ne suffisent pas pour comparer toutes les personnes sélectionnées."
                                  : "Les éléments disponibles dans le CV ne suffisent pas pour répondre à cette question.")
                    : "Je peux uniquement traiter les questions pertinentes pour le poste à partir des CV autorisés. "
                      + "Je ne peux ni fournir ni déduire de caractéristiques personnelles protégées ou sensibles.");
            case "pt" -> new CvChatControlledText("pt", kind == CvChatAnswerKind.INSUFFICIENT_EVIDENCE
                    ? (comparison ? "A informação disponível nos currículos não é suficiente para comparar todas as pessoas selecionadas."
                                  : "A informação disponível no currículo não é suficiente para responder a esta pergunta.")
                    : "Só posso responder a perguntas relevantes para a função com base nos currículos autorizados. "
                      + "Não posso fornecer nem inferir características pessoais protegidas ou sensíveis.");
            default -> new CvChatControlledText("en", english);
        };
    }
}
