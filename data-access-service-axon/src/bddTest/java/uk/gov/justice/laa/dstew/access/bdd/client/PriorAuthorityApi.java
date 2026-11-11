package uk.gov.justice.laa.dstew.access.bdd.client;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.bdd.support.ApiClient;
import uk.gov.justice.laa.dstew.access.bdd.support.ApiResponse;
import uk.gov.justice.laa.dstew.access.bdd.support.HttpPolling;

public final class PriorAuthorityApi {
  private final ApiClient apiClient;
  private final HttpPolling polling;

  public PriorAuthorityApi(ApiClient apiClient, HttpPolling polling) {
    this.apiClient = apiClient;
    this.polling = polling;
  }

  public ApiResponse createDraft(Object draft) {
    return apiClient.post("/api/v0/prior-authorities", draft);
  }

  public ApiResponse submitDraft(UUID priorAuthorityId) {
    return apiClient.post("/api/v0/prior-authorities/" + priorAuthorityId + "/submit");
  }

  public ApiResponse get(UUID priorAuthorityId) {
    return apiClient.get("/api/v0/prior-authorities/" + priorAuthorityId);
  }

  public ApiResponse awaitSubmitted(UUID priorAuthorityId) {
    return polling.awaitGet(
        "/api/v0/prior-authorities/" + priorAuthorityId,
        response -> "SUBMITTED".equals(response.body().path("status").asText()));
  }

  public ApiResponse decide(UUID priorAuthorityId, Object decision) {
    return apiClient.patch("/api/v0/prior-authorities/" + priorAuthorityId + "/decision", decision);
  }

  public ApiResponse awaitDecision(UUID priorAuthorityId, String decision) {
    return polling.awaitGet(
        "/api/v0/prior-authorities/" + priorAuthorityId,
        response -> decision.equals(response.body().path("decision").asText()));
  }
}
