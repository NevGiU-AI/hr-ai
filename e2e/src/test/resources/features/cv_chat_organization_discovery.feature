@cv-chat @provider @organization-discovery
Feature: Organization-wide candidate discovery
  Discovery proposes a shortlist; only explicitly confirmed candidates can be compared.

  Scenario: A recruiter compares a subset of an organization shortlist
    Given a recruiter is ready to discover candidates in CV chat
    When the recruiter discovers candidates using the configured organization question
    Then the shortlist awaits explicit confirmation
    When the recruiter confirms two of the proposed candidates
    And the recruiter asks the configured comparison question
    Then the comparison cites both confirmed candidates and no others
