@draftApplicationJourney @smoke
Feature: Draft Application Journey
  As a user
  I want to be able to create a draft application, upload supporting documents, and submit it.

  Background: User has created a draft application
    Given I am logged in as "smoke-test"
    And I create a draft application

  Scenario: user can create a draft application, upload supporting documents, and submit it
    And I upload supporting document to the application
    When I submit the draft application
    Then the application is submitted