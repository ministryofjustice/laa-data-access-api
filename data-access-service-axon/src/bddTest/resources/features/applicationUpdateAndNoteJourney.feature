@smokeTest @applicationUpdateAndNoteJourney
Feature: Application Update and Note Journey
  As a user
  I want to be able to update an existing application and add notes to it.

  Background: User
    Given I am logged in as "smoke-test"
    And I create a draft application
    And I submit the draft application


  @applicationNotes
  Scenario: User can add notes to existing application
    When I add a note to the application
    Then the note is added to the application


  @applicationUpdate
  Scenario: User can update the application
    When I update the application
    Then the application is updated