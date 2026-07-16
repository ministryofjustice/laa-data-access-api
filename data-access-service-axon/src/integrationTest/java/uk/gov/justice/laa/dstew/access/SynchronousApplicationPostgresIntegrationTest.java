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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.query.synchronousapplication.SynchronousApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.synchronousapplication.SynchronousApplicationReadRepository;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SynchronousApplicationPostgresIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private SynchronousApplicationReadRepository synchronousApplicationReadRepository;

  @Test
  void givenValidRequest_whenPost_thenReturns201WithLocationAndProjectsState() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applyProceedingId = UUID.randomUUID();

    ResponseEntity<Void> response =
        restTemplate.postForEntity(
            "/api/v0/synchronous-applications",
            new HttpEntity<>(
                validCreateApplicationRequest(applyApplicationId, applyProceedingId), headers()),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getHeaders().getLocation()).isNotNull();
    String locationPath = response.getHeaders().getLocation().getPath();
    assertThat(locationPath).isEqualTo("/api/v0/synchronous-applications/" + applyApplicationId);

    // Projection is synchronous — available immediately
    SynchronousApplicationReadModel projected =
        synchronousApplicationReadRepository
            .findById(applyApplicationId)
            .orElseThrow(
                () -> new AssertionError("Projection not found for " + applyApplicationId));

    assertThat(projected.getApplyApplicationId()).isEqualTo(applyApplicationId);
    assertThat(projected.getStatus()).isEqualTo(ApplicationStatus.APPLICATION_SUBMITTED.name());
    assertThat(projected.getLaaReference()).isEqualTo("LAA-123");
    assertThat(projected.getOfficeCode()).isEqualTo("1A001B");
    assertThat(projected.getSubmittedAt()).isEqualTo(Instant.parse("2026-07-14T12:30:00Z"));
    assertThat(projected.getCreatedAt()).isNotNull();
    assertThat(projected.getIndividuals()).hasSize(1);
    assertThat(projected.getProceedings()).hasSize(1);
  }

  @Test
  void givenCreatedApplication_whenGet_thenReturns200WithBody() throws Exception {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applyProceedingId = UUID.randomUUID();
    restTemplate.postForEntity(
        "/api/v0/synchronous-applications",
        new HttpEntity<>(
            validCreateApplicationRequest(applyApplicationId, applyProceedingId), headers()),
        Void.class);

    ResponseEntity<String> response =
        restTemplate.getForEntity(
            "/api/v0/synchronous-applications/" + applyApplicationId, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    ApplicationResponse body =
        objectMapper.readValue(response.getBody(), ApplicationResponse.class);
    assertThat(body.getApplicationId()).isEqualTo(applyApplicationId);
    assertThat(body.getStatus()).isEqualTo(ApplicationStatus.APPLICATION_SUBMITTED);
    assertThat(body.getLaaReference()).isEqualTo("LAA-123");
  }

  @Test
  void givenUnknownId_whenGet_thenReturnsNotFound() {
    UUID unknownId = UUID.randomUUID();

    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/v0/synchronous-applications/" + unknownId, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }

  @Test
  void givenDuplicateApplyApplicationId_whenPostAgain_thenReturnsIdempotent201() {
    // Sequential re-delivery is idempotent with CREATE_IF_MISSING — both calls return 201.
    UUID applyApplicationId = UUID.randomUUID();
    HttpEntity<?> request =
        new HttpEntity<>(
            validCreateApplicationRequest(applyApplicationId, UUID.randomUUID()), headers());

    ResponseEntity<String> firstResponse =
        restTemplate.postForEntity("/api/v0/synchronous-applications", request, String.class);
    ResponseEntity<String> duplicateResponse =
        restTemplate.postForEntity("/api/v0/synchronous-applications", request, String.class);

    assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "2");
    return headers;
  }
}
