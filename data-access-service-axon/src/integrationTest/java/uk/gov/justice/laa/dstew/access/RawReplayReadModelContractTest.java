package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.replay.ApplicationRawReplayService;

/**
 * Contract test: verifies that {@link ApplicationRawReplayService} can reconstruct an {@link
 * ApplicationReadModel} directly from {@code domain_event_entry} and {@code application_data},
 * without going through the Axon query API — both as a service call and via the {@code /raw-replay}
 * diagnostic endpoint.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RawReplayReadModelContractTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private ApplicationRawReplayService rawReplayService;

  @Test
  void givenCreatedApplication_whenReplayedViaService_thenReadModelMatchesOriginal() {
    UUID applicationId = createApplication();

    Optional<ApplicationReadModel> replayed = rawReplayService.replay(applicationId);

    assertThat(replayed).isPresent();
    ApplicationReadModel model = replayed.get();
    assertThat(model.getApplicationId()).isEqualTo(applicationId);
    assertThat(model.getApplicationVersion()).isEqualTo(0L);
    assertThat(model.getApplicationDataVersion()).isEqualTo(0L);
    assertThat(model.getStatus()).isNotNull();
    assertThat(model.getLaaReference()).isEqualTo("LAA-123");
    assertThat(model.getApplicationContent()).isNotNull();
    assertThat(model.getIndividuals()).hasSize(1);
  }

  @Test
  void givenCreatedApplication_whenReplayedViaEndpoint_thenResponseMatchesOriginal() {
    UUID applicationId = createApplication();

    ResponseEntity<ApplicationResponse> response =
        restTemplate.exchange(
            "http://localhost:" + port + "/api/v0/applications/" + applicationId + "/raw-replay",
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            ApplicationResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getApplicationId()).isEqualTo(applicationId);
    assertThat(response.getBody().getLaaReference()).isEqualTo("LAA-123");
  }

  /** Creates an Application and waits for its creation event to be persisted. */
  private UUID createApplication() {
    UUID applicationId = UUID.randomUUID();
    UUID applyProceedingId = UUID.randomUUID();
    ResponseEntity<Void> response =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(applicationId, applyProceedingId), headers()),
            Void.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

    // Poll until the event is persisted (projection may be async but event storage is synchronous).
    org.awaitility.Awaitility.await()
        .atMost(10, java.util.concurrent.TimeUnit.SECONDS)
        .until(
            () ->
                jdbcTemplate.queryForList(
                    "SELECT 1 FROM axon.domain_event_entry WHERE aggregate_identifier = ?",
                    applicationId.toString()),
            list -> !list.isEmpty());
    return applicationId;
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "2");
    return headers;
  }
}
