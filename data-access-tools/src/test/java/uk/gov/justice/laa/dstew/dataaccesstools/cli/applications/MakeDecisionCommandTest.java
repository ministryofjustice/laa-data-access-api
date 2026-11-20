package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
  void getsCurrentApplicationDataAndMakesDecisionForTheAuthenticatedUser() throws Exception {
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
                "GRANTED");

    assertEquals(0, exitCode);
    assertEquals(2, requests.size());
    assertEquals("GET", requests.getFirst().method());
    assertEquals("/api/v0/applications/" + applicationId, requests.getFirst().path());
    assertEquals("PATCH", requests.get(1).method());
    assertEquals("/api/v0/applications/" + applicationId + "/decision", requests.get(1).path());

    JsonNode decision = OBJECT_MAPPER.readTree(requests.get(1).body());
    assertEquals("GRANTED", decision.required("overallDecision").asText());
    assertEquals(7, decision.required("applicationVersion").asLong());
    assertFalse(decision.has("caseworkerId"));
    assertEquals(2, decision.required("proceedings").size());
    assertTrue(decision.toString().contains(firstProceedingId.toString()));
    assertTrue(decision.toString().contains(secondProceedingId.toString()));
    JsonNode certificate = decision.required("certificate");
    assertEquals(applicationId.toString(), certificate.required("applicationId").asText());
    assertEquals("LAA-CLI-12345678", certificate.required("laaReference").asText());
    assertEquals(2, certificate.required("proceedings").size());
    assertEquals(
        "GRANTED", certificate.required("proceedings").get(0).required("meritsDecision").asText());
    assertEquals(
        "GRANTED", certificate.required("proceedings").get(1).required("meritsDecision").asText());
  }

  private URI baseUri() {
    return URI.create("http://localhost:" + server.getAddress().getPort());
  }

  private void respond(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes());
    requests.add(
        new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(), body));
    if (exchange.getRequestMethod().equals("GET")) {
      JsonNode generated =
          OBJECT_MAPPER.readTree(new ApplicationRequestFactory(42).create().request());
      ObjectNode details = generated.required("applicationContent").deepCopy();
      details.put("applicationId", applicationId.toString());
      details.put("laaReference", "LAA-CLI-12345678");
      details.put("version", 7);
      ObjectNode first = (ObjectNode) details.required("proceedings").get(0);
      first.put("proceedingId", firstProceedingId.toString());
      first.put("meritsDecision", "PENDING");
      var second = first.deepCopy().put("proceedingId", secondProceedingId.toString());
      details.withArray("proceedings").add(second);
      byte[] response = OBJECT_MAPPER.writeValueAsBytes(details);
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
    } else {
      exchange.sendResponseHeaders(200, -1);
    }
    exchange.close();
  }

  private record Request(String method, String path, String body) {}
}
