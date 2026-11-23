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
import uk.gov.justice.laa.dstew.access.bdd.client.WorkListApi;
import uk.gov.justice.laa.dstew.access.bdd.support.ApiResponse;
import uk.gov.justice.laa.dstew.access.bdd.support.ScenarioContext;

@ExtendWith(SerenityJUnit5Extension.class)
public class TestApplicationDecisionJourneySteps {

  private final ApplicationApi applications;
  private final WorkListApi workList;
  private final ScenarioContext context;

  public TestApplicationDecisionJourneySteps(
      ApplicationApi applications, WorkListApi workList, ScenarioContext context) {
    this.applications = applications;
    this.workList = workList;
    this.context = context;
  }

  @And("the application with outcome {string} granted")
  public void existingApplicationWithOutcomeManualGranted(String outcome) {
    String normalized = outcome.toUpperCase();
    ApiResponse response = applications.recordAutoGrantOutcome(context.applicationId(), normalized);
    context.lastResponse(response);
    if (!response.hasStatus(200, 204))
      assertEquals(
          200,
          response.status(),
          "Step failed: the application with outcome \""
              + outcome
              + "\" granted. Unexpected response: "
              + response.body());
    context.lastResponse(applications.awaitAutoGrantOutcome(context.applicationId(), normalized));
  }

  @And("I assign an application to myself")
  public void iAssignAnApplicationToMyself() {
    JsonNode item = workList.awaitUnassignedItem("APPLICATION", context.applicationId());
    UUID workItemId = UUID.fromString(item.path("itemId").asText());
    context.workItemId(workItemId);
    context.workItemType("APPLICATION");
    context.assignmentVersion(item.path("assignmentVersion").asLong());

    ApiResponse response = workList.assign(workItemId, context.assignmentVersion());
    context.lastResponse(response);
    assertEquals(
        200,
        response.status(),
        "Step failed: I assign an application to myself. Unexpected response: " + response.body());

    JsonNode assigned = workList.awaitAssignedItem(workItemId);
    context.assignmentVersion(assigned.path("assignmentVersion").asLong());
  }

  @When("I unassign the same application")
  public void iUnassignTheSameApplication() {
    ApiResponse response = workList.unassign(context.workItemId(), context.assignmentVersion());
    context.lastResponse(response);
    assertEquals(
        200,
        response.status(),
        "Step failed: I unassign the same application. Unexpected response: " + response.body());
  }

  @Then("the application is eventually unassigned")
  public void theApplicationIsEventuallyUnassigned() {
    JsonNode item = workList.awaitUnassignedItem(context.workItemType(), context.workItemId());
    context.assignmentVersion(item.path("assignmentVersion").asLong());
  }
}
