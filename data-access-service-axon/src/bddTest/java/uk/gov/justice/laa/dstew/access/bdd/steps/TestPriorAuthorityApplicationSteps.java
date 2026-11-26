package uk.gov.justice.laa.dstew.access.bdd.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import uk.gov.justice.laa.dstew.access.bdd.client.PriorAuthorityApi;
import uk.gov.justice.laa.dstew.access.bdd.fixtures.PriorAuthorityFixtureFactory;
import uk.gov.justice.laa.dstew.access.bdd.support.ApiResponse;
import uk.gov.justice.laa.dstew.access.bdd.support.ScenarioContext;

public class TestPriorAuthorityApplicationSteps {
  private static final String UPDATED_JUSTIFICATION = "Revised interpreter costs for proceedings";

  private final ApplicationSteps applicationSteps;
  private final WorkListSteps workListSteps;
  private final PriorAuthoritySteps priorAuthoritySteps;
  private final PriorAuthorityApi priorAuthorities;
  private final PriorAuthorityFixtureFactory fixtures;
  private final ScenarioContext context;

  public TestPriorAuthorityApplicationSteps(
      ApplicationSteps applicationSteps,
      WorkListSteps workListSteps,
      PriorAuthoritySteps priorAuthoritySteps,
      PriorAuthorityApi priorAuthorities,
      PriorAuthorityFixtureFactory fixtures,
      ScenarioContext context) {
    this.applicationSteps = applicationSteps;
    this.workListSteps = workListSteps;
    this.priorAuthoritySteps = priorAuthoritySteps;
    this.priorAuthorities = priorAuthorities;
    this.fixtures = fixtures;
    this.context = context;
  }

  @And("The application is granted")
  public void theApplicationIsGranted() {
    applicationSteps.submitted();
    applicationSteps.recordAutoGrantOutcome("MANUAL");
    applicationSteps.readyForManualAssessment();
    workListSteps.waitForApplicationItem("APPLICATION");
    workListSteps.assignCurrentItem();
    workListSteps.itemAssigned();
    applicationSteps.decide("GRANTED");
    applicationSteps.decided("GRANTED");
  }

  @And("I create a prior authority draft for the application")
  public void iCreateAPriorAuthorityDraftForTheApplication() {
    priorAuthoritySteps.createDraft();
  }

  @When("I update the prior authority draft details")
  public void iUpdateThePriorAuthorityDraftDetails() {
    assertStatus(
        "I update the prior authority draft details",
        priorAuthorities.update(
            context.priorAuthorityId(), fixtures.updatedDraft(UPDATED_JUSTIFICATION)),
        204);
  }

  @Then("the prior authority draft details are eventually updated")
  public void thePriorAuthorityDraftDetailsAreEventuallyUpdated() {
    context.lastResponse(
        priorAuthorities.awaitPriorAuthority(
            context.priorAuthorityId(),
            response ->
                UPDATED_JUSTIFICATION.equals(response.body().path("justification").asText())));
  }

  @When("I upload a document to the prior authority draft")
  public void iUploadADocumentToThePriorAuthorityDraft() {
    priorAuthoritySteps.uploadDocument();
  }

  @Then("the prior authority draft includes the uploaded document")
  public void thePriorAuthorityDraftIncludesTheUploadedDocument() {
    context.lastResponse(
        priorAuthorities.awaitPriorAuthority(
            context.priorAuthorityId(), response -> uploadedDocument(response) != null));
  }

  @When("I change the prior authority document type to {string}")
  public void iChangeThePriorAuthorityDocumentTypeTo(String documentType) {
    assertStatus(
        "I change the prior authority document type to \"" + documentType + "\"",
        priorAuthorities.updateDocumentType(
            context.priorAuthorityId(), context.documentId(), documentType),
        200);
  }

  @Then("the uploaded prior authority document has type {string}")
  public void theUploadedPriorAuthorityDocumentHasType(String documentType) {
    context.lastResponse(
        priorAuthorities.awaitPriorAuthority(
            context.priorAuthorityId(),
            response ->
                uploadedDocument(response) != null
                    && documentType.equals(
                        uploadedDocument(response).path("documentType").asText())));
  }

  @When("I delete the uploaded prior authority document")
  public void iDeleteTheUploadedPriorAuthorityDocument() {
    assertStatus(
        "I delete the uploaded prior authority document",
        priorAuthorities.deleteDocument(context.priorAuthorityId(), context.documentId()),
        204);
  }

  @Then("the prior authority draft does not include that document")
  public void thePriorAuthorityDraftDoesNotIncludeThatDocument() {
    context.lastResponse(
        priorAuthorities.awaitPriorAuthority(
            context.priorAuthorityId(), response -> uploadedDocument(response) == null));
  }

  private JsonNode uploadedDocument(ApiResponse response) {
    for (JsonNode document : response.body().path("uploadedDocuments")) {
      if (context.documentId().toString().equals(document.path("documentId").asText()))
        return document;
    }
    return null;
  }

  private void assertStatus(String step, ApiResponse response, int... statuses) {
    context.lastResponse(response);
    if (!response.hasStatus(statuses))
      assertEquals(
          statuses[0],
          response.status(),
          "Step failed: " + step + ". Unexpected response: " + response.body());
  }
}
