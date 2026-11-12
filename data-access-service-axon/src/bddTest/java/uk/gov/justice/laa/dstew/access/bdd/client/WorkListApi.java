package uk.gov.justice.laa.dstew.access.bdd.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.bdd.support.ApiClient;
import uk.gov.justice.laa.dstew.access.bdd.support.ApiResponse;
import uk.gov.justice.laa.dstew.access.bdd.support.HttpPolling;

public final class WorkListApi {
  private final ApiClient apiClient;
  private final HttpPolling polling;

  public WorkListApi(ApiClient apiClient, HttpPolling polling) {
    this.apiClient = apiClient;
    this.polling = polling;
  }

  public JsonNode awaitUnassignedItem(String type, UUID itemId) {
    ApiResponse response =
        polling.awaitGet(
            "/api/v0/work-list?unassigned=true&itemType=" + type + "&pageSize=100",
            candidate -> findItem(candidate.body(), itemId) != null);
    return findItem(response.body(), itemId);
  }

  public JsonNode awaitAssignedItem(UUID itemId) {
    ApiResponse response =
        polling.awaitGet(
            "/api/v0/work-list?assignedToMe=true&unassigned=false&pageSize=100",
            candidate -> findItem(candidate.body(), itemId) != null);
    return findItem(response.body(), itemId);
  }

  public void awaitItemAbsent(UUID itemId) {
    polling.awaitGet(
        "/api/v0/work-list?unassigned=true&assignedToMe=true&pageSize=100",
        candidate -> findItem(candidate.body(), itemId) == null);
  }

  public ApiResponse assign(UUID workItemId, long expectedAssignmentVersion) {
    return apiClient.post(
        "/api/v0/work-list/" + workItemId + "/assign",
        java.util.Map.of("expectedAssignmentVersion", expectedAssignmentVersion));
  }

  public ApiResponse unassign(UUID workItemId, long expectedAssignmentVersion) {
    return apiClient.post(
        "/api/v0/work-list/" + workItemId + "/unassign",
        java.util.Map.of("expectedAssignmentVersion", expectedAssignmentVersion));
  }

  private JsonNode findItem(JsonNode response, UUID itemId) {
    for (JsonNode item : response.path("items")) {
      if (itemId.toString().equals(item.path("itemId").asText())) return item;
    }
    return null;
  }
}
