@cv-chat @provider @named-discovery
Feature: Named candidate discovery

  Scenario: A recruiter resolves an ambiguous name before confirming scope
    Given a recruiter opens CV chat for named discovery
    When the recruiter submits the configured ambiguous name request
    Then the matches appear under two requested names without any selected candidates
    When the recruiter selects one card from each name group and confirms scope
    Then only the two selected candidate cards remain in the active scope after reload
