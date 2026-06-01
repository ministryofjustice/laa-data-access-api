package uk.gov.justice.laa.dstew.access.config;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;

import com.github.tomakehurst.wiremock.WireMockServer;
import uk.gov.justice.laa.dstew.access.utils.harness.TestContextProvider;

/**
 * WireMock stub configuration for SDS API integration tests. Obtain via {@link
 * #from(TestContextProvider)}. Default stubs run at priority 5; override stubs at priority 1.
 */
public class SdsWireMockStubs {

  private static final int DEFAULT_PRIORITY = 5;
  private static final int OVERRIDE_PRIORITY = 1;

  private static final String SAVE_FILE = "/save_file";
  private static final String GET_FILE = "/get_file";
  private static final String SAVE_OR_UPDATE_FILE = "/save_or_update_file";
  private static final String DELETE_FILES = "/delete_files";
  private static final String HEALTH = "/health";

  private static final String JSON_CONTENT_TYPE = "application/json";
  private static final String PROBLEM_CONTENT_TYPE = "application/problem+json";

  private static final String UPLOAD_SUCCESS_BODY =
      """
      {"detail":"test-app/document.pdf","success":"File uploaded successfully","checksum":"mock-checksum-abc123"}
      """;
  private static final String DOWNLOAD_SUCCESS_BODY =
      """
      {"fileURL":"https://sds-mock.example.com/files/test-file.pdf"}
      """;
  private static final String UPDATE_SUCCESS_BODY =
      """
      {"fileURL":"https://sds-mock.example.com/files/updated/test-file.pdf"}
      """;
  private static final String HEALTH_SUCCESS_BODY =
      """
      {"Health":"UP"}
      """;
  private static final String CONFLICT_BODY =
      """
      {"title":"Conflict","detail":"File already exists in SDS","status":409}
      """;
  private static final String NOT_FOUND_BODY =
      """
      {"title":"Not Found","detail":"File not found","status":404}
      """;

  private final WireMockServer server;

  private SdsWireMockStubs(WireMockServer server) {
    this.server = server;
  }

  /** Creates an instance bound to the WireMock server held in the given test context. */
  public static SdsWireMockStubs from(TestContextProvider provider) {
    return new SdsWireMockStubs(provider.getBean(WireMockServer.class));
  }

  /**
   * Resets all stubs and registers default success responses for all SDS endpoints. Call this in
   * {@code @BeforeEach} to ensure a clean state at the start of each test.
   */
  public void setupDefaultStubs() {
    server.resetAll();

    server.stubFor(
        post(urlEqualTo(SAVE_FILE))
            .atPriority(DEFAULT_PRIORITY)
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", JSON_CONTENT_TYPE)
                    .withBody(UPLOAD_SUCCESS_BODY)));

    server.stubFor(
        get(urlPathEqualTo(GET_FILE))
            .atPriority(DEFAULT_PRIORITY)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", JSON_CONTENT_TYPE)
                    .withBody(DOWNLOAD_SUCCESS_BODY)));

    server.stubFor(
        put(urlEqualTo(SAVE_OR_UPDATE_FILE))
            .atPriority(DEFAULT_PRIORITY)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", JSON_CONTENT_TYPE)
                    .withBody(UPDATE_SUCCESS_BODY)));

    server.stubFor(
        delete(urlPathEqualTo(DELETE_FILES))
            .atPriority(DEFAULT_PRIORITY)
            .willReturn(aResponse().withStatus(204)));

    server.stubFor(
        get(urlEqualTo(HEALTH))
            .atPriority(DEFAULT_PRIORITY)
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", JSON_CONTENT_TYPE)
                    .withBody(HEALTH_SUCCESS_BODY)));
  }

  /**
   * Configures a WireMock scenario where the first upload succeeds (201) and the second upload of
   * the same endpoint returns a conflict (409). Use this when testing duplicate file upload
   * behaviour.
   */
  public void stubDuplicateUpload() {
    server.stubFor(
        post(urlEqualTo(SAVE_FILE))
            .atPriority(OVERRIDE_PRIORITY)
            .inScenario("duplicate-upload")
            .whenScenarioStateIs(STARTED)
            .willReturn(
                aResponse()
                    .withStatus(201)
                    .withHeader("Content-Type", JSON_CONTENT_TYPE)
                    .withBody(UPLOAD_SUCCESS_BODY))
            .willSetStateTo("uploaded"));

    server.stubFor(
        post(urlEqualTo(SAVE_FILE))
            .atPriority(OVERRIDE_PRIORITY)
            .inScenario("duplicate-upload")
            .whenScenarioStateIs("uploaded")
            .willReturn(
                aResponse()
                    .withStatus(409)
                    .withHeader("Content-Type", PROBLEM_CONTENT_TYPE)
                    .withBody(CONFLICT_BODY)));
  }

  /**
   * Overrides the default download stub to return 404 for all download requests. Use this to
   * simulate a file that does not exist in SDS.
   */
  public void stubFileNotFoundOnDownload() {
    server.stubFor(
        get(urlPathEqualTo(GET_FILE))
            .atPriority(OVERRIDE_PRIORITY)
            .willReturn(
                aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", PROBLEM_CONTENT_TYPE)
                    .withBody(NOT_FOUND_BODY)));
  }

  /**
   * Overrides the default delete stub to return 404 for all delete requests. Use this to simulate
   * attempting to delete a file that does not exist in SDS.
   */
  public void stubFileNotFoundOnDelete() {
    server.stubFor(
        delete(urlPathEqualTo(DELETE_FILES))
            .atPriority(OVERRIDE_PRIORITY)
            .willReturn(
                aResponse()
                    .withStatus(404)
                    .withHeader("Content-Type", PROBLEM_CONTENT_TYPE)
                    .withBody(NOT_FOUND_BODY)));
  }
}
