package uk.gov.justice.laa.dstew.dataaccesstools.utils.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    UUID applicationId = UUID.randomUUID();

    client.createApplication("{}");
    client.recordManualOutcome(applicationId);
    client.makeDecision(applicationId, "{}");
    client.createPriorAuthority(applicationId, "{}");

    assertEquals(4, requests.size());
    requests.forEach(
        request -> {
          assertEquals("Bearer swagger-caseworker-token", request.authorization());
          assertEquals("CIVIL_APPLY", request.serviceName());
        });
    assertEquals("POST", requests.get(0).method());
    assertEquals("/api/v0/applications", requests.get(0).path());
    assertEquals("PATCH", requests.get(1).method());
    assertEquals("{\"outcome\":\"MANUAL\"}", requests.get(1).body());
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
    exchange.sendResponseHeaders(status, -1);
    exchange.close();
  }

  private record Request(
      String method, String path, String authorization, String serviceName, String body) {}
}
