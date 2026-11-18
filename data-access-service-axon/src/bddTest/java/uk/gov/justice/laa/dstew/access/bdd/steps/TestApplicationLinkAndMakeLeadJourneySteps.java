package uk.gov.justice.laa.dstew.access.bdd.steps;

import io.cucumber.java.PendingException;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.junit5.SerenityJUnit5Extension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SerenityJUnit5Extension.class)
public class TestApplicationLinkAndMakeLeadJourneySteps {

  @And("I create standalone application")
  public void iCreateStandaloneApplication() {
    // Write code here that turns the phrase above into concrete actions
    throw new PendingException();
  }

  @And("I create application group by linking the standalone application to another application")
  public void iCreateApplicationGroupByLinkingTheStandaloneApplicationToAnotherApplication() {
    // Write code here that turns the phrase above into concrete actions
    throw new PendingException();
  }

  @When("I link the application to another application")
  public void iLinkTheApplicationToAnotherApplication() {
    // Write code here that turns the phrase above into concrete actions
    throw new PendingException();
  }

  @Then("the applications are linked")
  public void theApplicationsAreLinked() {
    // Write code here that turns the phrase above into concrete actions
    throw new PendingException();
  }

  @When("I unlink the application from another application")
  public void iUnlinkTheApplicationFromAnotherApplication() {
    // Write code here that turns the phrase above into concrete actions
    throw new PendingException();
  }

  @Then("the applications are unlinked")
  public void theApplicationsAreUnlinked() {
    // Write code here that turns the phrase above into concrete actions
    throw new PendingException();
  }

  @When("I change the lead of the application group")
  public void iChangeTheLeadOfTheApplicationGroup() {
    // Write code here that turns the phrase above into concrete actions
    throw new PendingException();
  }

  @Then("the lead of the application group is changed")
  public void theLeadOfTheApplicationGroupIsChanged() {
    // Write code here that turns the phrase above into concrete actions
    throw new PendingException();
  }
}
