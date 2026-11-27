import { cvChatQuestionLanguage } from './cv-chat-question-language';

describe('cvChatQuestionLanguage', () => {
  it('recognizes clear questions without relying on technical terms', () => {
    expect(cvChatQuestionLanguage('Does this CV document a Kubernetes certification?')).toBe('en');
    expect(cvChatQuestionLanguage('Quelle était son expérience professionnelle chez KLx ?')).toBe('fr');
    expect(cvChatQuestionLanguage('Qual foi a experiência desta pessoa com Java?')).toBe('pt');
  });

  it('abstains for short, mixed, unsupported, and repeated-word input', () => {
    expect(cvChatQuestionLanguage('OCR ?')).toBeNull();
    expect(cvChatQuestionLanguage('Does this candidate have une expérience chez KLx avec Java ?')).toBeNull();
    expect(cvChatQuestionLanguage('¿Dónde trabajó la persona durante su carrera?')).toBeNull();
    expect(cvChatQuestionLanguage('quelle quelle quelle quelle quelle')).toBeNull();
  });
});
