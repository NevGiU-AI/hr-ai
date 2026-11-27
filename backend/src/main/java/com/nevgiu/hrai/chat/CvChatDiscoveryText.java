package com.nevgiu.hrai.chat;

import org.springframework.context.i18n.LocaleContextHolder;

/** Application-owned discovery guidance. Translate by workflow outcome, never by matching display text. */
final class CvChatDiscoveryText {
    private CvChatDiscoveryText() {
    }

    static String localize(CvChatToolOrchestrationOutcome.Kind kind, boolean organizationWide, String english) {
        return switch (LocaleContextHolder.getLocale().getLanguage()) {
            case "fr" -> switch (kind) {
                case CANDIDATE_CONFIRMATION_REQUIRED -> organizationWide
                        ? "Des candidatures correspondent aux critères. Confirmez jusqu’à cinq candidatures."
                        : "Des candidatures ont été trouvées. Confirmez les fiches avant de continuer.";
                case CLARIFICATION_REQUIRED -> organizationWide
                        ? "Aucune candidature indexée ne correspond à ces critères professionnels. Précisez la demande."
                        : "Précisez les noms des personnes avant de continuer.";
                case REFUSED -> "Je peux uniquement aider à examiner les candidatures dans le périmètre autorisé "
                        + "et pour des critères liés au poste. Je ne peux ni fournir ni déduire de caractéristiques "
                        + "personnelles protégées ou sensibles.";
                case CONFIRMED_SCOPE_READY -> "Le périmètre confirmé est prêt pour la comparaison.";
            };
            case "pt" -> switch (kind) {
                case CANDIDATE_CONFIRMATION_REQUIRED -> organizationWide
                        ? "Foram encontradas candidaturas que correspondem aos critérios. Confirme até cinco candidaturas."
                        : "Foram encontradas candidaturas. Confirme os cartões antes de continuar.";
                case CLARIFICATION_REQUIRED -> organizationWide
                        ? "Nenhuma candidatura indexada corresponde a estes critérios profissionais. Refine o pedido."
                        : "Esclareça os nomes das pessoas antes de continuar.";
                case REFUSED -> "Só posso ajudar a analisar candidaturas no âmbito autorizado e com critérios "
                        + "relevantes para a vaga. Não posso fornecer nem inferir características pessoais "
                        + "protegidas ou sensíveis.";
                case CONFIRMED_SCOPE_READY -> "O âmbito confirmado está pronto para a comparação.";
            };
            default -> english;
        };
    }
}
