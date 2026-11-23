package uk.gov.justice.laa.dstew.access.bdd.steps;

import io.cucumber.java.PendingException;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.junit5.SerenityJUnit5Extension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SerenityJUnit5Extension.class)
public class TestApplicationDecisionJourneySteps {

  @And("the application with outcome {string} granted")
  public void existingApplicationWithOutcomeManualGranted(String outcome) {
    // Write code here that turns the phrase above into concrete actions
    throw new PendingException();
  }

  @And("I assign an application to myself")
  public void iAssignAnApplicationToMyself() {
    // Write code here that turns the phrase above into concrete actions
    throw new PendingException();
  }

  @When("I unassign the same application")
  public void iUnassignTheSameApplication() {
    // Write code here that turns the phrase above into concrete actions
    throw new PendingException();
  }

  @Then("the application is eventually unassigned")
  public void theApplicationIsEventuallyUnassigned() {
    // Write code here that turns the phrase above into concrete actions
    throw new PendingException();
  }
}
