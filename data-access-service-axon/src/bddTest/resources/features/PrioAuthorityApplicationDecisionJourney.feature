@smokeTest @PAApplicationDecisionJourney
Feature: caseworker makes decision on PA applications
  As a caseworker
  I want to be able to make a decision on PA application
  So decision is made the PA application

  Background: caseworker has existing PA application to make decision on
    Given I am logged in as "smoke-test"
    Given existing PA application

  @PAApplicationDecision
  Scenario Outline: caseworker can assign themselves to the PA application and make a decision
    And I assign a PA application to myself
    When I decide the PA application as "<decision>"
    Then the PA application is eventually decided as "<decision>"
    Examples:
      | decision |
      | GRANTED  |
      | REFUSED  |


  @PAApplicationUnassign
  Scenario: caseworker can unassign themselves from the PA application
    And I assign a PA application to myself
    When I unassign the same PA application
    Then the PA application is eventually unassigned