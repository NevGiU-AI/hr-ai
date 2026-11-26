// Interface copy is selected independently of the language requested for backend replies.
const COPY = {
  en: {
    retryResume: "Retry loading conversation",
    checkHistory: "Check chat history",
    insufficient: "Insufficient evidence",
    outOfScope: "Outside CV chat scope",
    operational: "Operational information",
    uncertainSend: "We could not confirm whether your message was saved. Check chat history before sending it again.",
    historyRefreshed: "History refreshed. If your message is already in the chat, do not send it again.",
    historyMissing: "This chat is no longer available. Start a new chat if you want to ask this question again.",
    historyCheckFailed: "Chat history could not be checked. Your draft is still here; try checking again.",
    resumeMissing: "This conversation is no longer available. Choose another chat or start a new one.",
    resumeTemporary: "The conversation could not be loaded right now.",
    resumeFailed: "The conversation could not be loaded.",
    sendFailed: "Message could not be sent.",
  },
  fr: {
    retryResume: "Réessayer de charger la conversation",
    checkHistory: "Vérifier l’historique de la conversation",
    insufficient: "Éléments insuffisants",
    outOfScope: "Hors du périmètre du chat CV",
    operational: "Informations opérationnelles",
    uncertainSend: "Impossible de confirmer l’enregistrement de votre message. Vérifiez l’historique avant de le renvoyer.",
    historyRefreshed: "Historique actualisé. Si votre message figure déjà dans la conversation, ne le renvoyez pas.",
    historyMissing: "Cette conversation n’est plus disponible. Lancez une nouvelle conversation pour poser à nouveau cette question.",
    historyCheckFailed: "Impossible de vérifier l’historique. Votre brouillon est toujours disponible ; réessayez.",
    resumeMissing: "Cette conversation n’est plus disponible. Choisissez une autre conversation ou lancez-en une nouvelle.",
    resumeTemporary: "Impossible de charger la conversation pour le moment.",
    resumeFailed: "Impossible de charger la conversation.",
    sendFailed: "Impossible d’envoyer le message.",
  },
  pt: {
    retryResume: "Tentar carregar a conversa novamente",
    checkHistory: "Verificar o histórico da conversa",
    insufficient: "Evidência insuficiente",
    outOfScope: "Fora do âmbito do chat de CV",
    operational: "Informações operacionais",
    uncertainSend: "Não foi possível confirmar se a sua mensagem foi guardada. Verifique o histórico antes de a enviar novamente.",
    historyRefreshed: "Histórico atualizado. Se a mensagem já estiver na conversa, não a envie novamente.",
    historyMissing: "Esta conversa já não está disponível. Inicie uma nova conversa para voltar a fazer esta pergunta.",
    historyCheckFailed: "Não foi possível verificar o histórico. O seu rascunho continua disponível; tente novamente.",
    resumeMissing: "Esta conversa já não está disponível. Escolha outra ou inicie uma nova.",
    resumeTemporary: "Não foi possível carregar a conversa neste momento.",
    resumeFailed: "Não foi possível carregar a conversa.",
    sendFailed: "Não foi possível enviar a mensagem.",
  },
};

export function cvChatInterfaceCopy(locale: string) {
  const language = locale.toLowerCase().split('-')[0];
  return language === 'fr' || language === 'pt' ? COPY[language] : COPY.en;
}
