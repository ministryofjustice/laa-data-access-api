Feature: Complete smoke journey
  Purpose: grant one manual-grant-eligible Application and its Prior Authority.
  Actors: Provider, Application Caseworker, and PA Caseworker (which may share credentials).
  Parallelisation: one virtual user and one iteration.
  Configuration: K6_PROVIDER_*, K6_APPLICATION_CASEWORKER_*, optional K6_PA_CASEWORKER_*,
  K6_POLL_TIMEOUT_MS, K6_POLL_INTERVAL_MS, and optional K6_THRESHOLDS.

  Scenario: Provider to PA grant journey
    Given Start application draft
    When Upload application document
    And Submit application draft
    And Record manual-grant eligibility
    Then Find application in Open Applications
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
    Then Find PA in Open Applications
    And Assign PA from Work Queue
    And Find PA in Personal Queue
    And Grant PA
    And Verify PA removed from Open Applications
