package uk.gov.justice.laa.dstew.access.bdd.steps;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.UUID;
import net.serenitybdd.junit5.SerenityJUnit5Extension;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.gov.justice.laa.dstew.access.bdd.client.ApplicationApi;
import uk.gov.justice.laa.dstew.access.bdd.config.BddConfiguration;
import uk.gov.justice.laa.dstew.access.bdd.fixtures.ApplicationFixtureFactory;
import uk.gov.justice.laa.dstew.access.bdd.support.ApiResponse;
import uk.gov.justice.laa.dstew.access.bdd.support.ScenarioContext;

@ExtendWith(SerenityJUnit5Extension.class)
public class TestDraftApplicationJourneySteps {

  private final ApplicationApi applications;
  private final ApplicationFixtureFactory fixtures;
  private final BddConfiguration configuration;
  private final ScenarioContext context;

  public TestDraftApplicationJourneySteps(
      ApplicationApi applications,
      ApplicationFixtureFactory fixtures,
      BddConfiguration configuration,
      ScenarioContext context) {
    this.applications = applications;
    this.fixtures = fixtures;
    this.configuration = configuration;
    this.context = context;
  }

  @And("I create a draft application")
  public void iCreateADraftApplication() {
    context.applicationId(UUID.randomUUID());
    context.proceedingId(UUID.randomUUID());
    ApiResponse response =
        applications.createDraft(
            fixtures.draft(
                context.applicationId(),
                context.proceedingId(),
                configuration.providerOfficeCode()));
    context.lastResponse(response);
    if (response.status() != 201)
      throw new AssertionError(
          "Step failed: I create a draft application. Unexpected response: " + response.body());
  }

  @And("I upload supporting document to the application")
  public void iUploadSupportingDocumentToTheApplication() {
    System.out.println("And I upload supporting document to the application");
  }

  @When("I submit the draft application")
  public void iSubmitTheDraftApplication() {
    ApiResponse response = applications.submitDraft(context.applicationId());
    context.lastResponse(response);
    if (!response.hasStatus(200, 202))
      throw new AssertionError(
          "Step failed: I submit the draft application. Unexpected response: " + response.body());
    context.lastResponse(applications.awaitSubmitted(context.applicationId()));
  }

  @Then("the application is submitted")
  public void theApplicationIsSubmitted() {
    System.out.println("Then the application is submitted");
  }
}
