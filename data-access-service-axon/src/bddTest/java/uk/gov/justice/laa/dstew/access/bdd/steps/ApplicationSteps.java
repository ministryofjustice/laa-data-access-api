package uk.gov.justice.laa.dstew.access.bdd.steps;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.java.en_scouse.Youseknowlikewhen;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.bdd.client.ApplicationApi;
import uk.gov.justice.laa.dstew.access.bdd.config.BddConfiguration;
import uk.gov.justice.laa.dstew.access.bdd.fixtures.ApplicationFixtureFactory;
import uk.gov.justice.laa.dstew.access.bdd.support.ApiResponse;
import uk.gov.justice.laa.dstew.access.bdd.support.ScenarioContext;

public final class ApplicationSteps {
  private static final String DOCUMENT_FILE_NAME = "smoke-application-evidence.pdf";
  private static final byte[] DOCUMENT_CONTENTS =
      "%PDF-1.4\nBDD application evidence\n%%EOF".getBytes(StandardCharsets.UTF_8);

  private final ApplicationApi applications;
  private final ApplicationFixtureFactory fixtures;
  private final BddConfiguration configuration;
  private final ScenarioContext context;

  public ApplicationSteps(
      ApplicationApi applications,
      ApplicationFixtureFactory fixtures,
      BddConfiguration configuration,
      ScenarioContext context) {
    this.applications = applications;
    this.fixtures = fixtures;
    this.configuration = configuration;
    this.context = context;
  }

  @Youseknowlikewhen("I create a valid application draft")
  public void createDraft() {
    context.applicationId(UUID.randomUUID());
    context.proceedingId(UUID.randomUUID());
    assertStatus(
        context,
        "I create a valid application draft",
        applications.createDraft(
            fixtures.draft(
                context.applicationId(),
                context.proceedingId(),
                configuration.providerOfficeCode())),
        201);
  }

  @When("I upload a document to the application")
  public void uploadDocument() {
    ApiResponse response =
        applications.uploadDocument(context.applicationId(), DOCUMENT_FILE_NAME, DOCUMENT_CONTENTS);
    assertStatus(context, "I upload a document to the application", response, 201);
    assertEquals(DOCUMENT_FILE_NAME, response.body().path("fileName").asText());
    assertNotNull(response.body().path("documentId").asText(null));
  }

  @When("I submit the application draft")
  public void submitDraft() {
    assertStatus(
        context,
        "I submit the application draft",
        applications.submitDraft(context.applicationId()),
        200,
        202);
  }

  @Then("the application is eventually submitted")
  public void submitted() {
    ApiResponse response = applications.awaitSubmitted(context.applicationId());
    context.lastResponse(response);
    context.applicationVersion(response.body().path("version").asLong());
  }

  @Then("the application appears in the search list with the current provider office code")
  public void appearsInSearchListWithCurrentProviderOfficeCode() {
    ApiResponse response = applications.searchByReference(reference());
    assertStatus(context, "the application appears in the search list", response, 200);
    JsonNode application = findApplication(response.body());
    assertNotNull(
        application,
        "The application is not present in the provider-filtered search response: "
            + response.body());
    assertEquals(context.officeCode(), application.path("officeCode").asText());
  }

  @When("the application auto-grant outcome is recorded as {string}")
  public void recordAutoGrantOutcome(String outcome) {
    assertStatus(
        context,
        "the application auto-grant outcome is recorded as \"" + outcome + "\"",
        applications.recordAutoGrantOutcome(context.applicationId(), outcome),
        200,
        204);
  }

  @Then("the application is eventually ready for manual assessment")
  public void readyForManualAssessment() {
    context.lastResponse(applications.awaitAutoGrantOutcome(context.applicationId(), "MANUAL"));
  }

  @When("I decide the application as {string}")
  public void decide(String decision) {
    if (!"GRANTED".equals(decision))
      throw new IllegalArgumentException("Only GRANTED is currently supported");
    ApiResponse latest = applications.get(context.applicationId());
    context.applicationVersion(latest.body().path("version").asLong());
    assertStatus(
        context,
        "I decide the application as \"" + decision + "\"",
        applications.decide(
            context.applicationId(),
            fixtures.grantDecision(context.proceedingId(), context.applicationVersion())),
        204);
  }

  @Then("the application is eventually decided as {string}")
  public void decided(String decision) {
    context.lastResponse(applications.awaitDecision(context.applicationId(), decision));
  }

  @Then("the application history eventually documents the complete lifecycle")
  public void historyDocumentsCompleteLifecycle() {
    context.lastResponse(
        applications.awaitHistory(context.applicationId(), this::hasCompleteLifecycleHistory));
  }

  private static void assertStatus(
      ScenarioContext context, String step, ApiResponse response, int... statuses) {
    context.lastResponse(response);
    if (!response.hasStatus(statuses))
      assertEquals(
          statuses[0],
          response.status(),
          "Step failed: " + step + ". Unexpected response: " + response.body());
  }

  private String reference() {
    return "BDD-" + context.applicationId().toString().substring(0, 8);
  }

  private JsonNode findApplication(JsonNode response) {
    for (JsonNode application : response.path("applications")) {
      if (context.applicationId().toString().equals(application.path("applicationId").asText()))
        return application;
    }
    return null;
  }

  private boolean hasCompleteLifecycleHistory(ApiResponse response) {
    JsonNode history = response.body();
    return eventTypes(history.path("events"))
            .equals(
                List.of(
                    "APPLICATION_CREATED",
                    "ASSIGN_APPLICATION_TO_CASEWORKER",
                    "APPLICATION_MAKE_DECISION_GRANTED"))
        && priorAuthorityHistory(history);
  }

  private boolean priorAuthorityHistory(JsonNode history) {
    for (JsonNode priorAuthority : history.path("priorAuthorities")) {
      if (context
          .priorAuthorityId()
          .toString()
          .equals(priorAuthority.path("priorAuthorityId").asText())) {
        return eventTypes(priorAuthority.path("events"))
            .equals(
                List.of(
                    "PRIOR_AUTHORITY_SUBMITTED",
                    "ASSIGN_APPLICATION_TO_CASEWORKER",
                    "UNASSIGN_APPLICATION_TO_CASEWORKER",
                    "ASSIGN_APPLICATION_TO_CASEWORKER",
                    "PRIOR_AUTHORITY_MAKE_DECISION_GRANTED"));
      }
    }
    return false;
  }

  private static List<String> eventTypes(JsonNode events) {
    List<String> eventTypes = new ArrayList<>();
    for (JsonNode event : events) {
      eventTypes.add(event.path("domainEventType").asText(event.path("eventType").asText()));
    }
    return eventTypes;
  }
}
