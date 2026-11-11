package uk.gov.justice.laa.dstew.access.bdd.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.bdd.client.PriorAuthorityApi;
import uk.gov.justice.laa.dstew.access.bdd.fixtures.PriorAuthorityFixtureFactory;
import uk.gov.justice.laa.dstew.access.bdd.support.ApiResponse;
import uk.gov.justice.laa.dstew.access.bdd.support.ScenarioContext;

public final class PriorAuthoritySteps {
  private static final String DOCUMENT_FILE_NAME = "smoke-prior-authority-evidence.pdf";
  private static final byte[] DOCUMENT_CONTENTS =
      "%PDF-1.4\nBDD prior authority evidence\n%%EOF".getBytes(StandardCharsets.UTF_8);

  private final PriorAuthorityApi priorAuthorities;
  private final PriorAuthorityFixtureFactory fixtures;
  private final ScenarioContext context;

  public PriorAuthoritySteps(
      PriorAuthorityApi priorAuthorities,
      PriorAuthorityFixtureFactory fixtures,
      ScenarioContext context) {
    this.priorAuthorities = priorAuthorities;
    this.fixtures = fixtures;
    this.context = context;
  }

  @When("I create a valid prior authority draft for the application")
  public void createDraft() {
    ApiResponse response = priorAuthorities.createDraft(fixtures.draft(context.applicationId()));
    assertStatus("I create a valid prior authority draft for the application", response, 201, 202);
    context.lastResponse(response);
    context.priorAuthorityId(UUID.fromString(response.body().path("priorAuthorityId").asText()));
  }

  @When("I upload a document to the prior authority")
  public void uploadDocument() {
    ApiResponse response =
        priorAuthorities.uploadDocument(
            context.priorAuthorityId(), DOCUMENT_FILE_NAME, DOCUMENT_CONTENTS);
    assertAndRecord("I upload a document to the prior authority", response, 201);
    assertEquals(DOCUMENT_FILE_NAME, response.body().path("fileName").asText());
    assertNotNull(response.body().path("documentId").asText(null));
  }

  @When("I submit the prior authority draft")
  public void submitDraft() {
    assertAndRecord(
        "I submit the prior authority draft",
        priorAuthorities.submitDraft(context.priorAuthorityId()),
        200,
        202);
  }

  @Then("the prior authority is eventually submitted")
  public void submitted() {
    ApiResponse response = priorAuthorities.awaitSubmitted(context.priorAuthorityId());
    context.lastResponse(response);
    context.priorAuthorityVersion(response.body().path("priorAuthorityVersion").asLong());
  }

  @When("I decide the prior authority as {string}")
  public void decide(String decision) {
    if (!"GRANTED".equals(decision))
      throw new IllegalArgumentException("Only GRANTED is currently supported");
    ApiResponse latest = priorAuthorities.get(context.priorAuthorityId());
    context.priorAuthorityVersion(latest.body().path("priorAuthorityVersion").asLong());
    assertAndRecord(
        "I decide the prior authority as \"" + decision + "\"",
        priorAuthorities.decide(
            context.priorAuthorityId(), fixtures.grantDecision(context.priorAuthorityVersion())),
        204);
  }

  @Then("the prior authority is eventually decided as {string}")
  public void decided(String decision) {
    context.lastResponse(priorAuthorities.awaitDecision(context.priorAuthorityId(), decision));
  }

  private void assertAndRecord(String step, ApiResponse response, int... statuses) {
    context.lastResponse(response);
    assertStatus(step, response, statuses);
  }

  private static void assertStatus(String step, ApiResponse response, int... statuses) {
    if (!response.hasStatus(statuses))
      assertEquals(
          statuses[0],
          response.status(),
          "Step failed: " + step + ". Unexpected response: " + response.body());
  }
}
