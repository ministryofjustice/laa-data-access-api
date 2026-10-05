package uk.gov.justice.laa.dstew.dataaccesstools.cli.applications;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;
import uk.gov.justice.laa.dstew.dataaccesstools.cli.DataAccessToolsCommand;

class CreateApplicationsCommandTest {
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
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

  @ParameterizedTest
  @ValueSource(
      strings = {"create-granted", "create-refused", "create-autogranted", "create-manual"})
  void usesSpecifiedOfficeCodeForEveryCreateCommand(String command) throws Exception {
    int exitCode =
        new CommandLine(new DataAccessToolsCommand())
            .execute(
                "--api-url",
                baseUri().toString(),
                "applications",
                command,
                "--count",
                "1",
                "--office-code",
                "1A234B");

    assertEquals(0, exitCode);
    Request applicationCreation =
        requests.stream()
            .filter(request -> request.method().equals("POST"))
            .filter(request -> request.path().equals("/api/v0/applications"))
            .findFirst()
            .orElseThrow();
    assertEquals(
        "1A234B",
        OBJECT_MAPPER
            .readTree(applicationCreation.body())
            .required("applicationContent")
            .required("provider")
            .required("officeCode")
            .asText());
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"create-granted", "create-refused", "create-autogranted", "create-manual"})
  void rejectsInvalidOfficeCodeForEveryCreateCommand(String command) {
    int exitCode =
        new CommandLine(new DataAccessToolsCommand())
            .execute(
                "--api-url",
                baseUri().toString(),
                "applications",
                command,
                "--count",
                "1",
                "--office-code",
                "invalid");

    assertEquals(CommandLine.ExitCode.USAGE, exitCode);
    assertEquals(List.of(), requests);
  }

  private URI baseUri() {
    return URI.create("http://localhost:" + server.getAddress().getPort());
  }

  private void respond(HttpExchange exchange) throws IOException {
    requests.add(
        new Request(
            exchange.getRequestMethod(),
            exchange.getRequestURI().getPath(),
            new String(exchange.getRequestBody().readAllBytes())));
    String path = exchange.getRequestURI().getPath();
    int status =
        path.equals("/api/v0/applications") ? 201 : path.contains("/work-list/") ? 200 : 204;
    exchange.sendResponseHeaders(status, -1);
    exchange.close();
  }

  private record Request(String method, String path, String body) {}
}
