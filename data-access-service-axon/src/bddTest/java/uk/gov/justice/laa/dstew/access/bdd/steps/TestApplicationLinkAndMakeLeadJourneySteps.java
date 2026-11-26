package uk.gov.justice.laa.dstew.access.bdd.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
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
public class TestApplicationLinkAndMakeLeadJourneySteps {
  private final ApplicationSteps applicationSteps;
  private final ApplicationApi applications;
  private final ApplicationFixtureFactory fixtures;
  private final BddConfiguration configuration;
  private final ScenarioContext context;

  public TestApplicationLinkAndMakeLeadJourneySteps(
      ApplicationSteps applicationSteps,
      ApplicationApi applications,
      ApplicationFixtureFactory fixtures,
      BddConfiguration configuration,
      ScenarioContext context) {
    this.applicationSteps = applicationSteps;
    this.applications = applications;
    this.fixtures = fixtures;
    this.configuration = configuration;
    this.context = context;
  }

  @And("I create standalone application")
  public void iCreateStandaloneApplication() {
    applicationSteps.createDraft();
    applicationSteps.submitDraft();
    applicationSteps.submitted();
  }

  @And("I create application group by linking the standalone application to another application")
  public void iCreateApplicationGroupByLinkingTheStandaloneApplicationToAnotherApplication() {
    UUID another = createSubmittedApplication();
    context.relatedApplicationId("another application", another);
    assertStatus(
        "I create application group",
        applications.link(context.applicationId(), another, null),
        204);
    awaitLinkedApplications(another);
  }

  @When("I link the application to another application")
  public void iLinkTheApplicationToAnotherApplication() {
    UUID linked = createSubmittedApplication();
    context.relatedApplicationId("linked application", linked);
    assertStatus(
        "I link the application to another application",
        applications.link(linked, context.applicationId(), linkedGroupVersion()),
        204);
  }

  @Then("the applications are linked")
  public void theApplicationsAreLinked() {
    awaitLinkedApplications(
        context.relatedApplicationId("another application"),
        context.relatedApplicationId("linked application"));
  }

  @When("I unlink the application from another application")
  public void iUnlinkTheApplicationFromAnotherApplication() {
    assertStatus(
        "I unlink the application from another application",
        applications.unlink(context.applicationId(), linkedGroupVersion()),
        204);
  }

  @Then("the applications are unlinked")
  public void theApplicationsAreUnlinked() {
    awaitLinkedApplications();
  }

  @Then("the remaining applications are still linked")
  public void theRemainingApplicationsAreStillLinked() {
    context.lastResponse(
        applications.awaitApplication(
            context.relatedApplicationId("another application"),
            response ->
                linksOnly(response.body(), context.relatedApplicationId("linked application"))));
  }

  @When("I change the lead of the application group")
  public void iChangeTheLeadOfTheApplicationGroup() {
    assertStatus(
        "I change the lead of the application group",
        applications.makeLead(context.applicationId(), linkedGroupVersion()),
        204);
  }

  @Then("the lead of the application group is changed")
  public void theLeadOfTheApplicationGroupIsChanged() {
    context.lastResponse(
        applications.awaitApplication(
            context.applicationId(), response -> response.body().path("isLead").asBoolean()));
  }

  UUID createSubmittedApplication() {
    UUID applicationId = UUID.randomUUID();
    assertStatus(
        "create an application draft",
        applications.createDraft(
            fixtures.draft(applicationId, UUID.randomUUID(), configuration.providerOfficeCode())),
        201);
    assertStatus("submit the application draft", applications.submitDraft(applicationId), 200, 202);
    applications.awaitSubmitted(applicationId);
    return applicationId;
  }

  String linkedGroupVersion() {
    return applications.get(context.applicationId()).body().path("linkedGroupVersion").asText();
  }

  void awaitLinkedApplications(UUID... expected) {
    context.lastResponse(
        applications.awaitApplication(
            context.applicationId(), response -> linksOnly(response.body(), expected)));
  }

  private static boolean linksOnly(JsonNode application, UUID... expected) {
    JsonNode linkedApplications = application.path("linkedApplications");
    if (linkedApplications.size() != expected.length) return false;
    for (UUID id : expected) {
      boolean found = false;
      for (JsonNode linked : linkedApplications) {
        if (id.toString().equals(linked.path("applicationId").asText())) found = true;
      }
      if (!found) return false;
    }
    return true;
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
