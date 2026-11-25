@smoke @applicationLinkAndMakeLeadJourney
Feature: Applications can be linked and a lead can be changed
  As a user
  I want to be able to link applications and change the lead of application group.

  Background: User creates application group by linking two applications
    Given I am logged in as "smoke-test"
    And I create standalone application
    And I create application group by linking the standalone application to another application

  @applicationLink
  Scenario: User can link applications
    When I link the application to another application
    Then the applications are linked

  @applicationUnlink
  Scenario: User can unlink applications
    When I unlink the application from another application
    Then the applications are unlinked

  @applicationChangeLead
  Scenario: User can change the lead of an application group
    When I change the lead of the application group
    Then the lead of the application group is changed