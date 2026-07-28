package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadRepository;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ApplicationPostgresIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private ApplicationReadRepository applicationReadRepository;

  @Test
  void givenStoredDraft_whenSubmitted_thenReturns204AndProjectsState() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applyProceedingId = UUID.randomUUID();

    ResponseEntity<Void> putResponse =
        putDraft(
            applyApplicationId,
            validCreateApplicationRequest(applyApplicationId, applyProceedingId));
    assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ResponseEntity<Void> submitResponse = submit(applyApplicationId);
    assertThat(submitResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // Projection is synchronous — available immediately
    ApplicationReadModel projected =
        applicationReadRepository
            .findById(applyApplicationId)
            .orElseThrow(
                () -> new AssertionError("Projection not found for " + applyApplicationId));

    assertThat(projected.getApplyApplicationId()).isEqualTo(applyApplicationId);
    assertThat(projected.getStatus()).isEqualTo(ApplicationStatus.APPLICATION_SUBMITTED.name());
    assertThat(projected.getLaaReference()).isEqualTo("LAA-123");
    assertThat(projected.getOfficeCode()).isEqualTo("1A001B");
    assertThat(projected.getSubmittedAt()).isEqualTo(Instant.parse("2026-07-14T12:30:00Z"));
    assertThat(projected.getCreatedAt()).isNotNull();
  }

  @Test
  void givenSubmittedApplication_whenGet_thenReturns200WithBody() throws Exception {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applyProceedingId = UUID.randomUUID();
    putDraft(
        applyApplicationId, validCreateApplicationRequest(applyApplicationId, applyProceedingId));
    submit(applyApplicationId);

    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/v0/applications/" + applyApplicationId, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    ApplicationResponse body =
        objectMapper.readValue(response.getBody(), ApplicationResponse.class);
    assertThat(body.getApplicationId()).isEqualTo(applyApplicationId);
    assertThat(body.getStatus()).isEqualTo(ApplicationStatus.APPLICATION_SUBMITTED);
    assertThat(body.getLaaReference()).isEqualTo("LAA-123");
    // Rich detail is rebuilt from the submissions payload store, not the metadata read model.
    assertThat(body.getProceedings()).isNotEmpty();
    assertThat(body.getProceedings().get(0).getProceedingDescription()).isEqualTo("Care order");
  }

  @Test
  void givenUnknownId_whenGet_thenReturnsNotFound() {
    UUID unknownId = UUID.randomUUID();

    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/v0/applications/" + unknownId, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void givenNoDraft_whenSubmitted_thenReturnsNotFound() {
    UUID unknownId = UUID.randomUUID();

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v0/applications/" + unknownId + "/submit",
            HttpMethod.POST,
            new HttpEntity<>(null, headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  private ResponseEntity<Void> putDraft(UUID id, ApplicationCreateRequest request) {
    return restTemplate.exchange(
        "/api/v0/applications/" + id,
        HttpMethod.PUT,
        new HttpEntity<>(request, headers()),
        Void.class);
  }

  private ResponseEntity<Void> submit(UUID id) {
    return restTemplate.exchange(
        "/api/v0/applications/" + id + "/submit",
        HttpMethod.POST,
        new HttpEntity<>(null, headers()),
        Void.class);
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "2");
    return headers;
  }
}
