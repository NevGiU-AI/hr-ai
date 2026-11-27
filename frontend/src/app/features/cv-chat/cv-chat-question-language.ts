import { CvChatResponseLanguage } from '../cv-ingestion/models/cv-chat.models';

// Prefer grammatical words over technology names, candidate names, or accents alone.
// The heuristic is intentionally limited to supported languages and can abstain.
const MARKERS: Record<CvChatResponseLanguage, ReadonlySet<string>> = {
  en: new Set(['the', 'this', 'these', 'does', 'what', 'which', 'with', 'their', 'about', 'has', 'were']),
  fr: new Set(['les', 'des', 'est', 'sont', 'cette', 'quel', 'quelle', 'quels', 'quelles', 'chez', 'dans', 'avec', 'était']),
  pt: new Set(['qual', 'quais', 'essa', 'esse', 'esta', 'este', 'foi', 'tem', 'não', 'nas', 'nos', 'uma', 'trabalhou']),
};

export function cvChatQuestionLanguage(question: string): CvChatResponseLanguage | null {
  const words = question.normalize('NFC').toLowerCase().match(/\p{L}+/gu) ?? [];
  if (words.length < 5) return null;

  const distinctWords = new Set(words);
  const matches = (Object.keys(MARKERS) as CvChatResponseLanguage[]).filter((language) =>
    [...distinctWords].filter((word) => MARKERS[language].has(word)).length >= 2);

  // Repetition is not stronger evidence, and competing signals are not a reason to guess.
  return matches.length === 1 ? matches[0] : null;
}
