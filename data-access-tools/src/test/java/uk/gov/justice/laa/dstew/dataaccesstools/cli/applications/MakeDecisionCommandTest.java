package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import picocli.CommandLine;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.DataAccessToolsCommand;

class MakeDecisionCommandTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private final UUID applicationId = UUID.randomUUID();
  private final UUID caseworkerId = UUID.randomUUID();
  private final UUID firstProceedingId = UUID.randomUUID();
  private final UUID secondProceedingId = UUID.randomUUID();
  private final List<Request> requests = new ArrayList<>();
  private HttpServer server;

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
  void getsCurrentApplicationDataAndMakesDecisionForTheAssignedCaseworker() throws Exception {
    int exitCode =
        new CommandLine(new DataAccessToolsCommand())
            .execute(
                "--api-url",
                baseUri().toString(),
                "applications",
                "make-decision",
                "--application-id",
                applicationId.toString(),
                "--decision",
                "GRANTED",
                "--caseworker-id",
                caseworkerId.toString());

    assertEquals(0, exitCode);
    assertEquals(2, requests.size());
    assertEquals("GET", requests.getFirst().method());
    assertEquals("/api/v0/applications/" + applicationId, requests.getFirst().path());
    assertEquals("PATCH", requests.get(1).method());
    assertEquals("/api/v0/applications/" + applicationId + "/decision", requests.get(1).path());

    JsonNode decision = OBJECT_MAPPER.readTree(requests.get(1).body());
    assertEquals("GRANTED", decision.required("overallDecision").asText());
    assertEquals(7, decision.required("applicationVersion").asLong());
    assertEquals(caseworkerId.toString(), decision.required("caseworkerId").asText());
    assertEquals(2, decision.required("proceedings").size());
    assertTrue(decision.toString().contains(firstProceedingId.toString()));
    assertTrue(decision.toString().contains(secondProceedingId.toString()));
  }

  private URI baseUri() {
    return URI.create("http://localhost:" + server.getAddress().getPort());
  }

  private void respond(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes());
    requests.add(
        new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), body));
    if (exchange.getRequestMethod().equals("GET")) {
      byte[] response =
          ("{\"laaReference\":\"LAA-CLI-12345678\",\"proceedings\":[{\"id\":\""
                  + firstProceedingId
                  + "\"},{\"id\":\""
                  + secondProceedingId
                  + "\"}],\"applicationVersion\":7}")
              .getBytes();
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
    } else {
      exchange.sendResponseHeaders(200, -1);
    }
    exchange.close();
  }

  private record Request(String method, String path, String body) {}
}
