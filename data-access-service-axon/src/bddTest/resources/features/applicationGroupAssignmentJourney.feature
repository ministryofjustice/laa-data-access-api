@smoke @applicationGroupAssignmentJourney
Feature: Linked applications share a caseworker assignment
  As a caseworker
  I want the applications in a linked group to be assigned together
  So that one caseworker handles the whole group

  Background: Two linked applications are ready for manual assessment
    Given I am logged in as "smoke-test"
    And I create a group of two linked applications ready for manual assessment

  @applicationGroupAssign
  Scenario: Assigning one application assigns the whole group
    When I assign the first application of the group to myself
    Then every application in the group is eventually assigned to me

  @applicationGroupUnassign
  Scenario: Unassigning one application unassigns the whole group
    Given I assign the first application of the group to myself
    When I unassign the first application of the group
    Then every application in the group is eventually unassigned

  @applicationGroupJoin
  Scenario: An application that joins an assigned group is assigned to the same caseworker
    Given I assign the first application of the group to myself
    When I link another application that is ready for manual assessment to the group
    Then every application in the group is eventually assigned to me

  @applicationGroupReady
  Scenario: An application that becomes ready in an assigned group is assigned to the same caseworker
    Given I link another application that is not yet ready for manual assessment to the group
    And I assign the first application of the group to myself
    When the other application is recorded as ready for manual assessment
    Then every application in the group is eventually assigned to me
