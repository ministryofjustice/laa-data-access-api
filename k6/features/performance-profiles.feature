Feature: Performance profiles
  Purpose: establish baseline metrics for complete journeys and the provider, Work Queue, and PA phases.
  Actors: Provider, Application Caseworker, and PA Caseworker.
  Parallelisation: K6_PERFORMANCE_VUS virtual users for K6_PERFORMANCE_DURATION; each iteration has unique data.
  Configuration: K6_PERFORMANCE_VUS, K6_PERFORMANCE_DURATION, actor credentials, and optional K6_THRESHOLDS.

  Scenario: Complete journey baseline
    Given Start application draft
    When Upload application document
    And Submit application draft
    And Record manual-grant eligibility
    And Find application in Open Applications
    And Assign application from Work Queue
    And Find application in Personal Queue
    And Grant application
    And Verify application removed from Open Applications
    And List applications
    And Start Prior Authority draft
    And Update Prior Authority draft
    And Upload Prior Authority document
    And Update Prior Authority document type
    And Submit Prior Authority
    And Find PA in Open Applications
    And Assign PA from Work Queue
    And Find PA in Personal Queue
    And Grant PA
    Then Verify PA removed from Open Applications

  Scenario: Provider drafting baseline
    Given Start application draft
    When Upload application document
    Then Submit application draft

  Scenario: Work Queue baseline
    Given Start application draft
    And Upload application document
    And Submit application draft
    And Record manual-grant eligibility
    And Find application in Open Applications
    When Assign application from Work Queue
    And Find application in Personal Queue
    And Grant application
    Then Verify application removed from Open Applications

  Scenario: Prior Authority baseline
    Given Start application draft
    And Upload application document
    And Submit application draft
    And Record manual-grant eligibility
    And Find application in Open Applications
    And Assign application from Work Queue
    And Find application in Personal Queue
    And Grant application
    And Verify application removed from Open Applications
    And List applications
    When Start Prior Authority draft
    And Update Prior Authority draft
    And Upload Prior Authority document
    And Update Prior Authority document type
    And Submit Prior Authority
    And Find PA in Open Applications
    And Assign PA from Work Queue
    And Find PA in Personal Queue
    And Grant PA
    Then Verify PA removed from Open Applications
