package uk.gov.justice.laa.dstew.access.bdd.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.serenitybdd.junit5.SerenityJUnit5Extension;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.gov.justice.laa.dstew.access.bdd.client.ApplicationApi;
import uk.gov.justice.laa.dstew.access.bdd.client.WorkListApi;
import uk.gov.justice.laa.dstew.access.bdd.support.ApiResponse;
import uk.gov.justice.laa.dstew.access.bdd.support.ScenarioContext;

@ExtendWith(SerenityJUnit5Extension.class)
public class TestApplicationGroupAssignmentJourneySteps {
  private static final String APPLICATION_ITEM_TYPE = "APPLICATION";

  private final ApplicationSteps applicationSteps;
  private final WorkListSteps workListSteps;
  private final TestApplicationLinkAndMakeLeadJourneySteps linkSteps;
  private final ApplicationApi applications;
  private final WorkListApi workList;
  private final ScenarioContext context;
  private final List<UUID> groupMembers = new ArrayList<>();

  public TestApplicationGroupAssignmentJourneySteps(
      ApplicationSteps applicationSteps,
      WorkListSteps workListSteps,
      TestApplicationLinkAndMakeLeadJourneySteps linkSteps,
      ApplicationApi applications,
      WorkListApi workList,
      ScenarioContext context) {
    this.applicationSteps = applicationSteps;
    this.workListSteps = workListSteps;
    this.linkSteps = linkSteps;
    this.applications = applications;
    this.workList = workList;
    this.context = context;
  }

  @And("I create a group of two linked applications ready for manual assessment")
  public void iCreateAGroupOfTwoLinkedApplicationsReadyForManualAssessment() {
    applicationSteps.createDraft();
    applicationSteps.submitDraft();
    applicationSteps.submitted();
    markReadyForManualAssessment(context.applicationId());
    UUID second = linkSteps.createSubmittedApplication();
    markReadyForManualAssessment(second);
    assertStatus(
        "I create a group of two linked applications",
        applications.link(context.applicationId(), second, null),
        204);
    linkSteps.awaitLinkedApplications(second);
    groupMembers.add(context.applicationId());
    groupMembers.add(second);
  }

  @When("I assign the first application of the group to myself")
  public void iAssignTheFirstApplicationOfTheGroupToMyself() {
    workListSteps.waitForApplicationItem(APPLICATION_ITEM_TYPE);
    workListSteps.assignCurrentItem();
    workListSteps.itemAssigned();
  }

  @When("I unassign the first application of the group")
  public void iUnassignTheFirstApplicationOfTheGroup() {
    workListSteps.unassignCurrentItem();
  }

  @When("I link another application that is ready for manual assessment to the group")
  public void iLinkAnotherApplicationThatIsReadyForManualAssessmentToTheGroup() {
    UUID other = linkSteps.createSubmittedApplication();
    markReadyForManualAssessment(other);
    linkToGroup(other);
  }

  @When("I link another application that is not yet ready for manual assessment to the group")
  public void iLinkAnotherApplicationThatIsNotYetReadyForManualAssessmentToTheGroup() {
    linkToGroup(linkSteps.createSubmittedApplication());
  }

  @When("the other application is recorded as ready for manual assessment")
  public void theOtherApplicationIsRecordedAsReadyForManualAssessment() {
    markReadyForManualAssessment(context.relatedApplicationId("other application"));
  }

  @Then("every application in the group is eventually assigned to me")
  public void everyApplicationInTheGroupIsEventuallyAssignedToMe() {
    groupMembers.forEach(workList::awaitAssignedItem);
  }

  @Then("every application in the group is eventually unassigned")
  public void everyApplicationInTheGroupIsEventuallyUnassigned() {
    groupMembers.forEach(id -> workList.awaitUnassignedItem(APPLICATION_ITEM_TYPE, id));
  }

  private void linkToGroup(UUID other) {
    context.relatedApplicationId("other application", other);
    assertStatus(
        "I link another application to the group",
        applications.link(other, context.applicationId(), linkSteps.linkedGroupVersion()),
        204);
    groupMembers.add(other);
    linkSteps.awaitLinkedApplications(
        groupMembers.stream()
            .filter(id -> !id.equals(context.applicationId()))
            .toArray(UUID[]::new));
  }

  private void markReadyForManualAssessment(UUID applicationId) {
    assertStatus(
        "record the auto-grant outcome as MANUAL",
        applications.recordAutoGrantOutcome(applicationId, "MANUAL"),
        200,
        204);
    applications.awaitAutoGrantOutcome(applicationId, "MANUAL");
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
