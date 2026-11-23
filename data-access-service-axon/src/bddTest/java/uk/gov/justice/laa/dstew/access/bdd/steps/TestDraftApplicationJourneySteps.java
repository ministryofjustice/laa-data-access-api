package uk.gov.justice.laa.dstew.access.bdd.steps;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.junit5.SerenityJUnit5Extension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SerenityJUnit5Extension.class)
public class TestDraftApplicationJourneySteps {

  @And("I create a draft application")
  public void iCreateADraftApplication() {
    System.out.println("And I create a draft application");
  }

  @And("I upload supporting document to the application")
  public void iUploadSupportingDocumentToTheApplication() {
    System.out.println("And I upload supporting document to the application");
  }

  @When("I submit the draft application")
  public void iSubmitTheDraftApplication() {
    System.out.println("When I submit the draft application");
  }

  @Then("the application is submitted")
  public void theApplicationIsSubmitted() {
    System.out.println("Then the application is submitted");
  }
}
