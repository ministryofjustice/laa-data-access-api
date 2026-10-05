package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.DataAccessToolsCommand;

class ApplicationsCommandTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private HttpServer server;
  private final List<Request> requests = new ArrayList<>();
  private final Map<UUID, JsonNode> drafts = new HashMap<>();
  private int failedDrafts;
  private int submissionStatus = 200;

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
  void createsCompleteDraftsWithoutSubmitting() {
    assertEquals(0, execute("create-draft", "--count", "2", "--office-code", "1A234B"));
    assertEquals(2, requests.size());
    requests.forEach(request -> assertEquals("/api/v0/application-drafts", request.path()));
    drafts.forEach(
        (applicationId, draft) -> {
          assertEquals(applicationId.toString(), draft.required("id").asText());
          assertEquals("APPLICATION_SUBMITTED", draft.required("status").asText());
          JsonNode content = draft.required("applicationContent");
          assertTrue(content.required("provider").has("officeCode"));
          assertEquals("1A234B", content.required("provider").required("officeCode").asText());
          assertTrue(content.required("client").required("addresses").size() > 0);
          assertTrue(content.required("proceedings").get(0).required("leadProceeding").asBoolean());
        });
  }

  @Test
  void defaultCreateOnlyCreatesAndSubmits() {
    assertEquals(0, execute("create", "--count", "1"));
    assertEquals(2, requests.size());
    assertEquals("/api/v0/application-drafts", requests.getFirst().path());
    UUID applicationId = drafts.keySet().iterator().next();
    assertEquals(
        "/api/v0/application-drafts/" + applicationId + "/submit", requests.getLast().path());
    assertEquals("", requests.getLast().body());
  }

  @ParameterizedTest
  @EnumSource(ApplicationCreationWorkflow.Outcome.class)
  void acceptsAllLowercaseOutcomes(ApplicationCreationWorkflow.Outcome outcome) throws IOException {
    assertEquals(
        0,
        execute(
            "create",
            "--count",
            "1",
            "--outcome",
            outcome.name().toLowerCase(java.util.Locale.ROOT),
            "--office-code",
            "1A234B"));
    List<String> methods = requests.stream().map(Request::method).toList();
    List<String> expected =
        switch (outcome) {
          case SUBMITTED -> List.of("POST", "POST");
          case MANUAL, AUTOGRANTED -> List.of("POST", "POST", "GET", "PATCH");
          case GRANTED, REFUSED -> List.of("POST", "POST", "GET", "PATCH", "POST", "PATCH");
        };
    assertEquals(expected, methods);
    assertEquals(
        "1A234B",
        MAPPER
            .readTree(requests.getFirst().body())
            .required("applicationContent")
            .required("provider")
            .required("officeCode")
            .asText());
    if (outcome == ApplicationCreationWorkflow.Outcome.GRANTED
        || outcome == ApplicationCreationWorkflow.Outcome.REFUSED) {
      assertTrue(requests.getLast().body().contains("\"overallDecision\":\"" + outcome + "\""));
    }
  }

  @Test
  void acceptsMixedCaseOutcome() {
    assertEquals(0, execute("create", "--count", "1", "--outcome", "MaNuAl"));
    assertEquals("{\"outcome\":\"MANUAL\"}", requests.getLast().body());
  }

  @ParameterizedTest
  @ValueSource(ints = {200, 202})
  void submitsExistingDraftWithoutReplacingContent(int responseStatus) {
    submissionStatus = responseStatus;
    assertEquals(0, execute("create-draft", "--count", "1"));
    UUID applicationId = drafts.keySet().iterator().next();
    JsonNode draft = drafts.get(applicationId);
    JsonNode original = draft.deepCopy();
    ((com.fasterxml.jackson.databind.node.ObjectNode) draft)
        .put("evidenceMarker", "uploaded-through-api");
    requests.clear();

    assertEquals(0, execute("submit-draft", "--application-id", applicationId.toString()));
    assertEquals(1, requests.size());
    assertEquals("", requests.getFirst().body());
    assertEquals(original.required("applicationContent"), draft.required("applicationContent"));
    assertEquals("uploaded-through-api", draft.required("evidenceMarker").asText());
  }

  @Test
  void continuesBatchAndReturnsFailureExitCode() {
    failedDrafts = 1;
    assertEquals(1, execute("create", "--count", "2"));
    assertEquals(3, requests.size());
    assertEquals(1, drafts.size());
  }

  @Test
  void failedExistingDraftSubmissionReturnsFailureExitCode() {
    assertEquals(1, execute("submit-draft", "--application-id", UUID.randomUUID().toString()));
    assertEquals(1, requests.size());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"create-manual", "create-autogranted", "create-granted", "create-refused"})
  void rejectsRemovedCommands(String command) {
    assertEquals(2, execute(command, "--count", "1"));
    assertTrue(requests.isEmpty());
  }

  @ParameterizedTest
  @MethodSource("invalidArguments")
  void rejectsInvalidArgumentsBeforeSendingRequests(String[] arguments) {
    assertEquals(2, execute(arguments));
    assertTrue(requests.isEmpty());
  }

  static Stream<Arguments> invalidArguments() {
    return Stream.<String[]>of(
            new String[] {"create", "--count", "0"},
            new String[] {"create", "--count", "-1"},
            new String[] {"create"},
            new String[] {"create", "--count", "1", "--outcome", "unknown"},
            new String[] {"create", "--count", "1", "--office-code", "invalid"},
            new String[] {"create-draft", "--count", "0"},
            new String[] {"create-draft", "--count", "-1"},
            new String[] {"create-draft"},
            new String[] {"create-draft", "--count", "1", "--office-code", "invalid"},
            new String[] {"submit-draft"},
            new String[] {"submit-draft", "--application-id", "not-a-uuid"})
        .map(arguments -> Arguments.of((Object) arguments));
  }

  @Test
  void helpShowsReplacementCommandsAndRetainsDecisionAndAssignment() {
    StringWriter output = new StringWriter();
    CommandLine command =
        new CommandLine(new DataAccessToolsCommand()).setOut(new PrintWriter(output));
    assertEquals(0, command.execute("applications", "--help"));
    assertTrue(output.toString().contains("create-draft"));
    assertTrue(output.toString().contains("submit-draft"));
    assertEquals(
        java.util.Set.of("create", "create-draft", "submit-draft", "make-decision", "assign"),
        command.getSubcommands().get("applications").getSubcommands().keySet());
    assertTrue(requests.isEmpty());
  }

  private int execute(String... arguments) {
    List<String> all =
        new ArrayList<>(
            List.of(
                "--api-url",
                "http://localhost:" + server.getAddress().getPort(),
                "--seed",
                "42",
                "applications"));
    all.addAll(List.of(arguments));
    return new CommandLine(new DataAccessToolsCommand())
        .setErr(new PrintWriter(new StringWriter()))
        .execute(all.toArray(String[]::new));
  }

  private void respond(HttpExchange exchange) throws IOException {
    String path = exchange.getRequestURI().getPath();
    String method = exchange.getRequestMethod();
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    requests.add(new Request(method, path, body));
    if (path.equals("/api/v0/application-drafts") && method.equals("POST")) {
      if (failedDrafts > 0) {
        failedDrafts--;
        exchange.sendResponseHeaders(400, -1);
      } else {
        JsonNode draft = MAPPER.readTree(body);
        UUID applicationId = UUID.fromString(draft.required("id").asText());
        drafts.put(applicationId, draft);
        exchange
            .getResponseHeaders()
            .set("Location", "/api/v0/application-drafts/" + applicationId);
        exchange.sendResponseHeaders(201, -1);
      }
    } else if (path.startsWith("/api/v0/application-drafts/") && path.endsWith("/submit")) {
      UUID applicationId = UUID.fromString(path.split("/")[4]);
      exchange.getResponseHeaders().set("Location", "/api/v0/applications/" + applicationId);
      exchange.sendResponseHeaders(drafts.containsKey(applicationId) ? submissionStatus : 404, -1);
    } else if (method.equals("GET") && path.startsWith("/api/v0/applications/")) {
      UUID applicationId = UUID.fromString(path.split("/")[4]);
      JsonNode draft = drafts.get(applicationId);
      String response =
          "{\"laaReference\":\""
              + draft.required("laaReference").asText()
              + "\",\"proceedings\":[{\"proceedingId\":\""
              + draft
                  .required("applicationContent")
                  .required("proceedings")
                  .get(0)
                  .required("id")
                  .asText()
              + "\"}],\"version\":0}";
      byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, bytes.length);
      exchange.getResponseBody().write(bytes);
    } else if (path.endsWith("/auto-grant-outcome")
        || path.endsWith("/decision")
        || path.endsWith("/assign")) {
      exchange.sendResponseHeaders(200, -1);
    } else {
      exchange.sendResponseHeaders(400, -1);
    }
    exchange.close();
  }

  private record Request(String method, String path, String body) {}
}
