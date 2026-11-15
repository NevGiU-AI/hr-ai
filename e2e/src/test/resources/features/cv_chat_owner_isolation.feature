@cv-chat @security @smoke
Feature: CV-chat conversation ownership
  Conversation history belongs to the authenticated user who created it, even when another authorized user belongs
  to the same organization.

  Scenario: An administrator cannot read a recruiter's conversation
    Given a recruiter owns an active CV-chat conversation
    And an administrator belongs to the same organization
    When the administrator requests the recruiter's conversation
    Then the response status is 404
    And no conversation metadata is disclosed
