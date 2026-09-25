package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validApplicationContent;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
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
import uk.gov.justice.laa.dstew.access.model.SaveApplicationDraftRequest;
import uk.gov.justice.laa.dstew.access.model.SaveApplicationDraftResponse;
import uk.gov.justice.laa.dstew.access.model.SubmitApplicationDraftResponse;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;
import util.ProjectionAwaiter;

/** Full HTTP/Postgres/Axon integration tests for the Application draft/submit lifecycle. */
@Testcontainers
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"feature.enable-dev-token=true"})
@AutoConfigureTestRestTemplate
@Import(TestJwtDecoderConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApplicationDraftIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private QueryGateway queryGateway;

  private ProjectionAwaiter projectionAwaiter;

  @PostConstruct
  void initialiseProjectionAwaiter() {
    projectionAwaiter = new ProjectionAwaiter(queryGateway);
  }

  @Test
  void givenValidContent_whenSaveApplicationDraft_thenPersistsDraftAndReturnsCreated() {
    SaveApplicationDraftRequest request =
        SaveApplicationDraftRequest.builder()
            .status(ApplicationStatus.APPLICATION_SUBMITTED)
            .laaReference("LAA-123")
            .applicationContent(Map.of())
            .build();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            saveDraftUrl(), new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    SaveApplicationDraftResponse body =
        objectMapper.readValue(response.getBody(), SaveApplicationDraftResponse.class);
    UUID applicationId = body.getApplicationId();
    assertThat(applicationId).isNotNull();
    assertThat(body.getSavedAt()).isNotNull();

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.application_draft WHERE application_id = ?",
                Integer.class,
                applicationId))
        .isEqualTo(1);

    ResponseEntity<String> getResponse =
        restTemplate.exchange(
            applicationUrl(applicationId),
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            String.class);
    assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void givenExistingDraft_whenUpdateApplicationDraft_thenReturns204AndPersistsUpdatedContent() {
    UUID applicationId = saveDraft("LAA-123", Map.of());

    SaveApplicationDraftRequest updateRequest =
        SaveApplicationDraftRequest.builder()
            .laaReference("LAA-999")
            .applicationContent(Map.of())
            .build();
    ResponseEntity<Void> updateResponse =
        restTemplate.exchange(
            draftUrl(applicationId),
            HttpMethod.PUT,
            new HttpEntity<>(updateRequest, headers()),
            Void.class);

    assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT payload ->> 'laaReference' FROM axon.application_draft"
                    + " WHERE application_id = ?",
                String.class,
                applicationId))
        .isEqualTo("LAA-999");
  }

  @Test
  void givenNoDraft_whenUpdateApplicationDraft_thenReturnsNotFound() {
    UUID applicationId = UUID.randomUUID();
    SaveApplicationDraftRequest updateRequest =
        SaveApplicationDraftRequest.builder()
            .laaReference("LAA-999")
            .applicationContent(Map.of())
            .build();

    ResponseEntity<String> updateResponse =
        restTemplate.exchange(
            draftUrl(applicationId),
            HttpMethod.PUT,
            new HttpEntity<>(updateRequest, headers()),
            String.class);

    assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void givenDraftInProgress_whenSubmitApplicationDraft_thenCreatesApplicationAndDeletesDraft() {
    UUID applyProceedingId = UUID.randomUUID();
    UUID applicationId = saveDraftPendingId();
    Map<String, Object> content = validApplicationContent(applicationId, applyProceedingId);
    SaveApplicationDraftRequest updateRequest =
        SaveApplicationDraftRequest.builder()
            .status(ApplicationStatus.APPLICATION_SUBMITTED)
            .laaReference("LAA-123")
            .applicationContent(content)
            .build();
    restTemplate.exchange(
        draftUrl(applicationId),
        HttpMethod.PUT,
        new HttpEntity<>(updateRequest, headers()),
        Void.class);

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            submitUrl(applicationId), new HttpEntity<>(null, headers()), String.class);

    assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.ACCEPTED);
    SubmitApplicationDraftResponse body =
        objectMapper.readValue(response.getBody(), SubmitApplicationDraftResponse.class);
    assertThat(body.getApplicationId()).isEqualTo(applicationId);
    assertThat(body.getSubmittedAt()).isNotNull();

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.application_draft WHERE application_id = ?",
                Integer.class,
                applicationId))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.application_data"
                    + " WHERE application_id = ? AND version = 0",
                Integer.class,
                applicationId))
        .isEqualTo(1);

    var application = projectionAwaiter.awaitApplication(applicationId);
    assertThat(application.getStatus()).isEqualTo("APPLICATION_SUBMITTED");
    assertThat(application.getLaaReference()).isEqualTo("LAA-123");
  }

  @Test
  void givenNoDraft_whenSubmitApplicationDraft_thenReturnsNotFound() {
    UUID applicationId = UUID.randomUUID();

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            submitUrl(applicationId), new HttpEntity<>(null, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void givenSchemaInvalidDraft_whenSubmitApplicationDraft_thenReturnsBadRequestAndDraftPersists() {
    UUID applyProceedingId = UUID.randomUUID();
    UUID applicationId = saveDraftPendingId();
    Map<String, Object> content =
        new HashMap<>(validApplicationContent(applicationId, applyProceedingId));
    content.remove("submittedAt");
    SaveApplicationDraftRequest updateRequest =
        SaveApplicationDraftRequest.builder()
            .status(ApplicationStatus.APPLICATION_SUBMITTED)
            .laaReference("LAA-123")
            .applicationContent(content)
            .build();
    restTemplate.exchange(
        draftUrl(applicationId),
        HttpMethod.PUT,
        new HttpEntity<>(updateRequest, headers()),
        Void.class);

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            submitUrl(applicationId), new HttpEntity<>(null, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.application_draft WHERE application_id = ?",
                Integer.class,
                applicationId))
        .isEqualTo(1);
  }

  private UUID saveDraft(String laaReference, Map<String, Object> content) {
    SaveApplicationDraftRequest request =
        SaveApplicationDraftRequest.builder()
            .status(ApplicationStatus.APPLICATION_SUBMITTED)
            .laaReference(laaReference)
            .applicationContent(content)
            .build();
    ResponseEntity<String> response =
        restTemplate.postForEntity(
            saveDraftUrl(), new HttpEntity<>(request, headers()), String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    return objectMapper
        .readValue(response.getBody(), SaveApplicationDraftResponse.class)
        .getApplicationId();
  }

  private UUID saveDraftPendingId() {
    return saveDraft("LAA-123", Map.of());
  }

  private String saveDraftUrl() {
    return "http://localhost:" + port + "/api/v0/application-drafts";
  }

  private String draftUrl(UUID applicationId) {
    return saveDraftUrl() + "/" + applicationId;
  }

  private String submitUrl(UUID applicationId) {
    return draftUrl(applicationId) + "/submit";
  }

  private String applicationUrl(UUID applicationId) {
    return "http://localhost:" + port + "/api/v0/applications/" + applicationId;
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(TestJwtDecoderConfig.BEARER_TOKEN);
    return headers;
  }
}
