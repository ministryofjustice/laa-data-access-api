@smoke @applicationDecisionJourney
Feature: caseworker makes decision on applications 'Manual' granted
  As a caseworker
  I want to be able to make a decision on an application that has been granted 'Manual'
  So the prior authority can be created and submitted

  Background: applications out come is 'Manual' granted
    Given I am logged in as "smoke-test"
    And I create a draft application
    And I submit the draft application
    And the application with outcome 'Manual' granted

  @applicationDecision
  Scenario Outline: caseworker can assign themselves to the application and make a decision
    And I assign an application to myself
    When I decide the application as "<decision>"
    Then the application is eventually decided as "<decision>"
    Examples:
      | decision |
      | GRANTED  |
      | REFUSED  |


  @applicationUnassign
  Scenario: caseworker can unassign themselves from the application
    And I assign an application to myself
    When I unassign the same application
    Then the application is eventually unassigned