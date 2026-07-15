package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validLinkedCreateApplicationRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import uk.gov.justice.laa.dstew.access.command.application.ApplyApplicationIdClaimedEvent;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationCommand;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationCommandHandler;
import uk.gov.justice.laa.dstew.access.controller.application.CreateApplicationCommandMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.IndividualCreateRequest;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryReadRepository;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PostgresAxonIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private EventStorageEngine eventStorageEngine;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private ApplicationReadRepository applicationReadRepository;

  @Autowired private ApplicationHistoryReadRepository applicationHistoryReadRepository;

  @Autowired private CommandGateway commandGateway;

  @Autowired private CreateApplicationCommandMapper commandMapper;

  @MockitoSpyBean private CreateApplicationCommandHandler createApplicationCommandHandler;

  @Test
  void givenPostgresAxonStore_whenHealthRequested_thenReportsUp() {
    ResponseEntity<String> response =
        restTemplate.getForEntity("http://localhost:" + port + "/actuator/health", String.class);

    List<String> axonTables =
        jdbcTemplate.queryForList(
            """
            SELECT table_name
            FROM information_schema.tables
            WHERE table_schema = 'axon'
              AND table_name IN (
                'association_value_entry',
                'domain_event_entry',
                'saga_entry',
                'snapshot_event_entry'
              )
            ORDER BY table_name
            """,
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"status\":\"UP\"");
    assertThat(eventStorageEngine).isInstanceOf(JpaEventStorageEngine.class);
    assertThat(axonTables)
        .containsExactly(
            "association_value_entry", "domain_event_entry", "saga_entry", "snapshot_event_entry");
  }

  @Test
  void givenValidRequest_whenPostApplication_thenPersistsEventAndCurrentStateProjection()
      throws Exception {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applyProceedingId = UUID.randomUUID();
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "2");

    ResponseEntity<Void> response =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(applyApplicationId, applyProceedingId), headers),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    UUID applicationId =
        UUID.fromString(
            response.getHeaders().getLocation().getPath().replace("/api/v0/applications/", ""));

    ApplicationReadModel projected = awaitProjection(applicationId);
    assertThat(projected.getApplicationId()).isEqualTo(applicationId);
    assertThat(projected.getStatus()).isEqualTo("APPLICATION_SUBMITTED");
    assertThat(projected.getLaaReference()).isEqualTo("LAA-123");
    assertThat(projected.getSchemaVersion()).isEqualTo(2);
    assertThat(projected.getApplicationType()).isEqualTo("APPLY");
    assertThat(projected.getApplyApplicationId()).isEqualTo(applyApplicationId);
    assertThat(projected.getSubmittedAt()).isEqualTo(Instant.parse("2026-07-14T12:30:00Z"));
    assertThat(projected.getOfficeCode()).isEqualTo("1A001B");
    assertThat(projected.getUsedDelegatedFunctions()).isFalse();
    assertThat(projected.getCategoryOfLaw()).isEqualTo("FAMILY");
    assertThat(projected.getMatterType()).isEqualTo("SPECIAL_CHILDREN_ACT");
    assertThat(projected.getCreatedAt()).isNotNull().isEqualTo(projected.getModifiedAt());
    assertThat(projected.getApplicationContent().getId()).isEqualTo(applyApplicationId);
    assertThat(projected.getApplicationContent().getOffice().getCode()).isEqualTo("1A001B");
    assertThat(projected.getIndividuals())
        .singleElement()
        .satisfies(
            individual -> {
              assertThat(individual.individualId()).isNotNull();
              assertThat(individual.firstName()).isEqualTo("Ada");
              assertThat(individual.lastName()).isEqualTo("Lovelace");
              assertThat(individual.dateOfBirth()).isEqualTo(LocalDate.parse("1815-12-10"));
              assertThat(individual.individualContent())
                  .containsExactlyEntriesOf(Map.of("preferredName", "Ada"));
              assertThat(individual.type()).isEqualTo("CLIENT");
            });
    assertThat(projected.getProceedings())
        .singleElement()
        .satisfies(
            proceeding -> {
              assertThat(proceeding.proceedingId()).isNotNull();
              assertThat(proceeding.applyProceedingId()).isEqualTo(applyProceedingId);
              assertThat(proceeding.description()).isEqualTo("Care order");
              assertThat(proceeding.lead()).isTrue();
              assertThat(proceeding.proceedingContent().getId()).isEqualTo(applyProceedingId);
            });

    assertThat(awaitHistory(applicationId, 1))
        .singleElement()
        .satisfies(
            history -> {
              assertThat(history.getEventType()).isEqualTo("APPLICATION_CREATED");
              assertThat(history.getRequestPayload()).contains("\"laaReference\"", "\"LAA-123\"");
              assertThat(history.getServiceName()).isEqualTo("CIVIL_APPLY");
            });

    List<Map<String, Object>> events =
        jdbcTemplate.queryForList(
            "SELECT payload_type, sequence_number FROM axon.domain_event_entry "
                + "WHERE aggregate_identifier = ?",
            applicationId.toString());
    assertThat(events)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.get("payload_type"))
                  .isEqualTo(
                      "uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent");
              assertThat(event.get("sequence_number")).isEqualTo(0L);
            });

    List<Map<String, Object>> claimEvents =
        jdbcTemplate.queryForList(
            "SELECT payload_type, sequence_number FROM axon.domain_event_entry "
                + "WHERE aggregate_identifier = ?",
            applyApplicationId.toString());
    assertThat(claimEvents)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.get("payload_type"))
                  .isEqualTo(ApplyApplicationIdClaimedEvent.class.getName());
              assertThat(event.get("sequence_number")).isEqualTo(0L);
            });
  }

  @Test
  void givenApplicationFinalisationFails_whenClaimReleased_thenApplyIdCanBeRetried()
      throws Exception {
    UUID existingApplicationId = UUID.randomUUID();
    CreateApplicationCommand existingApplication =
        withApplicationId(
            commandMapper.toCommand(
                validCreateApplicationRequest(UUID.randomUUID(), UUID.randomUUID()), 1),
            existingApplicationId);
    commandGateway.sendAndWait(existingApplication);
    awaitProjection(existingApplicationId);

    UUID unclaimedApplyApplicationId = UUID.randomUUID();
    CreateApplicationCommand conflictingApplication =
        withApplicationId(
            commandMapper.toCommand(
                validCreateApplicationRequest(unclaimedApplyApplicationId, UUID.randomUUID()), 1),
            existingApplicationId);

    commandGateway.sendAndWait(conflictingApplication);
    awaitClaimEventCount(unclaimedApplyApplicationId, 2);

    CreateApplicationCommand retry =
        commandMapper.toCommand(
            validCreateApplicationRequest(unclaimedApplyApplicationId, UUID.randomUUID()), 1);
    commandGateway.sendAndWait(retry);
    awaitProjection(retry.applicationId());

    List<String> claimEventTypes =
        jdbcTemplate.queryForList(
            "SELECT payload_type FROM axon.domain_event_entry "
                + "WHERE aggregate_identifier = ? ORDER BY sequence_number",
            String.class,
            unclaimedApplyApplicationId.toString());
    assertThat(claimEventTypes)
        .containsExactly(
            ApplyApplicationIdClaimedEvent.class.getName(),
            "uk.gov.justice.laa.dstew.access.command.application.ApplyApplicationIdReleasedEvent",
            ApplyApplicationIdClaimedEvent.class.getName());
  }

  @Test
  void givenClaimedApplyApplicationId_whenPostApplicationAgain_thenReturnsBadRequest() {
    UUID applyApplicationId = UUID.randomUUID();
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    HttpEntity<?> request =
        new HttpEntity<>(
            validCreateApplicationRequest(applyApplicationId, UUID.randomUUID()), headers);

    ResponseEntity<String> firstResponse =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/applications", request, String.class);
    ResponseEntity<String> duplicateResponse =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/applications", request, String.class);

    assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(duplicateResponse.getBody())
        .contains("Application already exists for Apply Application Id: " + applyApplicationId);
    Integer claimEventCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM axon.domain_event_entry WHERE aggregate_identifier = ?",
            Integer.class,
            applyApplicationId.toString());
    assertThat(claimEventCount).isEqualTo(1);
  }

  @Test
  void givenExistingLeadApplication_whenPostLinkedApplication_thenProjectsCurrentStateAndHistory()
      throws Exception {
    UUID leadApplyApplicationId = UUID.randomUUID();
    ResponseEntity<Void> leadResponse =
        post(validCreateApplicationRequest(leadApplyApplicationId, UUID.randomUUID()), headers());
    UUID leadApplicationId = applicationId(leadResponse);
    awaitProjection(leadApplicationId);

    ResponseEntity<Void> linkedResponse =
        post(
            validLinkedCreateApplicationRequest(
                UUID.randomUUID(), UUID.randomUUID(), leadApplyApplicationId),
            headers());
    UUID linkedApplicationId = applicationId(linkedResponse);

    assertThat(awaitProjection(linkedApplicationId).getLeadApplicationId())
        .isEqualTo(leadApplicationId);
    assertThat(awaitHistory(linkedApplicationId, 2))
        .extracting(history -> history.getEventType())
        .containsExactly("APPLICATION_CREATED", "APPLICATION_LINKED");
  }

  @Test
  void givenMissingLeadApplication_whenPostApplication_thenReturnsNotFound() {
    UUID missingLeadApplyApplicationId = UUID.randomUUID();

    ResponseEntity<String> response =
        post(
            validLinkedCreateApplicationRequest(
                UUID.randomUUID(), UUID.randomUUID(), missingLeadApplyApplicationId),
            headers(),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).contains(missingLeadApplyApplicationId.toString());
  }

  @Test
  void givenMissingAssociatedApplication_whenPostApplication_thenReturnsNotFound()
      throws Exception {
    UUID leadApplyApplicationId = UUID.randomUUID();
    UUID leadApplicationId =
        applicationId(
            post(
                validCreateApplicationRequest(leadApplyApplicationId, UUID.randomUUID()),
                headers()));
    awaitProjection(leadApplicationId);

    UUID missingAssociatedApplyApplicationId = UUID.randomUUID();
    ResponseEntity<String> response =
        post(
            validLinkedCreateApplicationRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                leadApplyApplicationId,
                missingAssociatedApplyApplicationId),
            headers(),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).contains(missingAssociatedApplyApplicationId.toString());
  }

  @Test
  void givenSchemaInvalidRequest_whenPostApplication_thenReturnsBadRequestBeforeHandlerRuns()
      throws Exception {
    ApplicationCreateRequest request =
        validCreateApplicationRequest(UUID.randomUUID(), UUID.randomUUID());
    Map<String, Object> invalidContent = new HashMap<>(request.getApplicationContent());
    invalidContent.remove("id");
    request.setApplicationContent(invalidContent);
    clearInvocations(createApplicationCommandHandler);

    ResponseEntity<String> response = post(request, headers(), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("Generic Validation Error");
    verify(createApplicationCommandHandler, never()).handle(any());
  }

  @Test
  void givenContentWithoutLeadProceeding_whenPostApplication_thenReturnsBadRequest() {
    ApplicationCreateRequest request =
        validCreateApplicationRequest(UUID.randomUUID(), UUID.randomUUID());
    Map<String, Object> content = new HashMap<>(request.getApplicationContent());
    Map<String, Object> proceeding = firstProceeding(content);
    proceeding.put("leadProceeding", false);
    content.put("proceedings", List.of(proceeding));
    request.setApplicationContent(content);

    ResponseEntity<String> response = post(request, headers(1), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("No lead proceeding found in application content");
  }

  @Test
  void givenUnparseableSubmissionTimestamp_whenPostApplication_thenReturnsBadRequest() {
    ApplicationCreateRequest request =
        validCreateApplicationRequest(UUID.randomUUID(), UUID.randomUUID());
    Map<String, Object> content = new HashMap<>(request.getApplicationContent());
    content.put("submittedAt", "not-an-instant");
    request.setApplicationContent(content);

    ResponseEntity<String> response = post(request, headers(), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("submittedAt: must be an ISO-8601 instant");
  }

  @Test
  void givenInvalidContentType_whenPostApplication_thenReturnsBadRequest() {
    ApplicationCreateRequest request =
        validCreateApplicationRequest(UUID.randomUUID(), UUID.randomUUID());
    Map<String, Object> content = new HashMap<>(request.getApplicationContent());
    Map<String, Object> proceeding = firstProceeding(content);
    proceeding.put("substantiveCostLimitation", "not-a-number");
    content.put("proceedings", List.of(proceeding));
    request.setApplicationContent(content);

    ResponseEntity<String> response = post(request, headers(), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("substantiveCostLimitation");
  }

  @Test
  void givenBeanInvalidRequest_whenPostApplication_thenReturnsBadRequest() {
    ApplicationCreateRequest request =
        validCreateApplicationRequest(UUID.randomUUID(), UUID.randomUUID());
    IndividualCreateRequest invalidIndividual = request.getIndividuals().getFirst();
    invalidIndividual.setType(null);

    ResponseEntity<String> response = post(request, headers(), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  private CreateApplicationCommand withApplicationId(
      CreateApplicationCommand command, UUID applicationId) {
    return new CreateApplicationCommand(
        applicationId,
        command.status(),
        command.laaReference(),
        command.applicationContent(),
        command.individuals(),
        command.serialisedRequest(),
        command.schemaVersion(),
        command.schemaName(),
        command.applicationType());
  }

  private ResponseEntity<Void> post(ApplicationCreateRequest request, HttpHeaders headers) {
    return restTemplate.postForEntity(
        "http://localhost:" + port + "/api/v0/applications",
        new HttpEntity<>(request, headers),
        Void.class);
  }

  private ResponseEntity<String> post(
      ApplicationCreateRequest request, HttpHeaders headers, Class<String> responseType) {
    return restTemplate.postForEntity(
        "http://localhost:" + port + "/api/v0/applications",
        new HttpEntity<>(request, headers),
        responseType);
  }

  private HttpHeaders headers() {
    return headers(2);
  }

  private HttpHeaders headers(int schemaVersion) {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", String.valueOf(schemaVersion));
    return headers;
  }

  private UUID applicationId(ResponseEntity<Void> response) {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getLocation()).isNotNull();
    return UUID.fromString(
        response.getHeaders().getLocation().getPath().replace("/api/v0/applications/", ""));
  }

  private Map<String, Object> firstProceeding(Map<String, Object> applicationContent) {
    Map<?, ?> source = (Map<?, ?>) ((List<?>) applicationContent.get("proceedings")).getFirst();
    Map<String, Object> proceeding = new HashMap<>();
    source.forEach((key, value) -> proceeding.put(key.toString(), value));
    return proceeding;
  }

  private void awaitClaimEventCount(UUID applyApplicationId, int expectedCount) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
    while (System.nanoTime() < deadline) {
      Integer count =
          jdbcTemplate.queryForObject(
              "SELECT COUNT(*) FROM axon.domain_event_entry WHERE aggregate_identifier = ?",
              Integer.class,
              applyApplicationId.toString());
      if (count != null && count == expectedCount) {
        return;
      }
      Thread.sleep(100);
    }
    throw new AssertionError(
        "Claim event count did not reach " + expectedCount + " for " + applyApplicationId);
  }

  private ApplicationReadModel awaitProjection(UUID applicationId) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
    while (System.nanoTime() < deadline) {
      var projected = applicationReadRepository.findById(applicationId);
      if (projected.isPresent()) {
        return projected.get();
      }
      Thread.sleep(100);
    }
    throw new AssertionError("Application projection was not populated for " + applicationId);
  }

  private java.util.List<
          uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryReadModel>
      awaitHistory(UUID applicationId, int expectedCount) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      var history =
          applicationHistoryReadRepository.findAllByApplicationIdOrderByOccurredAtAsc(
              applicationId);
      if (history.size() == expectedCount) {
        return history;
      }
      Thread.sleep(50);
    }
    throw new AssertionError(
        "Application history projection was not populated for " + applicationId);
  }
}
