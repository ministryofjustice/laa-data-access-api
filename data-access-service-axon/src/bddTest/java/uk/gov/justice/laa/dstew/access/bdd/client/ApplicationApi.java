package uk.gov.justice.laa.dstew.access.bdd.client;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.bdd.support.ApiClient;
import uk.gov.justice.laa.dstew.access.bdd.support.ApiResponse;
import uk.gov.justice.laa.dstew.access.bdd.support.HttpPolling;

public final class ApplicationApi {
  private final ApiClient apiClient;
  private final HttpPolling polling;

  public ApplicationApi(ApiClient apiClient, HttpPolling polling) {
    this.apiClient = apiClient;
    this.polling = polling;
  }

  public ApiResponse createDraft(Object draft) {
    return apiClient.post("/api/v0/application-drafts", draft);
  }

  public ApiResponse submitDraft(UUID applicationId) {
    return apiClient.post("/api/v0/application-drafts/" + applicationId + "/submit");
  }

  public ApiResponse get(UUID applicationId) {
    return apiClient.get("/api/v0/applications/" + applicationId);
  }

  public ApiResponse awaitSubmitted(UUID applicationId) {
    return polling.awaitGet(
        "/api/v0/applications/" + applicationId,
        response -> "APPLICATION_SUBMITTED".equals(response.body().path("status").asText()));
  }

  public ApiResponse recordAutoGrantOutcome(UUID applicationId, String outcome) {
    return apiClient.patch(
        "/api/v0/applications/" + applicationId + "/auto-grant-outcome",
        java.util.Map.of("outcome", outcome));
  }

  public ApiResponse awaitAutoGrantOutcome(UUID applicationId, String outcome) {
    return polling.awaitGet(
        "/api/v0/applications/" + applicationId,
        response -> outcome.equals(response.body().path("autoGranted").asText()));
  }

  public ApiResponse decide(UUID applicationId, Object decision) {
    return apiClient.patch("/api/v0/applications/" + applicationId + "/decision", decision);
  }

  public ApiResponse awaitDecision(UUID applicationId, String decision) {
    return polling.awaitGet(
        "/api/v0/applications/" + applicationId,
        response -> decision.equals(response.body().path("decisionStatus").asText()));
  }
}
