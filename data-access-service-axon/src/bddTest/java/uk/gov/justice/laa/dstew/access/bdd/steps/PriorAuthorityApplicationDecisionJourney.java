package uk.gov.justice.laa.dstew.access.bdd.steps;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class PriorAuthorityApplicationDecisionJourney {
  private final ApplicationSteps applicationSteps;
  private final TestPriorAuthorityApplicationSteps grantedApplicationSteps;
  private final PriorAuthoritySteps priorAuthoritySteps;
  private final WorkListSteps workListSteps;

  public PriorAuthorityApplicationDecisionJourney(
      ApplicationSteps applicationSteps,
      TestPriorAuthorityApplicationSteps grantedApplicationSteps,
      PriorAuthoritySteps priorAuthoritySteps,
      WorkListSteps workListSteps) {
    this.applicationSteps = applicationSteps;
    this.grantedApplicationSteps = grantedApplicationSteps;
    this.priorAuthoritySteps = priorAuthoritySteps;
    this.workListSteps = workListSteps;
  }

  @Given("existing PA application")
  public void existingPAApplication() {
    applicationSteps.createDraft();
    applicationSteps.submitDraft();
    grantedApplicationSteps.theApplicationIsGranted();
    priorAuthoritySteps.createDraft();
    priorAuthoritySteps.submitDraft();
    priorAuthoritySteps.submitted();
  }

  @And("I assign a PA application to myself")
  public void iAssignAPAApplicationToMyself() {
    workListSteps.waitForPriorAuthorityItem("PRIOR_AUTHORITY");
    workListSteps.assignCurrentItem();
    workListSteps.itemAssigned();
  }

  @When("I decide the PA application as {string}")
  public void iDecideThePAApplicationAs(String decisionOutcome) {
    priorAuthoritySteps.decide(decisionOutcome);
  }

  @Then("the PA application is eventually decided as {string}")
  public void thePAApplicationIsEventuallyDecidedAs(String decisionOutcome) {
    priorAuthoritySteps.decided(decisionOutcome);
  }

  @When("I unassign the same PA application")
  public void iUnassignTheSamePAApplication() {
    workListSteps.unassignCurrentItem();
  }

  @Then("the PA application is eventually unassigned")
  public void thePAApplicationIsEventuallyUnassigned() {
    workListSteps.itemUnassigned();
  }
}
