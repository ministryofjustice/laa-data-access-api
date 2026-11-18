@smokeTest @prior-authority
Feature: Manage a Prior Authority draft and its supporting documents
  As user
  I want to manage a Prior Authority draft and its supporting documents
  so that I can submit a complete Prior Authority application

  Background: User create draft PA application
   Given existing prior-authority draft

  @updatePA
  Scenario: Update a Prior Authority draft
    When I update the prior authority draft details
    Then the prior authority draft details are eventually updated

  @uploadPADocument
  Scenario: Upload a supporting document to a Prior Authority draft
    When I upload a document to the prior authority draft
    Then the prior authority draft includes the uploaded document

  @replacePADocumentType
  Scenario: Replace the type of a Prior Authority document
    And I upload a document to the prior authority draft
    When I change the prior authority document type to "GATEWAY_EVIDENCE"
    Then the uploaded prior authority document has type "GATEWAY_EVIDENCE"

  @deletePADocument
  Scenario: Delete a supporting document from a Prior Authority draft
    And I upload a document to the prior authority
    When I delete the uploaded prior authority document
    Then the prior authority draft does not include that document

  @submitPA
  Scenario: Submit a Prior Authority draft
    When I submit the prior authority draft
    Then the prior authority is eventually submitted