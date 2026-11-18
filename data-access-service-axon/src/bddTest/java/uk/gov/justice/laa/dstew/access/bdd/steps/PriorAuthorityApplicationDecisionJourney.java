package uk.gov.justice.laa.dstew.access.bdd.steps;

import io.cucumber.java.PendingException;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;

public class PriorAuthorityApplicationDecisionJourney {
  @Given("existing PA application")
  public void existingPAApplication() {}

  @And("I assign a PA application to myself")
  public void iAssignAPAApplicationToMyself() {
    // Write code here that turns the phrase above into concrete actions
    throw new PendingException();
  }

  @When("I decide the PA application as {string}")
  public void iDecideThePAApplicationAs(String decisionOutcome) {
    // Write code here that turns the phrase above into concrete actions
    throw new PendingException();
  }

  @Then("the PA application is eventually decided as {string}")
  public void thePAApplicationIsEventuallyDecidedAs(String decisionOutcome) {
    // Write code here that turns the phrase above into concrete actions
    throw new PendingException();
  }

  @When("I unassign the same PA application")
  public void iUnassignTheSamePAApplication() {
    // Write code here that turns the phrase above into concrete actions
    throw new PendingException();
  }

  @Then("the PA application is eventually unassigned")
  public void thePAApplicationIsEventuallyUnassigned() {
    // Write code here that turns the phrase above into concrete actions
    throw new PendingException();
  }
}
