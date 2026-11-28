@cv-chat @security @tenant-isolation
Feature: CV-chat tenant isolation
  Scenario: A recruiter from another organization cannot read a disposable conversation or its history
    Given a recruiter owns an active CV-chat conversation
    When the recruiter asks for the CV extraction status
    Then the conversation and its history are available only to the owner in the two-organization fixture
