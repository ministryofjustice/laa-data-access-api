package uk.gov.justice.laa.dstew.access.bdd.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.bdd.client.WorkListApi;
import uk.gov.justice.laa.dstew.access.bdd.support.ApiResponse;
import uk.gov.justice.laa.dstew.access.bdd.support.ScenarioContext;

public final class WorkListSteps {
  private final WorkListApi workList;
  private final ScenarioContext context;

  public WorkListSteps(WorkListApi workList, ScenarioContext context) {
    this.workList = workList;
    this.context = context;
  }

  @When("I wait for an unassigned {string} work-list item for the application")
  public void waitForApplicationItem(String type) {
    selectItem(type, context.applicationId());
  }

  @When("I wait for an unassigned {string} work-list item for the prior authority")
  public void waitForPriorAuthorityItem(String type) {
    selectItem(type, context.priorAuthorityId());
  }

  @When("I assign the current work-list item to myself")
  public void assignCurrentItem() {
    ApiResponse response = workList.assign(context.workItemId(), context.assignmentVersion());
    context.lastResponse(response);
    assertEquals(
        200,
        response.status(),
        "Step failed: I assign the current work-list item to myself. Unexpected response: "
            + response.body());
  }

  @Then("the current work-list item is eventually assigned to me")
  public void itemAssigned() {
    JsonNode item = workList.awaitAssignedItem(context.workItemId());
    context.assignmentVersion(item.path("assignmentVersion").asLong());
  }

  @When("I unassign the current work-list item")
  public void unassignCurrentItem() {
    ApiResponse response = workList.unassign(context.workItemId(), context.assignmentVersion());
    context.lastResponse(response);
    assertEquals(
        200,
        response.status(),
        "Step failed: I unassign the current work-list item. Unexpected response: "
            + response.body());
  }

  @Then("the current work-list item is eventually unassigned")
  public void itemUnassigned() {
    JsonNode item = workList.awaitUnassignedItem(context.workItemType(), context.workItemId());
    context.assignmentVersion(item.path("assignmentVersion").asLong());
  }

  @Then("the prior authority is eventually absent from the work list")
  public void priorAuthorityIsAbsent() {
    workList.awaitItemAbsent(context.priorAuthorityId());
  }

  private void selectItem(String type, UUID itemId) {
    JsonNode item = workList.awaitUnassignedItem(type, itemId);
    context.workItemId(UUID.fromString(item.path("itemId").asText()));
    context.workItemType(type);
    context.assignmentVersion(item.path("assignmentVersion").asLong());
  }
}
