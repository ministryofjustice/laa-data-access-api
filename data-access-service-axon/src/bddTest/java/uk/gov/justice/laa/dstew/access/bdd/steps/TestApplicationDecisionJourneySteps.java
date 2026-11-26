package uk.gov.justice.laa.dstew.access.bdd.steps;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.junit5.SerenityJUnit5Extension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SerenityJUnit5Extension.class)
public class TestApplicationDecisionJourneySteps {
  private static final String APPLICATION_ITEM_TYPE = "APPLICATION";

  private final ApplicationSteps applicationSteps;
  private final WorkListSteps workListSteps;

  public TestApplicationDecisionJourneySteps(
      ApplicationSteps applicationSteps, WorkListSteps workListSteps) {
    this.applicationSteps = applicationSteps;
    this.workListSteps = workListSteps;
  }

  @And("the application with outcome {string} granted")
  public void existingApplicationWithOutcomeManualGranted(String outcome) {
    applicationSteps.submitted();
    applicationSteps.recordAutoGrantOutcome(outcome.toUpperCase());
    applicationSteps.readyForManualAssessment();
  }

  @And("I assign an application to myself")
  public void iAssignAnApplicationToMyself() {
    workListSteps.waitForApplicationItem(APPLICATION_ITEM_TYPE);
    workListSteps.assignCurrentItem();
    workListSteps.itemAssigned();
  }

  @When("I unassign the same application")
  public void iUnassignTheSameApplication() {
    workListSteps.unassignCurrentItem();
  }

  @Then("the application is eventually unassigned")
  public void theApplicationIsEventuallyUnassigned() {
    workListSteps.itemUnassigned();
  }
}
