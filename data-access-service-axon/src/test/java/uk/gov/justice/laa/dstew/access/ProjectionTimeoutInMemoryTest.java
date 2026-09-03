package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.messaging.eventhandling.processing.streaming.StreamingEventProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import uk.gov.justice.laa.dstew.access.command.caseworker.Caseworker;
import uk.gov.justice.laa.dstew.access.command.caseworker.CaseworkerRepository;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceedingRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.ManualOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetailsRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;
import uk.gov.justice.laa.dstew.access.model.WorkListAssignRequest;
import uk.gov.justice.laa.dstew.access.testsupport.TestJwtDecoderConfig;

/**
 * Verifies that when the application-projection tracking processor is stopped the controller
 * returns 202 Accepted with a deterministic Location header after the short configured timeout, and
 * does not block for the full default five-second projection wait.
 *
 * <p>Uses a separate Spring context with a 200 ms projection timeout so the test completes in well
 * under one second. {@code @DirtiesContext} discards the stopped processor after the class so it
 * cannot leak into other test contexts.
 */
@SpringBootTest(
    classes = DataAccessServiceAxonApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.properties.hibernate.default_schema=PUBLIC",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "spring.datasource.url=jdbc:h2:mem:axon-timeout;DB_CLOSE_DELAY=-1",
      "application.projection.timeout=200ms"
    })
@AutoConfigureTestRestTemplate
@Import(TestJwtDecoderConfig.class)
@DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
class ProjectionTimeoutInMemoryTest {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private AxonConfiguration axonConfiguration;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private CaseworkerRepository caseworkers;

  @Test
  void givenStoppedProjectionProcessor_whenPostApplication_thenReturnsAcceptedWithLocation() {
    // Stop the projection processor so no QueryUpdateEmitter.emit can be called for this command.
    StreamingEventProcessor processor =
        axonConfiguration
            .getComponents(StreamingEventProcessor.class)
            .get("application-projection");
    processor.shutdown().join();

    UUID applicationId = UUID.randomUUID();
    ApplicationCreateRequest request =
        validCreateApplicationRequest(applicationId, UUID.randomUUID());
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "1");
    headers.setBearerAuth(TestJwtDecoderConfig.BEARER_TOKEN);

    long startMs = System.currentTimeMillis();
    ResponseEntity<Void> response =
        restTemplate.postForEntity(
            "/api/v0/applications", new HttpEntity<>(request, headers), Void.class);
    long elapsedMs = System.currentTimeMillis() - startMs;

    // Command committed → 202 because projection never appeared within the 200 ms timeout.
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getLocation().getPath())
        .isEqualTo("/api/v0/applications/" + applicationId);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM domain_event_entry "
                    + "WHERE aggregate_identifier = ? AND sequence_number = 0",
                Integer.class,
                applicationId.toString()))
        .isEqualTo(1);

    // The test must complete well below the full 5-second default timeout.
    assertThat(elapsedMs).isLessThan(3_000L);

    CompletableFuture<Void> restart =
        CompletableFuture.runAsync(
            () -> processor.start().join(),
            CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS));
    ResponseEntity<ApplicationResponse> directRead =
        restTemplate.exchange(
            "/api/v0/applications/" + applicationId,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            ApplicationResponse.class);

    restart.join();
    assertThat(directRead.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(directRead.getBody()).isNotNull();
    assertThat(directRead.getBody().getApplicationId()).isEqualTo(applicationId);
  }

  @Test
  void givenProjectionLagAfterManualReadiness_whenDeliveryIsRepeated_thenNoSecondEventIsAppended() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreateRequest createRequest =
        validCreateApplicationRequest(applicationId, UUID.randomUUID());
    HttpHeaders headers = headers();
    assertThat(
            restTemplate
                .postForEntity(
                    "/api/v0/applications", new HttpEntity<>(createRequest, headers), Void.class)
                .getStatusCode())
        .isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);
    awaitApplicationProjection(applicationId, headers);

    StreamingEventProcessor processor =
        axonConfiguration
            .getComponents(StreamingEventProcessor.class)
            .get("application-projection");
    processor.shutdown().join();
    ManualOutcomeRequest readyRequest = new ManualOutcomeRequest(AutoGrantOutcome.MANUAL);
    HttpEntity<ManualOutcomeRequest> readyEntity = new HttpEntity<>(readyRequest, headers);

    ResponseEntity<Void> first =
        restTemplate.exchange(
            "/api/v0/applications/" + applicationId + "/auto-grant-outcome",
            HttpMethod.PATCH,
            readyEntity,
            Void.class);
    ResponseEntity<ApplicationResponse> staleRead =
        restTemplate.exchange(
            "/api/v0/applications/" + applicationId,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            ApplicationResponse.class);
    ResponseEntity<Void> repeated =
        restTemplate.exchange(
            "/api/v0/applications/" + applicationId + "/auto-grant-outcome",
            HttpMethod.PATCH,
            readyEntity,
            Void.class);

    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(staleRead.getBody()).isNotNull();
    assertThat(staleRead.getBody().getAutoGranted())
        .isEqualTo(uk.gov.justice.laa.dstew.access.model.AutoGranted.PENDING);
    assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM domain_event_entry WHERE aggregate_identifier = ?",
                Integer.class,
                applicationId.toString()))
        .isEqualTo(2);

    processor.start().join();
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              ResponseEntity<ApplicationResponse> currentRead =
                  restTemplate.exchange(
                      "/api/v0/applications/" + applicationId,
                      HttpMethod.GET,
                      new HttpEntity<>(headers),
                      ApplicationResponse.class);
              assertThat(currentRead.getBody()).isNotNull();
              assertThat(currentRead.getBody().getAutoGranted())
                  .isEqualTo(uk.gov.justice.laa.dstew.access.model.AutoGranted.MANUAL);
              assertThat(currentRead.getBody().getVersion()).isEqualTo(1L);
            });
  }

  @Test
  void givenProjectionLagAfterDecision_whenDeliveryIsRepeated_thenConflictDoesNotAppendAnEvent() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreateRequest createRequest =
        validCreateApplicationRequest(applicationId, UUID.randomUUID());
    HttpHeaders headers = headers();
    assertThat(
            restTemplate
                .postForEntity(
                    "/api/v0/applications", new HttpEntity<>(createRequest, headers), Void.class)
                .getStatusCode())
        .isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);
    awaitApplicationProjection(applicationId, headers);
    ApplicationResponse created =
        restTemplate
            .exchange(
                "/api/v0/applications/" + applicationId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                ApplicationResponse.class)
            .getBody();
    assertThat(created).isNotNull();
    UUID proceedingId = created.getProceedings().getFirst().getProceedingId();

    assertThat(
            restTemplate
                .exchange(
                    "/api/v0/applications/" + applicationId + "/auto-grant-outcome",
                    HttpMethod.PATCH,
                    new HttpEntity<>(new ManualOutcomeRequest(AutoGrantOutcome.MANUAL), headers),
                    Void.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.NO_CONTENT);
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              ResponseEntity<ApplicationResponse> currentRead =
                  restTemplate.exchange(
                      "/api/v0/applications/" + applicationId,
                      HttpMethod.GET,
                      new HttpEntity<>(headers),
                      ApplicationResponse.class);
              assertThat(currentRead.getBody()).isNotNull();
              assertThat(currentRead.getBody().getAutoGranted())
                  .isEqualTo(uk.gov.justice.laa.dstew.access.model.AutoGranted.MANUAL);
              assertThat(currentRead.getBody().getVersion()).isEqualTo(1L);
            });

    StreamingEventProcessor processor =
        axonConfiguration
            .getComponents(StreamingEventProcessor.class)
            .get("application-projection");
    processor.shutdown().join();
    UUID caseworkerId = UUID.randomUUID();
    caseworkers.save(new Caseworker(caseworkerId, "timeout-decider@example.com"));
    assertThat(
            restTemplate
                .exchange(
                    "/api/v0/work-list/" + applicationId + "/assign",
                    HttpMethod.POST,
                    new HttpEntity<>(new WorkListAssignRequest(caseworkerId, 0L), headers),
                    Void.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.OK);
    MakeDecisionRequest decisionRequest =
        MakeDecisionRequest.builder()
            .expectedApplicationVersion(1L)
            .caseworkerId(caseworkerId)
            .overallDecision(DecisionStatus.GRANTED)
            .certificate(
                Map.of(
                    "certificateNumber", "AUTO-2099",
                    "issueDate", "2026-08-04",
                    "validUntil", "2027-08-04"))
            .eventHistory(
                EventHistoryRequest.builder().eventDescription("Decision recorded").build())
            .proceedings(
                List.of(
                    MakeDecisionProceedingRequest.builder()
                        .proceedingId(proceedingId)
                        .meritsDecision(
                            MeritsDecisionDetailsRequest.builder()
                                .decision(MeritsDecisionStatus.GRANTED)
                                .justification("Passed automatic assessment")
                                .build())
                        .build()))
            .build();
    HttpEntity<MakeDecisionRequest> decisionEntity = new HttpEntity<>(decisionRequest, headers);

    ResponseEntity<Void> first =
        restTemplate.exchange(
            "/api/v0/applications/" + applicationId + "/decision",
            HttpMethod.PATCH,
            decisionEntity,
            Void.class);
    ResponseEntity<ApplicationResponse> staleRead =
        restTemplate.exchange(
            "/api/v0/applications/" + applicationId,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            ApplicationResponse.class);
    ResponseEntity<Void> repeated =
        restTemplate.exchange(
            "/api/v0/applications/" + applicationId + "/decision",
            HttpMethod.PATCH,
            decisionEntity,
            Void.class);

    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(staleRead.getBody()).isNotNull();
    assertThat(staleRead.getBody().getAutoGranted())
        .isEqualTo(uk.gov.justice.laa.dstew.access.model.AutoGranted.MANUAL);
    assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM domain_event_entry WHERE aggregate_identifier = ?",
                Integer.class,
                applicationId.toString()))
        .isEqualTo(4);

    processor.start().join();
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              ResponseEntity<ApplicationResponse> currentRead =
                  restTemplate.exchange(
                      "/api/v0/applications/" + applicationId,
                      HttpMethod.GET,
                      new HttpEntity<>(headers),
                      ApplicationResponse.class);
              assertThat(currentRead.getBody()).isNotNull();
              assertThat(currentRead.getBody().getAutoGranted())
                  .isEqualTo(uk.gov.justice.laa.dstew.access.model.AutoGranted.MANUAL);
              assertThat(currentRead.getBody().getVersion()).isEqualTo(2L);
            });
  }

  private void awaitApplicationProjection(UUID applicationId, HttpHeaders headers) {
    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () ->
                assertThat(
                        restTemplate.exchange(
                            "/api/v0/applications/" + applicationId,
                            HttpMethod.GET,
                            new HttpEntity<>(headers),
                            String.class))
                    .extracting(ResponseEntity::getStatusCode)
                    .isEqualTo(HttpStatus.OK));
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "1");
    headers.setBearerAuth(TestJwtDecoderConfig.BEARER_TOKEN);
    return headers;
  }
}
