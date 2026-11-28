@cv-chat @conversation @provider @localized-outcomes
Feature: Localized CV-chat controlled replies
  Scenario Outline: A recruiter selects <language> replies in the English interface
    Given a recruiter owns an active CV-chat conversation
    When the recruiter selects "<language>" for controlled replies
    Then a job-relevant question with missing evidence shows the "<code>" evidence-limit reply
    And a sensitive question shows the "<code>" refusal

    Examples:
      | language  | code |
      | English   | en   |
      | Français  | fr   |
      | Português | pt   |
