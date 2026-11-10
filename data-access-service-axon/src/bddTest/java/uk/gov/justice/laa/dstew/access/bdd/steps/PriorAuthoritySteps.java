package uk.gov.justice.laa.dstew.access.bdd.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.bdd.client.PriorAuthorityApi;
import uk.gov.justice.laa.dstew.access.bdd.fixtures.PriorAuthorityFixtureFactory;
import uk.gov.justice.laa.dstew.access.bdd.support.ApiResponse;
import uk.gov.justice.laa.dstew.access.bdd.support.ScenarioContext;

public final class PriorAuthoritySteps {
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

  @Given("I create a valid prior authority draft for the application")
  public void createDraft() {
    ApiResponse response = priorAuthorities.createDraft(fixtures.draft(context.applicationId()));
    assertStatus(response, 201, 202);
    context.lastResponse(response);
    context.priorAuthorityId(UUID.fromString(response.body().path("priorAuthorityId").asText()));
  }

  @When("I submit the prior authority draft")
  public void submitDraft() {
    assertAndRecord(priorAuthorities.submitDraft(context.priorAuthorityId()), 200, 202);
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
        priorAuthorities.decide(
            context.priorAuthorityId(), fixtures.grantDecision(context.priorAuthorityVersion())),
        204);
  }

  @Then("the prior authority is eventually decided as {string}")
  public void decided(String decision) {
    context.lastResponse(priorAuthorities.awaitDecision(context.priorAuthorityId(), decision));
  }

  private void assertAndRecord(ApiResponse response, int... statuses) {
    context.lastResponse(response);
    assertStatus(response, statuses);
  }

  private static void assertStatus(ApiResponse response, int... statuses) {
    if (!response.hasStatus(statuses))
      assertEquals(statuses[0], response.status(), "Unexpected response: " + response.body());
  }
}
