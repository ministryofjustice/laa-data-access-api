package uk.gov.justice.laa.dstew.access.bdd.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import net.serenitybdd.junit5.SerenityJUnit5Extension;
import org.junit.jupiter.api.extension.ExtendWith;
import uk.gov.justice.laa.dstew.access.bdd.client.ApplicationApi;
import uk.gov.justice.laa.dstew.access.bdd.support.ApiResponse;
import uk.gov.justice.laa.dstew.access.bdd.support.ScenarioContext;

@ExtendWith(SerenityJUnit5Extension.class)
public class TestApplicationUpdateAndNoteJourneySteps {
  private static final String NOTE = "Client called to confirm their address";

  private final ApplicationSteps applicationSteps;
  private final ApplicationApi applications;
  private final ScenarioContext context;

  public TestApplicationUpdateAndNoteJourneySteps(
      ApplicationSteps applicationSteps, ApplicationApi applications, ScenarioContext context) {
    this.applicationSteps = applicationSteps;
    this.applications = applications;
    this.context = context;
  }

  @When("I add a note to the application")
  public void iAddANoteToTheApplication() {
    applicationSteps.submitted();
    assertStatus(
        "I add a note to the application",
        applications.addNote(context.applicationId(), NOTE),
        200,
        204);
  }

  @Then("the note is added to the application")
  public void theNoteIsAddedToTheApplication() {
    context.lastResponse(applications.awaitNotes(context.applicationId(), this::containsNote));
  }

  private boolean containsNote(ApiResponse response) {
    for (JsonNode note : response.body().path("notes")) {
      if (NOTE.equals(note.path("note").asText())) return true;
    }
    return false;
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
