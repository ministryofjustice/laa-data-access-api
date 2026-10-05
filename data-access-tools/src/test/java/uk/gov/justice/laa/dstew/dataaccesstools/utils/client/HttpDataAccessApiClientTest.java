package uk.gov.justice.laa.dstew.dataaccesstools.utils.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HttpDataAccessApiClientTest {
  private HttpServer server;
  private final List<Request> requests = new ArrayList<>();
  private final UUID applicationId = UUID.randomUUID();
  private final UUID priorAuthorityId = UUID.randomUUID();
  private boolean includeLocation = true;
  private int applicationCreationStatus = 201;
  private int applicationSubmissionStatus = 200;
  private int readStatus = 200;
  private int unavailableReads;
  private String locationOverride;
  private boolean invalidReadBody;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext("/", this::respond);
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void sendsRequiredAuthenticationAndServiceHeadersForEveryOperation() {
    HttpDataAccessApiClient client = new HttpDataAccessApiClient(baseUri());

    assertEquals(applicationId, client.createApplicationDraft("{}"));
    assertEquals(
        "LAA-CLI-12345678", client.getApplicationDecisionData(applicationId).laaReference());
    client.recordManualOutcome(applicationId);
    client.recordAutograntedOutcome(
        applicationId, "{\"outcome\":\"AUTOGRANTED\",\"certificate\":{}}");
    client.makeDecision(applicationId, "{}");
    assertEquals(priorAuthorityId, client.createPriorAuthorityDraft("{}"));
    client.updatePriorAuthorityDraft(priorAuthorityId, "{\"justification\":\"Required\"}");
    assertEquals(priorAuthorityId, client.submitPriorAuthorityDraft(priorAuthorityId));
    client.assignWorkListItem(applicationId, 3, "Assigned \"locally\"");

    assertEquals(9, requests.size());
    requests.forEach(
        request -> {
          assertEquals("Bearer swagger-caseworker-token", request.authorization());
          assertEquals("CIVIL_APPLY", request.serviceName());
        });
    assertEquals("POST", requests.get(0).method());
    assertEquals("/api/v0/application-drafts", requests.get(0).path());
    assertEquals("GET", requests.get(1).method());
    assertEquals("/api/v0/applications/" + applicationId, requests.get(1).path());
    assertEquals("PATCH", requests.get(2).method());
    assertEquals("{\"outcome\":\"MANUAL\"}", requests.get(2).body());
    assertEquals("PATCH", requests.get(3).method());
    assertEquals("{\"outcome\":\"AUTOGRANTED\",\"certificate\":{}}", requests.get(3).body());
    assertEquals("POST", requests.get(5).method());
    assertEquals("/api/v0/prior-authorities", requests.get(5).path());
    assertEquals("PUT", requests.get(6).method());
    assertEquals("/api/v0/prior-authorities/" + priorAuthorityId, requests.get(6).path());
    assertEquals("POST", requests.get(7).method());
    assertEquals(
        "/api/v0/prior-authorities/" + priorAuthorityId + "/submit", requests.get(7).path());
    assertEquals("POST", requests.get(8).method());
    assertEquals("/api/v0/work-list/" + applicationId + "/assign", requests.get(8).path());
    assertEquals(
        "{\"expectedAssignmentVersion\":3,\"eventHistory\":{\"eventDescription\":\"Assigned \\\"locally\\\"\"}}",
        requests.get(8).body());
  }

  @Test
  void rejectsAsynchronousDraftCreationWithoutALocationHeader() {
    includeLocation = false;
    applicationCreationStatus = 202;

    assertThrows(
        ApiException.class,
        () -> new HttpDataAccessApiClient(baseUri()).createApplicationDraft("{}"));
  }

  @ParameterizedTest
  @ValueSource(ints = {201, 202})
  void createsApplicationDraft(int responseStatus) {
    applicationCreationStatus = responseStatus;
    UUID result = new HttpDataAccessApiClient(baseUri()).createApplicationDraft("{\"id\":1}");

    assertEquals(applicationId, result);
    assertEquals("POST", requests.getFirst().method());
    assertEquals("/api/v0/application-drafts", requests.getFirst().path());
    assertEquals("{\"id\":1}", requests.getFirst().body());
    assertEquals("CIVIL_APPLY", requests.getFirst().serviceName());
    assertEquals("Bearer swagger-caseworker-token", requests.getFirst().authorization());
  }

  @ParameterizedTest
  @ValueSource(ints = {200, 202})
  void submitsApplicationDraftWithoutABody(int responseStatus) {
    applicationSubmissionStatus = responseStatus;
    UUID result = new HttpDataAccessApiClient(baseUri()).submitApplicationDraft(applicationId);

    assertEquals(applicationId, result);
    assertEquals("POST", requests.getFirst().method());
    assertEquals(
        "/api/v0/application-drafts/" + applicationId + "/submit", requests.getFirst().path());
    assertEquals("", requests.getFirst().body());
  }

  @Test
  void rejectsMissingDraftLocation() {
    includeLocation = false;

    assertThrows(
        ApiException.class,
        () -> new HttpDataAccessApiClient(baseUri()).createApplicationDraft("{}"));
  }

  @Test
  void acceptsRelativeDraftLocation() {
    locationOverride = "/api/v0/application-drafts/" + applicationId;
    assertEquals(
        applicationId, new HttpDataAccessApiClient(baseUri()).createApplicationDraft("{}"));
  }

  @Test
  void rejectsInvalidLocationAndMismatchedSubmittedId() {
    locationOverride = "/api/v0/application-drafts/not-a-uuid";
    assertThrows(
        ApiException.class,
        () -> new HttpDataAccessApiClient(baseUri()).createApplicationDraft("{}"));
    locationOverride = "/api/v0/applications/" + UUID.randomUUID();
    assertThrows(
        ApiException.class,
        () -> new HttpDataAccessApiClient(baseUri()).submitApplicationDraft(applicationId));
  }

  @Test
  void waitsForProjectionWithoutRetryingWrites() {
    unavailableReads = 2;
    waitingClient(Duration.ofSeconds(2)).awaitApplicationReadable(applicationId);
    assertEquals(3, requests.size());
    requests.forEach(request -> assertEquals("GET", request.method()));
  }

  @Test
  void timesOutWhenApplicationRemainsUnreadable() {
    readStatus = 404;
    ApiException exception =
        assertThrows(
            ApiException.class,
            () -> waitingClient(Duration.ofMillis(100)).awaitApplicationReadable(applicationId));
    assertTrue(exception.getMessage().contains("Timed out"));
  }

  @ParameterizedTest
  @ValueSource(ints = {401, 403, 500})
  void doesNotRetryNonProjectionFailures(int responseStatus) {
    readStatus = responseStatus;
    assertThrows(
        ApiException.class,
        () -> waitingClient(Duration.ofSeconds(2)).awaitApplicationReadable(applicationId));
    assertEquals(1, requests.size());
  }

  @Test
  void rejectsMalformedProjectionWithoutRetrying() {
    invalidReadBody = true;
    assertThrows(
        ApiException.class,
        () -> waitingClient(Duration.ofSeconds(2)).awaitApplicationReadable(applicationId));
    assertEquals(1, requests.size());
  }

  @Test
  void preservesInterruption() {
    Thread.currentThread().interrupt();
    try {
      assertThrows(
          ApiException.class,
          () -> waitingClient(Duration.ofSeconds(2)).awaitApplicationReadable(applicationId));
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  private HttpDataAccessApiClient waitingClient(Duration timeout) {
    return new HttpDataAccessApiClient(
        baseUri(), HttpClient.newHttpClient(), timeout, Duration.ofMillis(1));
  }

  private URI baseUri() {
    return URI.create("http://localhost:" + server.getAddress().getPort());
  }

  private void respond(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes());
    requests.add(
        new Request(
            exchange.getRequestMethod(),
            exchange.getRequestURI().getPath(),
            exchange.getRequestHeaders().getFirst("Authorization"),
            exchange.getRequestHeaders().getFirst("X-Service-Name"),
            body));
    String path = exchange.getRequestURI().getPath();
    if (exchange.getRequestMethod().equals("GET")) {
      if (unavailableReads > 0 || readStatus != 200) {
        unavailableReads--;
        exchange.sendResponseHeaders(readStatus == 200 ? 404 : readStatus, -1);
        exchange.close();
        return;
      }
      byte[] response =
          (invalidReadBody
                  ? "not-json"
                  : "{\"laaReference\":\"LAA-CLI-12345678\",\"proceedings\":[{\"proceedingId\":\""
                      + priorAuthorityId
                      + "\"}],\"version\":2}")
              .getBytes();
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
      return;
    }
    int status =
        path.endsWith("auto-grant-outcome") || exchange.getRequestMethod().equals("PUT")
            ? 204
            : 201;
    if (path.equals("/api/v0/application-drafts") && exchange.getRequestMethod().equals("POST")) {
      status = applicationCreationStatus;
    }
    if (exchange.getRequestURI().getPath().endsWith("/decision")) {
      status = 200;
    }
    if (path.endsWith("/assign") || path.endsWith("/submit")) {
      status = 200;
    }
    if (path.startsWith("/api/v0/application-drafts/") && path.endsWith("/submit")) {
      status = applicationSubmissionStatus;
    }
    if (includeLocation && exchange.getRequestMethod().equals("POST")) {
      UUID id = path.contains("/prior-authorities") ? priorAuthorityId : applicationId;
      String locationPath =
          path.contains("/prior-authorities")
              ? "/api/v0/prior-authorities/" + id
              : path.equals("/api/v0/application-drafts")
                  ? "/api/v0/application-drafts/" + id
                  : "/api/v0/applications/" + id;
      exchange
          .getResponseHeaders()
          .set(
              "Location",
              locationOverride != null
                  ? locationOverride
                  : baseUri().resolve(locationPath).toString());
    }
    exchange.sendResponseHeaders(status, -1);
    exchange.close();
  }

  private record Request(
      String method, String path, String authorization, String serviceName, String body) {}
}
