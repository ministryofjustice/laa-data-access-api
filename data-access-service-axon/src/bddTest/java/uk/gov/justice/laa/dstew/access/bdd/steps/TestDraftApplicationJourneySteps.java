package uk.gov.justice.laa.dstew.access.bdd.steps;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.junit5.SerenityJUnit5Extension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SerenityJUnit5Extension.class)
public class TestDraftApplicationJourneySteps {
  private final ApplicationSteps applicationSteps;

  public TestDraftApplicationJourneySteps(ApplicationSteps applicationSteps) {
    this.applicationSteps = applicationSteps;
  }

  @And("I create a draft application")
  public void iCreateADraftApplication() {
    applicationSteps.createDraft();
  }

  @And("I upload supporting document to the application")
  public void iUploadSupportingDocumentToTheApplication() {
    applicationSteps.uploadDocument();
  }

  @When("I submit the draft application")
  public void iSubmitTheDraftApplication() {
    applicationSteps.submitDraft();
  }

  @Then("the application is submitted")
  public void theApplicationIsSubmitted() {
    applicationSteps.submitted();
  }
}
