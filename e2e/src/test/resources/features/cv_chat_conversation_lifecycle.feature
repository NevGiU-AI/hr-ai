@cv-chat @conversation @smoke
Feature: CV-chat conversation lifecycle
  Recruiters can resume, navigate evidence from, and explicitly delete their own persisted conversations.

  Scenario: A recruiter resumes the same confirmed scope
    Given a recruiter owns an active CV-chat conversation
    When the recruiter leaves and resumes that conversation
    Then the same confirmed candidate scope survives a browser reload

  @provider
  Scenario: A grounded answer navigates to its cited CV document
    Given a recruiter owns an active CV-chat conversation
    When the recruiter asks the configured grounded question
    Then the first citation opens its rendered CV document

  Scenario: A recruiter permanently deletes a conversation
    Given a recruiter owns an active CV-chat conversation
    When the recruiter deletes that conversation
    Then the deleted conversation is absent from recent conversations
    And the recruiter can no longer retrieve the deleted conversation
