package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validLinkedCreateApplicationRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.axonframework.eventsourcing.eventstore.EventStorageEngine;
import org.axonframework.eventsourcing.eventstore.jpa.JpaEventStorageEngine;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationProceedingResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationType;
import uk.gov.justice.laa.dstew.access.model.CategoryOfLaw;
import uk.gov.justice.laa.dstew.access.model.IndividualCreateRequest;
import uk.gov.justice.laa.dstew.access.model.InvolvedChildResponse;
import uk.gov.justice.laa.dstew.access.model.MatterType;
import uk.gov.justice.laa.dstew.access.model.OpponentResponse;
import uk.gov.justice.laa.dstew.access.model.ProviderResponse;
import uk.gov.justice.laa.dstew.access.model.ScopeLimitationResponse;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.FindApplicationByIdQuery;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryReadModel;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadRepository;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PostgresAxonIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

  @LocalServerPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private EventStorageEngine eventStorageEngine;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Autowired private ApplicationReadRepository applicationReadRepository;

  @Autowired private ApplicationHistoryReadRepository applicationHistoryReadRepository;

  @Autowired private LinkedApplicationGroupReadRepository groupReadRepository;

  @Autowired private QueryGateway queryGateway;

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
                'domain_event_entry',
                'snapshot_event_entry',
                'token_entry'
              )
            ORDER BY table_name
            """,
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"status\":\"UP\"");
    assertThat(eventStorageEngine).isInstanceOf(JpaEventStorageEngine.class);
    assertThat(axonTables)
        .containsExactly("domain_event_entry", "snapshot_event_entry", "token_entry");
  }

  @Test
  void givenApplicationData_whenMutated_thenOnlyControlledRetentionDeleteIsAllowed()
      throws Exception {
    UUID applicationId = UUID.randomUUID();
    applicationId(post(validCreateApplicationRequest(applicationId, UUID.randomUUID()), headers()));
    awaitProjection(applicationId);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.application_data WHERE application_id = ? AND version = 0",
                Integer.class,
                applicationId))
        .isEqualTo(1);

    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE axon.application_data SET payload_hash = ? WHERE application_id = ?",
                    "tampered",
                    applicationId))
        .hasStackTraceContaining("application_data is append-only; UPDATE is prohibited");
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "DELETE FROM axon.application_data WHERE application_id = ?", applicationId))
        .hasStackTraceContaining("application_data is append-only; DELETE is prohibited");

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT axon.delete_application_data_for_retention(?)", Long.class, applicationId))
        .isEqualTo(1L);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.application_data WHERE application_id = ?",
                Integer.class,
                applicationId))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM axon.application_current_state WHERE application_id = ?",
                Integer.class,
                applicationId))
        .isEqualTo(1);

    ResponseEntity<String> response =
        restTemplate.getForEntity(
            "http://localhost:" + port + "/api/v0/applications/" + applicationId, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

    List<String> currentStateColumns =
        jdbcTemplate.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_schema = 'axon' AND table_name = 'application_current_state'",
            String.class);
    assertThat(currentStateColumns)
        .contains("application_data_version")
        .doesNotContain(
            "laa_reference",
            "application_content",
            "individuals",
            "submitted_at",
            "office_code",
            "proceedings");
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

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    UUID applicationId = applicationId(response);
    assertThat(applicationId).isEqualTo(applyApplicationId);

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
              assertThat(history.getRequestPayload())
                  .contains("\"applicationDataVersion\"", "\"requestFingerprint\"")
                  .doesNotContain("LAA-123", "Ada", "Lovelace", "Care order");
              assertThat(history.getServiceName()).isEqualTo("CIVIL_APPLY");
            });

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT payload ->> 'laaReference' FROM axon.application_data "
                    + "WHERE application_id = ? AND version = 0",
                String.class,
                applicationId))
        .isEqualTo("LAA-123");

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
  }

  @Test
  void givenCreatedApplication_whenGetApplication_thenReturnsCurrentStateProjection()
      throws Exception {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applyProceedingId = UUID.randomUUID();
    final UUID involvedChildId = UUID.randomUUID();
    ApplicationCreateRequest request =
        validCreateApplicationRequest(applyApplicationId, applyProceedingId);
    Map<String, Object> content = new HashMap<>(request.getApplicationContent());
    Map<String, Object> proceeding = firstProceeding(content);
    proceeding.put("meaning", "Care proceedings");
    proceeding.put("substantiveLevelOfServiceNameEnum", "FULL_REPRESENTATION");
    proceeding.put("substantiveCostLimitation", 2_500.0);
    proceeding.put(
        "scopeLimitations", List.of(Map.of("meaning", "LIMITED", "description", "Limited scope")));
    content.put("proceedings", List.of(proceeding));
    content.put("submitterEmail", "provider@example.com");
    content.put(
        "applicationMerits",
        Map.of(
            "opponents",
            List.of(
                Map.of(
                    "opposableType",
                    "INDIVIDUAL",
                    "opposable",
                    Map.of("firstName", "Grace", "lastName", "Hopper"))),
            "involvedChildren",
            List.of(
                Map.of(
                    "id",
                    involvedChildId.toString(),
                    "fullName",
                    "Child Example",
                    "dateOfBirth",
                    "2015-01-02"))));
    content.put(
        "proceedingMerits",
        List.of(
            Map.of(
                "proceedingId",
                applyProceedingId.toString(),
                "proceedingLinkedChildren",
                List.of(Map.of("involvedChildId", involvedChildId.toString())))));
    request.setApplicationContent(content);

    UUID applicationId = applicationId(post(request, headers()));
    ResponseEntity<ApplicationResponse> response = awaitGet(applicationId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    ApplicationResponse actual = response.getBody();
    assertThat(actual).isNotNull();
    assertThat(actual.getLastUpdated()).isNotNull();
    assertThat(actual.getProceedings())
        .singleElement()
        .satisfies(
            proceedingResponse -> assertThat(proceedingResponse.getProceedingId()).isNotNull());

    ApplicationResponse expected =
        new ApplicationResponse()
            .applicationId(applicationId)
            .status(ApplicationStatus.APPLICATION_SUBMITTED)
            .laaReference("LAA-123")
            .lastUpdated(actual.getLastUpdated())
            .submittedAt(OffsetDateTime.parse("2026-07-14T12:30:00Z"))
            .isLead(true)
            .usedDelegatedFunctions(false)
            .applicationType(ApplicationType.INITIAL)
            .provider(
                new ProviderResponse().officeCode("1A001B").contactEmail("provider@example.com"))
            .opponents(
                List.of(
                    new OpponentResponse()
                        .opponentType("INDIVIDUAL")
                        .firstName("Grace")
                        .lastName("Hopper")))
            .proceedings(
                List.of(
                    new ApplicationProceedingResponse()
                        .proceedingId(actual.getProceedings().getFirst().getProceedingId())
                        .proceedingDescription("Care order")
                        .proceedingType("Care proceedings")
                        .categoryOfLaw(CategoryOfLaw.FAMILY)
                        .matterType(MatterType.SPECIAL_CHILDREN_ACT)
                        .levelOfService("FULL_REPRESENTATION")
                        .substantiveCostLimitation(2_500.0)
                        .scopeLimitations(
                            List.of(
                                new ScopeLimitationResponse()
                                    .scopeLimitation("LIMITED")
                                    .scopeDescription("Limited scope")))
                        .involvedChildren(
                            List.of(
                                new InvolvedChildResponse()
                                    .fullName("Child Example")
                                    .dateOfBirth(LocalDate.parse("2015-01-02"))))));

    assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
  }

  @Test
  void givenUnknownApplication_whenGetApplication_thenReturnsNotFound() {
    UUID applicationId = UUID.randomUUID();

    ResponseEntity<String> response =
        restTemplate.getForEntity(
            "http://localhost:" + port + "/api/v0/applications/" + applicationId, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).contains("No application found with ID: " + applicationId);
  }

  @Test
  void givenIdenticalRetry_whenPostApplicationAgain_thenReturnsCreatedIdempotently()
      throws Exception {
    UUID applyApplicationId = UUID.randomUUID();
    HttpEntity<ApplicationCreateRequest> request =
        new HttpEntity<>(
            validCreateApplicationRequest(applyApplicationId, UUID.randomUUID()), headers());

    ResponseEntity<Void> firstResponse =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/applications", request, Void.class);
    awaitProjection(applicationId(firstResponse));

    ResponseEntity<Void> retryResponse =
        restTemplate.postForEntity(
            "http://localhost:" + port + "/api/v0/applications", request, Void.class);

    assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(retryResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(retryResponse.getHeaders().getLocation())
        .isEqualTo(firstResponse.getHeaders().getLocation());

    UUID applicationId = applicationId(firstResponse);
    assertThat(awaitHistory(applicationId, 1))
        .singleElement()
        .satisfies(h -> assertThat(h.getEventType()).isEqualTo("APPLICATION_CREATED"));

    List<Map<String, Object>> events =
        jdbcTemplate.queryForList(
            "SELECT COUNT(*) as cnt FROM axon.domain_event_entry WHERE aggregate_identifier = ?",
            applicationId.toString());
    assertThat(events.getFirst().get("cnt")).isEqualTo(1L);
  }

  @Test
  void givenChangedPayload_whenPostApplicationAgain_thenReturnsConflict() throws Exception {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applyProceedingId = UUID.randomUUID();

    ResponseEntity<Void> firstResponse =
        post(validCreateApplicationRequest(applyApplicationId, applyProceedingId), headers());
    awaitProjection(applicationId(firstResponse));

    ResponseEntity<String> conflictResponse =
        post(
            validCreateApplicationRequest(applyApplicationId, UUID.randomUUID()),
            headers(),
            String.class);

    assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(conflictResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(awaitHistory(applyApplicationId, 1)).hasSize(1);
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

    assertThat(
            awaitHistoryTypes(
                linkedApplicationId, "APPLICATION_CREATED", "APPLICATION_GROUP_JOINED"))
        .extracting(ApplicationHistoryReadModel::getEventType)
        .containsExactlyInAnyOrder("APPLICATION_CREATED", "APPLICATION_GROUP_JOINED");
    assertThat(
            awaitHistoryTypes(
                leadApplicationId, "APPLICATION_CREATED", "APPLICATION_GROUP_CREATED"))
        .extracting(ApplicationHistoryReadModel::getEventType)
        .containsExactlyInAnyOrder("APPLICATION_CREATED", "APPLICATION_GROUP_CREATED");
    ApplicationReadModel projected =
        applicationReadRepository
            .findById(linkedApplicationId)
            .orElseThrow(() -> new AssertionError("Application not found: " + linkedApplicationId));
    assertThat(projected.getLeadApplicationId()).isEqualTo(leadApplicationId);
  }

  @Test
  void givenMissingLeadApplication_whenPostApplication_thenReturnsNotFound() {
    UUID missingLeadApplyApplicationId = UUID.randomUUID();
    UUID rejectedApplicationId = UUID.randomUUID();

    ResponseEntity<String> response =
        post(
            validLinkedCreateApplicationRequest(
                rejectedApplicationId, UUID.randomUUID(), missingLeadApplyApplicationId),
            headers(),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).contains(missingLeadApplyApplicationId.toString());
    assertRejectedApplicationWasRolledBack(rejectedApplicationId);
    assertThat(groupReadRepository.findByLeadApplicationId(missingLeadApplyApplicationId))
        .isEmpty();
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
    UUID rejectedApplicationId = UUID.randomUUID();
    ResponseEntity<String> response =
        post(
            validLinkedCreateApplicationRequest(
                rejectedApplicationId,
                UUID.randomUUID(),
                leadApplyApplicationId,
                missingAssociatedApplyApplicationId),
            headers(),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).contains(missingAssociatedApplyApplicationId.toString());
    assertRejectedApplicationWasRolledBack(rejectedApplicationId);
    assertThat(groupReadRepository.findByLeadApplicationId(leadApplicationId)).isEmpty();
  }

  @Test
  void givenNullContentId_whenPostApplication_thenReturnsBadRequestWithNoEvent() {
    ApplicationCreateRequest request =
        validCreateApplicationRequest(UUID.randomUUID(), UUID.randomUUID());
    Map<String, Object> contentWithNullId = new HashMap<>(request.getApplicationContent());
    contentWithNullId.put("id", null);
    request.setApplicationContent(contentWithNullId);

    ResponseEntity<String> response = post(request, headers(), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("Generic Validation Error");
  }

  @Test
  void givenSchemaInvalidRequest_whenPostApplication_thenReturnsBadRequest() throws Exception {
    ApplicationCreateRequest request =
        validCreateApplicationRequest(UUID.randomUUID(), UUID.randomUUID());
    Map<String, Object> invalidContent = new HashMap<>(request.getApplicationContent());
    invalidContent.remove("id");
    request.setApplicationContent(invalidContent);

    ResponseEntity<String> response = post(request, headers(), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("Generic Validation Error");
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

  @Test
  void givenConcurrentIdenticalRequests_whenPosted_thenBothSucceedWithOneCreationEvent()
      throws Exception {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applyProceedingId = UUID.randomUUID();
    ApplicationCreateRequest request =
        validCreateApplicationRequest(applyApplicationId, applyProceedingId);
    HttpEntity<ApplicationCreateRequest> entity = new HttpEntity<>(request, headers());

    CyclicBarrier barrier = new CyclicBarrier(2);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      CompletableFuture<ResponseEntity<Void>> f1 =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  barrier.await(10, TimeUnit.SECONDS);
                  return restTemplate.postForEntity(
                      "http://localhost:" + port + "/api/v0/applications", entity, Void.class);
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              },
              executor);
      CompletableFuture<ResponseEntity<Void>> f2 =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  barrier.await(10, TimeUnit.SECONDS);
                  return restTemplate.postForEntity(
                      "http://localhost:" + port + "/api/v0/applications", entity, Void.class);
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              },
              executor);

      ResponseEntity<Void> r1 = f1.get(20, TimeUnit.SECONDS);
      ResponseEntity<Void> r2 = f2.get(20, TimeUnit.SECONDS);

      // Both requests must resolve successfully regardless of which wins the concurrency race.
      assertThat(r1.getStatusCode().is2xxSuccessful()).isTrue();
      assertThat(r2.getStatusCode().is2xxSuccessful()).isTrue();
    } finally {
      executor.shutdown();
    }

    // The event store must contain exactly one ApplicationCreatedEvent.
    awaitProjection(applyApplicationId);
    List<Map<String, Object>> events =
        jdbcTemplate.queryForList(
            "SELECT payload_type, sequence_number FROM axon.domain_event_entry "
                + "WHERE aggregate_identifier = ? ORDER BY sequence_number",
            applyApplicationId.toString());
    assertThat(events)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.get("payload_type")).asString().contains("ApplicationCreatedEvent");
              assertThat(event.get("sequence_number")).isEqualTo(0L);
            });
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
    assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);
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

  private ResponseEntity<ApplicationResponse> awaitGet(UUID applicationId) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
    while (System.nanoTime() < deadline) {
      ResponseEntity<String> response =
          restTemplate.getForEntity(
              "http://localhost:" + port + "/api/v0/applications/" + applicationId, String.class);
      if (response.getStatusCode() == HttpStatus.OK) {
        return new ResponseEntity<>(
            objectMapper.readValue(response.getBody(), ApplicationResponse.class),
            response.getHeaders(),
            response.getStatusCode());
      }
      Thread.sleep(100);
    }
    throw new AssertionError(
        "Application was not available from the query projection: " + applicationId);
  }

  private ApplicationReadModel awaitProjection(UUID applicationId) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
    while (System.nanoTime() < deadline) {
      var projected =
          queryGateway
              .query(
                  new FindApplicationByIdQuery(applicationId),
                  ResponseTypes.optionalInstanceOf(ApplicationReadModel.class))
              .join();
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

  private List<ApplicationHistoryReadModel> awaitHistoryTypes(
      UUID applicationId, String... expectedEventTypes) throws Exception {
    List<String> expected = List.of(expectedEventTypes);
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      List<ApplicationHistoryReadModel> history =
          applicationHistoryReadRepository.findAllByApplicationIdOrderByOccurredAtAsc(
              applicationId);
      List<String> actual =
          history.stream().map(ApplicationHistoryReadModel::getEventType).toList();
      if (actual.size() == expected.size()
          && new java.util.HashSet<>(actual).equals(new java.util.HashSet<>(expected))) {
        return history;
      }
      Thread.sleep(50);
    }
    throw new AssertionError(
        "Application history projection did not contain " + expected + " for " + applicationId);
  }

  private void assertRejectedApplicationWasRolledBack(UUID applicationId) {
    Integer eventCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM axon.domain_event_entry WHERE aggregate_identifier = ?",
            Integer.class,
            applicationId.toString());
    assertThat(eventCount).isZero();
    assertThat(applicationReadRepository.findById(applicationId)).isEmpty();
    assertThat(applicationHistoryReadRepository.countByApplicationId(applicationId)).isZero();
  }
}
