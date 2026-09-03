package uk.gov.justice.laa.dstew.dataaccesstools.utils.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpDataAccessApiClientTest {
  private HttpServer server;
  private final List<Request> requests = new ArrayList<>();
  private final UUID applicationId = UUID.randomUUID();
  private final UUID priorAuthorityId = UUID.randomUUID();
  private boolean includeLocation = true;

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

    assertEquals(applicationId, client.createApplication("{}"));
    client.recordManualOutcome(applicationId);
    client.recordAutograntedOutcome(
        applicationId, "{\"outcome\":\"AUTOGRANTED\",\"certificate\":{}}");
    client.makeDecision(applicationId, "{}");
    assertEquals(priorAuthorityId, client.createPriorAuthority(applicationId, "{}"));
    client.assignWorkListItem(applicationId, priorAuthorityId, 3, "Assigned \"locally\"");

    assertEquals(6, requests.size());
    requests.forEach(
        request -> {
          assertEquals("Bearer swagger-caseworker-token", request.authorization());
          assertEquals("CIVIL_APPLY", request.serviceName());
        });
    assertEquals("POST", requests.get(0).method());
    assertEquals("/api/v0/applications", requests.get(0).path());
    assertEquals("PATCH", requests.get(1).method());
    assertEquals("{\"outcome\":\"MANUAL\"}", requests.get(1).body());
    assertEquals("PATCH", requests.get(2).method());
    assertEquals("{\"outcome\":\"AUTOGRANTED\",\"certificate\":{}}", requests.get(2).body());
    assertEquals("POST", requests.get(5).method());
    assertEquals("/api/v0/work-list/" + applicationId + "/assign", requests.get(5).path());
    assertEquals(
        "{\"caseworkerId\":\""
            + priorAuthorityId
            + "\",\"expectedAssignmentVersion\":3,\"eventHistory\":{\"eventDescription\":\"Assigned \\\"locally\\\"\"}}",
        requests.get(5).body());
  }

  @Test
  void rejectsSuccessfulCreationResponsesWithoutALocationHeader() {
    includeLocation = false;

    ApiException exception =
        assertThrows(
            ApiException.class,
            () -> new HttpDataAccessApiClient(baseUri()).createApplication("{}"));

    assertEquals("POST /api/v0/applications returned no Location header", exception.getMessage());
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
    int status = exchange.getRequestURI().getPath().endsWith("auto-grant-outcome") ? 204 : 201;
    if (exchange.getRequestURI().getPath().endsWith("/decision")) {
      status = 200;
    }
    if (exchange.getRequestURI().getPath().endsWith("/assign")) {
      status = 200;
    }
    if (includeLocation && exchange.getRequestMethod().equals("POST")) {
      UUID id =
          exchange.getRequestURI().getPath().endsWith("/prior-authority")
              ? priorAuthorityId
              : applicationId;
      exchange
          .getResponseHeaders()
          .set(
              "Location",
              baseUri().resolve(exchange.getRequestURI().getPath() + "/" + id).toString());
    }
    exchange.sendResponseHeaders(status, -1);
    exchange.close();
  }

  private record Request(
      String method, String path, String authorization, String serviceName, String body) {}
}
