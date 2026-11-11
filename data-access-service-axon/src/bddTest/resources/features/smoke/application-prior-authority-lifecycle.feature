@smoke @application @prior-authority
Feature: Manual application and prior authority lifecycle

  Scenario: A caseworker grants a manual application then decides its prior authority
    Given I am logged in as "smoke-test"
    When I create a valid application draft
    And I submit the application draft
    Then the application is eventually submitted
    When the application auto-grant outcome is recorded as "MANUAL"
    Then the application is eventually ready for manual assessment
    When I wait for an unassigned "APPLICATION" work-list item for the application
    And I assign the current work-list item to myself
    Then the current work-list item is eventually assigned to me
    When I decide the application as "GRANTED"
    Then the application is eventually decided as "GRANTED"
    Given I am logged in as "smoke-provider-1"
    Then the application appears in the search list with the current provider office code
    When I create a valid prior authority draft for the application
    And I submit the prior authority draft
    Then the prior authority is eventually submitted
    Given I am logged in as "smoke-test"
    When I wait for an unassigned "PRIOR_AUTHORITY" work-list item for the prior authority
    And I assign the current work-list item to myself
    Then the current work-list item is eventually assigned to me
    When I decide the prior authority as "GRANTED"
    Then the prior authority is eventually decided as "GRANTED"

