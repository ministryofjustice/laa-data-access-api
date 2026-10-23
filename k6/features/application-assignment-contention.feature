Feature: Application assignment contention
  Purpose: demonstrate one successful Work Queue assignment and documented conflicts for every other contender.
  Setup: a Provider creates a manual-grant-eligible Application and waits for its Open Applications item.
  Actors: Provider, Application Caseworker, and Contender Caseworker.
  Parallelisation: K6_CONTENTION_VUS virtual users, one assignment attempt each.
  Configuration: K6_CONTENTION_VUS, K6_CONTENDER_*, K6_PROVIDER_*, K6_APPLICATION_CASEWORKER_*,
  K6_POLL_TIMEOUT_MS, K6_POLL_INTERVAL_MS, and optional K6_THRESHOLDS.

  Scenario: Competing caseworkers claim one Application
    Given Start application draft
    And Upload application document
    And Submit application draft
    And Record manual-grant eligibility
    And Find application in Open Applications
    When Assign application from Work Queue
    # Exactly one contender records assigned; every other contender records conflict.
