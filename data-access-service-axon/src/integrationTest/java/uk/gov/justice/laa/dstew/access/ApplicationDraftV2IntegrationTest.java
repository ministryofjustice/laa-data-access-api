package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validApplicationContent;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.CreateApplicationDraftV2Request;
import uk.gov.justice.laa.dstew.access.model.SaveApplicationDraftV2Response;
import uk.gov.justice.laa.dstew.access.model.UpdateApplicationDraftV2Request;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;

@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"feature.enable-dev-token=true"})
@AutoConfigureTestRestTemplate
@Import(TestJwtDecoderConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApplicationDraftV2IntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  private static final Map<String, Object> INCOMPLETE_CONTENT =
      Map.of("provider", Map.of("officeCode", "OFF-1"), "incomplete", true);

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void givenIncompleteContent_whenCreateV2_thenStoresDraftWithMetadataWithoutValidatingContent() {
    UUID applicationId = UUID.randomUUID();

    ResponseEntity<String> response = createV2Draft(applicationId, INCOMPLETE_CONTENT);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    SaveApplicationDraftV2Response body = readSaveResponse(response);
    assertThat(body.getApplicationId()).isEqualTo(applicationId);
    assertThat(body.getDraftVersion()).isZero();
    assertThat(storedDraftPayload(applicationId)).contains("\"currentStep\"", "\"client\"");
  }

  @Test
  void givenExistingDraft_whenUpdateV2_thenReplacesMetadataAndReturnsNewVersion() {
    UUID applicationId = UUID.randomUUID();
    createV2Draft(applicationId, INCOMPLETE_CONTENT);

    ResponseEntity<String> response =
        restTemplate.exchange(
            draftV2Url(applicationId),
            HttpMethod.PUT,
            new HttpEntity<>(updateRequest(Map.of("incomplete", true), "proceedings"), headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(readSaveResponse(response).getDraftVersion()).isEqualTo(1L);
    assertThat(storedDraftPayload(applicationId))
        .contains("proceedings")
        .doesNotContain("\"client\"");
  }

  @Test
  void givenNoDraft_whenUpdateV2_thenReturnsNotFound() {
    ResponseEntity<String> response =
        restTemplate.exchange(
            draftV2Url(UUID.randomUUID()),
            HttpMethod.PUT,
            new HttpEntity<>(updateRequest(Map.of("incomplete", true), "client"), headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void givenInvalidContent_whenSubmitV2_thenReturnsBadRequestAndKeepsDraft() {
    UUID applicationId = UUID.randomUUID();
    createV2Draft(applicationId, INCOMPLETE_CONTENT);

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            submitV2Url(applicationId), new HttpEntity<>(null, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(storedDraftPayload(applicationId)).isNotNull();
    assertThat(applicationDataCount(applicationId)).isZero();
  }

  @Test
  void givenValidContentWithMetadata_whenSubmitV2_thenSubmitsWithoutMetadata() {
    UUID applicationId = UUID.randomUUID();
    Map<String, Object> content = validApplicationContent(applicationId, UUID.randomUUID());
    createV2Draft(applicationId, content);

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            submitV2Url(applicationId), new HttpEntity<>(null, headers()), String.class);

    assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.ACCEPTED);
    assertThat(applicationDataCount(applicationId)).isEqualTo(1);
    assertThat(applicationDataPayload(applicationId)).doesNotContain("\"currentStep\"");
    assertThat(draftCount(applicationId)).isZero();
  }

  @Test
  void givenMissingOfficeCode_whenCreateV2_thenReturnsBadRequestAndPersistsNothing() {
    UUID applicationId = UUID.randomUUID();

    ResponseEntity<String> response = createV2Draft(applicationId, Map.of("incomplete", true));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(draftCount(applicationId)).isZero();
  }

  @Test
  void givenIdenticalRetryWithChangedMetadata_whenCreateV2_thenUpdatesMetadataAndVersion() {
    UUID applicationId = UUID.randomUUID();
    createV2Draft(applicationId, INCOMPLETE_CONTENT);

    ResponseEntity<String> retry =
        restTemplate.postForEntity(
            draftV2Url(),
            new HttpEntity<>(
                createRequest(applicationId, INCOMPLETE_CONTENT, "proceedings"), headers()),
            String.class);

    assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(readSaveResponse(retry).getDraftVersion()).isEqualTo(1L);
    assertThat(storedDraftPayload(applicationId))
        .contains("proceedings")
        .doesNotContain("\"client\"");
  }

  private ResponseEntity<String> createV2Draft(UUID applicationId, Map<String, Object> content) {
    return restTemplate.postForEntity(
        draftV2Url(),
        new HttpEntity<>(createRequest(applicationId, content, "client"), headers()),
        String.class);
  }

  private static CreateApplicationDraftV2Request createRequest(
      UUID applicationId, Map<String, Object> content, String currentStep) {
    return new CreateApplicationDraftV2Request()
        .id(applicationId)
        .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
        .laaReference("LAA-123")
        .applicationContent(content)
        .metadata(Map.of("currentStep", currentStep));
  }

  private static UpdateApplicationDraftV2Request updateRequest(
      Map<String, Object> content, String currentStep) {
    return new UpdateApplicationDraftV2Request()
        .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
        .laaReference("LAA-123")
        .applicationContent(content)
        .metadata(Map.of("currentStep", currentStep));
  }

  private SaveApplicationDraftV2Response readSaveResponse(ResponseEntity<String> response) {
    return objectMapper.readValue(response.getBody(), SaveApplicationDraftV2Response.class);
  }

  private String storedDraftPayload(UUID applicationId) {
    return jdbcTemplate.queryForObject(
        "SELECT payload::text FROM axon.application_draft WHERE application_id = ?",
        String.class,
        applicationId);
  }

  private String applicationDataPayload(UUID applicationId) {
    return jdbcTemplate.queryForObject(
        "SELECT payload::text FROM axon.application_data WHERE application_id = ?",
        String.class,
        applicationId);
  }

  private int applicationDataCount(UUID applicationId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM axon.application_data WHERE application_id = ?",
        Integer.class,
        applicationId);
  }

  private int draftCount(UUID applicationId) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM axon.application_draft WHERE application_id = ?",
        Integer.class,
        applicationId);
  }

  private String draftV2Url() {
    return "http://localhost:" + port + "/api/v2/application-drafts";
  }

  private String draftV2Url(UUID applicationId) {
    return draftV2Url() + "/" + applicationId;
  }

  private String submitV2Url(UUID applicationId) {
    return draftV2Url(applicationId) + "/submit";
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(TestJwtDecoderConfig.BEARER_TOKEN);
    return headers;
  }
}
