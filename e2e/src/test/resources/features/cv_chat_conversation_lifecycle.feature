@cv-chat @conversation @smoke
Feature: CV-chat conversation lifecycle
  Recruiters can resume, navigate evidence from, and explicitly delete their own persisted conversations.

  Scenario: A recruiter resumes the same confirmed scope
    Given a recruiter owns an active CV-chat conversation
    When the recruiter leaves and resumes that conversation
    Then the same confirmed candidate scope survives a browser reload

  @editable-scope
  Scenario: A recruiter changes the approved job and clears the previous timeline
    Given a recruiter owns an active CV-chat conversation
    When the recruiter asks for the CV extraction status
    And the recruiter adds an approved job to the confirmed scope
    Then the same conversation keeps the edited scope and an empty timeline after reload

  @provider
  Scenario: A grounded answer navigates to its cited CV document
    Given a recruiter owns an active CV-chat conversation
    When the recruiter asks the configured grounded question
    Then the first citation opens its rendered CV document

  # This is safe for the regular smoke suite: the scenario creates and deletes only its own disposable conversation.
  # Tests that erase shared candidates, CVs, jobs, accounts, or retention state must instead be tagged @destructive.
  Scenario: A recruiter permanently deletes a conversation
    Given a recruiter owns an active CV-chat conversation
    When the recruiter deletes that conversation
    Then the deleted conversation is cleared from the active browser state
    And the recruiter can no longer retrieve the deleted conversation
