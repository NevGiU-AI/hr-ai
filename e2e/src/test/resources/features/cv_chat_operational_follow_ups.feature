@cv-chat @conversation @operational-follow-ups
Feature: CV-chat operational follow-ups
  Scenario: A recruiter reviews document processing and saved evaluations in one chat
    Given a recruiter owns an active CV-chat conversation
    When the recruiter asks about extraction, ingestion warnings, and stored evaluation scores
    Then the operational replies stay in the same confirmed conversation without CV citations
