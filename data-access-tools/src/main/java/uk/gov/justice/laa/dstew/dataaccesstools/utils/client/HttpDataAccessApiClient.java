package uk.gov.justice.laa.dstew.dataaccesstools.utils.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

public final class HttpDataAccessApiClient implements DataAccessApiClient {
  private static final String DEV_TOKEN = "swagger-caseworker-token";
  private final URI baseUri;
  private final HttpClient client;

  public HttpDataAccessApiClient(URI baseUri) {
    this(baseUri, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build());
  }

  HttpDataAccessApiClient(URI baseUri, HttpClient client) {
    this.baseUri = baseUri.resolve(baseUri.getPath().endsWith("/") ? "" : "/");
    this.client = client;
  }

  @Override
  public UUID createApplication(String requestBody) {
    return locationId(
        execute("POST", "api/v0/applications", requestBody, "CIVIL_APPLY", Set.of(201)),
        "POST",
        "api/v0/applications");
  }

  @Override
  public void recordManualOutcome(UUID applicationId) {
    execute(
        "PATCH",
        "api/v0/applications/" + applicationId + "/auto-grant-outcome",
        "{\"outcome\":\"MANUAL\"}",
        "CIVIL_APPLY",
        Set.of(200, 204));
  }

  @Override
  public void recordAutograntedOutcome(UUID applicationId, String requestBody) {
    execute(
        "PATCH",
        "api/v0/applications/" + applicationId + "/auto-grant-outcome",
        requestBody,
        "CIVIL_APPLY",
        Set.of(200, 204));
  }

  @Override
  public void makeDecision(UUID applicationId, String requestBody) {
    execute(
        "PATCH",
        "api/v0/applications/" + applicationId + "/decision",
        requestBody,
        "CIVIL_APPLY",
        Set.of(200, 204));
  }

  @Override
  public UUID createPriorAuthority(UUID applicationId, String requestBody) {
    String path = "api/v0/applications/" + applicationId + "/prior-authority";
    return locationId(
        execute("POST", path, requestBody, "CIVIL_APPLY", Set.of(201, 202)), "POST", path);
  }

  @Override
  public void assignWorkListItem(
      UUID itemId, UUID caseworkerId, long expectedAssignmentVersion, String eventDescription) {
    String body =
        "{\"caseworkerId\":\"%s\",\"expectedAssignmentVersion\":%d%s}"
            .formatted(
                caseworkerId,
                expectedAssignmentVersion,
                eventDescription == null
                    ? ""
                    : ",\"eventHistory\":{\"eventDescription\":\""
                        + escapeJson(eventDescription)
                        + "\"}");
    execute("POST", "api/v0/work-list/" + itemId + "/assign", body, "CIVIL_APPLY", Set.of(200));
  }

  private HttpResponse<String> execute(
      String method, String path, String body, String serviceName, Set<Integer> acceptedStatuses) {
    HttpRequest request =
        HttpRequest.newBuilder(baseUri.resolve(path))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", "Bearer " + DEV_TOKEN)
            .header("X-Service-Name", serviceName)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .method(method, HttpRequest.BodyPublishers.ofString(body))
            .build();
    try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (!acceptedStatuses.contains(response.statusCode())) {
        throw new ApiException(
            "%s /%s returned HTTP %d: %s"
                .formatted(method, path, response.statusCode(), safeBody(response.body())));
      }
      return response;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new ApiException(method + " /" + path + " interrupted", exception);
    } catch (IOException exception) {
      throw new ApiException(
          method + " /" + path + " failed: " + exception.getMessage(), exception);
    }
  }

  private UUID locationId(HttpResponse<String> response, String method, String path) {
    String location =
        response
            .headers()
            .firstValue("Location")
            .orElseThrow(
                () -> new ApiException(method + " /" + path + " returned no Location header"));
    try {
      String locationPath = URI.create(location).getPath();
      if (locationPath == null || locationPath.isBlank()) {
        throw new IllegalArgumentException("Location path is blank");
      }
      return UUID.fromString(locationPath.substring(locationPath.lastIndexOf('/') + 1));
    } catch (IllegalArgumentException exception) {
      throw new ApiException(
          method + " /" + path + " returned invalid Location header: " + location, exception);
    }
  }

  private String safeBody(String body) {
    return body == null || body.isBlank()
        ? "no response body"
        : body.substring(0, Math.min(500, body.length()));
  }

  private String escapeJson(String value) {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }
}
