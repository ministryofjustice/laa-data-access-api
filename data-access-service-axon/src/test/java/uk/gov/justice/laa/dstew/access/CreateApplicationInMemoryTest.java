package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validLinkedCreateApplicationRequest;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.messaging.eventhandling.processing.streaming.StreamingEventProcessor;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataId;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataRepository;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationHistoryResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.IndividualsResponse;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.FindApplicationByIdQuery;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryReadModel;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadRepository;

@SpringBootTest(
    classes = DataAccessServiceAxonApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.properties.hibernate.default_schema=PUBLIC",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "spring.datasource.url=jdbc:h2:mem:axon-create;DB_CLOSE_DELAY=-1"
    })
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CreateApplicationInMemoryTest {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ApplicationReadRepository applicationReadRepository;
  @Autowired private ApplicationHistoryReadRepository applicationHistoryReadRepository;
  @Autowired private LinkedApplicationGroupReadRepository groupReadRepository;
  @Autowired private AxonConfiguration axonConfiguration;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private QueryGateway queryGateway;
  @Autowired private ApplicationDataRepository applicationDataRepository;

  @Test
  void givenAxonApplication_whenOpenApiRequested_thenDocumentsCreateApplication() {
    ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
        .contains("\"openapi\":\"3.1")
        .contains("\"/api/v0/applications\"");
  }

  @Test
  void givenUnknownApplication_whenGetApplication_thenReturnsNotFound() {
    UUID applicationId = UUID.randomUUID();

    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/v0/applications/" + applicationId, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).contains("No application found with ID: " + applicationId);
  }

  @Test
  void givenCreatedApplication_whenGetApplicationHistory_thenReturnsApiVisibleEvents() {
    UUID applicationId = UUID.randomUUID();
    applicationId(
        restTemplate.postForEntity(
            "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(applicationId, UUID.randomUUID()), headers()),
            Void.class));
    awaitHistoryTypes(applicationId, "APPLICATION_CREATED");

    ResponseEntity<ApplicationHistoryResponse> response =
        restTemplate.exchange(
            "/api/v0/applications/" + applicationId + "/history-search",
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            ApplicationHistoryResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getEvents())
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getApplicationId()).isEqualTo(applicationId);
              assertThat(event.getDomainEventType()).isEqualTo(DomainEventType.APPLICATION_CREATED);
              assertThat(event.getCreatedBy()).isEqualTo("CIVIL_APPLY");
              assertThat(event.getCreatedAt()).isNotNull();
            });
  }

  @Test
  void givenNonMatchingEventType_whenGetApplicationHistory_thenReturnsEmptyEvents() {
    UUID applicationId = UUID.randomUUID();
    applicationId(
        restTemplate.postForEntity(
            "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(applicationId, UUID.randomUUID()), headers()),
            Void.class));
    awaitHistoryTypes(applicationId, "APPLICATION_CREATED");

    ResponseEntity<ApplicationHistoryResponse> response =
        restTemplate.exchange(
            "/api/v0/applications/"
                + applicationId
                + "/history-search?eventType=APPLICATION_UPDATED",
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            ApplicationHistoryResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getEvents()).isEmpty();
  }

  @Test
  void givenMissingServiceName_whenGetApplicationHistory_thenReturnsBadRequest() {
    ResponseEntity<String> response =
        restTemplate.getForEntity(
            "/api/v0/applications/" + UUID.randomUUID() + "/history-search", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  @Test
  void givenCreatedApplication_whenGetIndividualsForApplication_thenReturnsEmptyIndividuals() {
    UUID applicationId = UUID.randomUUID();
    applicationId(
        restTemplate.postForEntity(
            "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(applicationId, UUID.randomUUID()), headers()),
            Void.class));
    awaitProjection(applicationId);

    ResponseEntity<IndividualsResponse> response =
        restTemplate.exchange(
            "/api/v0/individuals?applicationId=" + applicationId + "&individualType=CLIENT",
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            IndividualsResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getIndividuals())
        .singleElement()
        .satisfies(
            individual -> {
              assertThat(individual.getFirstName()).isEqualTo("Ada");
              assertThat(individual.getLastName()).isEqualTo("Lovelace");
              assertThat(individual.getType().name()).isEqualTo("CLIENT");
            });
    assertThat(response.getBody().getPaging().getPage()).isEqualTo(1);
    assertThat(response.getBody().getPaging().getPageSize()).isEqualTo(20);
    assertThat(response.getBody().getPaging().getTotalRecords()).isEqualTo(1);
    assertThat(response.getBody().getPaging().getItemsReturned()).isEqualTo(1);
  }

  @Test
  void givenClientDetailsWithoutApplicationId_whenGetIndividuals_thenReturnsBadRequest() {
    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v0/individuals?include=CLIENT_DETAILS",
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody())
        .contains("Application ID is required when included data is CLIENT_DETAILS");
  }

  @Test
  void givenValidRequest_whenPostApplication_thenReturnsCreatedAndProjectsOwnedState() {
    UUID applicationId = UUID.randomUUID();
    UUID applyProceedingId = UUID.randomUUID();
    ApplicationCreateRequest request =
        validCreateApplicationRequest(applicationId, applyProceedingId);
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "1");

    ResponseEntity<Void> response =
        restTemplate.postForEntity(
            "/api/v0/applications", new HttpEntity<>(request, headers), Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getLocation().getPath())
        .isEqualTo("/api/v0/applications/" + applicationId);

    UUID createdApplicationId =
        UUID.fromString(
            response.getHeaders().getLocation().getPath().replace("/api/v0/applications/", ""));
    assertThat(createdApplicationId).isEqualTo(applicationId);
    ApplicationReadModel projected = awaitProjection(createdApplicationId);

    assertThat(projected.getApplicationId()).isEqualTo(createdApplicationId);
    assertThat(projected.getStatus()).isEqualTo(ApplicationStatus.APPLICATION_SUBMITTED.name());
    assertThat(projected.getLaaReference()).isEqualTo("LAA-123");
    assertThat(projected.getOfficeCode()).isEqualTo("1A001B");
    assertThat(projected.getSchemaVersion()).isEqualTo(1);
    assertThat(projected.getProceedings())
        .singleElement()
        .satisfies(
            proceeding -> {
              assertThat(proceeding.getId()).isEqualTo(applyProceedingId);
              assertThat(proceeding.getLeadProceeding()).isTrue();
              assertThat(proceeding.getDescription()).isEqualTo("Care order");
            });

    assertThat(awaitHistory(createdApplicationId, 1))
        .singleElement()
        .satisfies(
            history -> {
              assertThat(history.getEventType()).isEqualTo("APPLICATION_CREATED");
              assertThat(history.getRequestPayload())
                  .contains("\"applicationDataVersion\"", "\"requestFingerprint\"")
                  .doesNotContain("LAA-123", "Ada", "Lovelace", "Care order");
              assertThat(history.getServiceName()).isEqualTo("CIVIL_APPLY");
            });

    assertThat(applicationDataRepository.findById(new ApplicationDataId(createdApplicationId, 0L)))
        .isPresent()
        .hasValueSatisfying(
            data -> {
              assertThat(data.getPayload().laaReference()).isEqualTo("LAA-123");
              assertThat(data.getPayloadHash()).hasSize(64);
            });

    var processors = axonConfiguration.getComponents(StreamingEventProcessor.class);
    assertThat(processors.get("application-projection")).isNotNull();
    assertThat(processors.get("application-history-projection")).isNotNull();
  }

  @Test
  void givenSchemaInvalidRequest_whenPostApplication_thenReturnsBadRequestAndNoProjection() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreateRequest validRequest =
        validCreateApplicationRequest(applicationId, UUID.randomUUID());
    var invalidContent = new java.util.HashMap<>(validRequest.getApplicationContent());
    invalidContent.remove("submittedAt");
    validRequest.setApplicationContent(invalidContent);

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            "/api/v0/applications", new HttpEntity<>(validRequest, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("Generic Validation Error");
    assertThat(applicationReadRepository.findById(applicationId)).isEmpty();
  }

  @Test
  void givenIdenticalRetry_whenPostApplicationAgain_thenReturnsCreatedIdempotently() {
    UUID applicationId = UUID.randomUUID();
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "1");
    HttpEntity<ApplicationCreateRequest> request =
        new HttpEntity<>(validCreateApplicationRequest(applicationId, UUID.randomUUID()), headers);

    ResponseEntity<Void> firstResponse =
        restTemplate.postForEntity("/api/v0/applications", request, Void.class);
    awaitProjection(applicationId(firstResponse));
    ResponseEntity<Void> retryResponse =
        restTemplate.postForEntity("/api/v0/applications", request, Void.class);

    assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(retryResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(retryResponse.getHeaders().getLocation())
        .isEqualTo(firstResponse.getHeaders().getLocation());

    UUID createdApplicationId = applicationId(firstResponse);
    assertThat(awaitHistory(createdApplicationId, 1))
        .singleElement()
        .satisfies(h -> assertThat(h.getEventType()).isEqualTo("APPLICATION_CREATED"));
  }

  @Test
  void givenChangedPayload_whenPostApplicationAgain_thenReturnsConflict() {
    UUID applicationId = UUID.randomUUID();
    UUID applyProceedingId = UUID.randomUUID();
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "1");

    ResponseEntity<Void> firstResponse =
        restTemplate.postForEntity(
            "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(applicationId, applyProceedingId), headers),
            Void.class);
    awaitProjection(applicationId(firstResponse));

    ResponseEntity<String> conflictResponse =
        restTemplate.postForEntity(
            "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(applicationId, UUID.randomUUID()), headers),
            String.class);

    assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(conflictResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(awaitHistory(applicationId, 1)).hasSize(1);
  }

  @Test
  @Disabled("Linked applications removed from schema; orchestration retained for future endpoint")
  void givenMissingLeadApplication_whenPostApplication_thenReturnsNotFound() {
    UUID missingApplicationId = UUID.randomUUID();
    UUID rejectedApplicationId = UUID.randomUUID();
    ApplicationCreateRequest request =
        validLinkedCreateApplicationRequest(
            rejectedApplicationId, UUID.randomUUID(), missingApplicationId);

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            "/api/v0/applications", new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).contains(missingApplicationId.toString());
    assertRejectedApplicationWasRolledBack(rejectedApplicationId);
    assertThat(groupReadRepository.findByLeadApplicationId(missingApplicationId)).isEmpty();
  }

  @Test
  @Disabled("Linked applications removed from schema; orchestration retained for future endpoint")
  void givenMissingAssociatedApplication_whenPostApplication_thenReturnsNotFound() {
    UUID leadApplicationId = UUID.randomUUID();
    ResponseEntity<Void> leadResponse =
        restTemplate.postForEntity(
            "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(leadApplicationId, UUID.randomUUID()), headers()),
            Void.class);
    awaitProjection(applicationId(leadResponse));

    UUID missingAssociatedApplicationId = UUID.randomUUID();
    UUID rejectedApplicationId = UUID.randomUUID();
    ApplicationCreateRequest request =
        validLinkedCreateApplicationRequest(
            rejectedApplicationId,
            UUID.randomUUID(),
            leadApplicationId,
            missingAssociatedApplicationId);

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            "/api/v0/applications", new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).contains(missingAssociatedApplicationId.toString());
    assertRejectedApplicationWasRolledBack(rejectedApplicationId);
    assertThat(groupReadRepository.findByLeadApplicationId(leadApplicationId)).isEmpty();
  }

  @Test
  @Disabled("Linked applications removed from schema; orchestration retained for future endpoint")
  void givenExistingLeadApplication_whenPostLinkedApplication_thenProjectsLeadLink() {
    UUID leadApplicationId = UUID.randomUUID();
    ResponseEntity<Void> leadResponse =
        restTemplate.postForEntity(
            "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(leadApplicationId, UUID.randomUUID()), headers()),
            Void.class);
    UUID createdLeadApplicationId = applicationId(leadResponse);
    awaitProjection(createdLeadApplicationId);

    UUID linkedApplicationId = UUID.randomUUID();
    ResponseEntity<Void> linkedResponse =
        restTemplate.postForEntity(
            "/api/v0/applications",
            new HttpEntity<>(
                validLinkedCreateApplicationRequest(
                    linkedApplicationId, UUID.randomUUID(), createdLeadApplicationId),
                headers()),
            Void.class);

    assertThat(linkedResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    UUID createdLinkedApplicationId = applicationId(linkedResponse);

    // ApplicationCreatedEvent carries leadApplicationId directly — projection sets it immediately.
    ApplicationReadModel projected = awaitProjection(createdLinkedApplicationId);
    assertThat(projected.getLeadApplicationId()).isEqualTo(createdLeadApplicationId);

    // LinkedApplicationGroupProjection (tracking) records group membership asynchronously.
    await()
        .atMost(10, TimeUnit.SECONDS)
        .pollInterval(50, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () ->
                assertThat(groupReadRepository.findByLeadApplicationId(createdLeadApplicationId))
                    .isPresent()
                    .hasValueSatisfying(
                        group -> {
                          assertThat(group.getLeadApplicationId())
                              .isEqualTo(createdLeadApplicationId);
                          assertThat(group.getMemberIds())
                              .contains(createdLeadApplicationId, createdLinkedApplicationId);
                        }));

    assertThat(
            awaitHistoryTypes(
                createdLinkedApplicationId, "APPLICATION_CREATED", "APPLICATION_GROUP_JOINED"))
        .extracting(ApplicationHistoryReadModel::getEventType)
        .containsExactlyInAnyOrder("APPLICATION_CREATED", "APPLICATION_GROUP_JOINED");
    assertThat(
            awaitHistoryTypes(
                createdLeadApplicationId, "APPLICATION_CREATED", "APPLICATION_GROUP_CREATED"))
        .extracting(ApplicationHistoryReadModel::getEventType)
        .containsExactlyInAnyOrder("APPLICATION_CREATED", "APPLICATION_GROUP_CREATED");

    ResponseEntity<ApplicationHistoryResponse> linkedHistoryResponse =
        getApplicationHistory(createdLinkedApplicationId, null);
    assertThat(linkedHistoryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(linkedHistoryResponse.getBody()).isNotNull();
    assertThat(linkedHistoryResponse.getBody().getEvents())
        .extracting(event -> event.getDomainEventType())
        .containsExactlyInAnyOrder(
            DomainEventType.APPLICATION_CREATED, DomainEventType.APPLICATION_GROUP_JOINED);

    ResponseEntity<ApplicationHistoryResponse> filteredGroupHistoryResponse =
        getApplicationHistory(createdLinkedApplicationId, DomainEventType.APPLICATION_GROUP_JOINED);
    assertThat(filteredGroupHistoryResponse.getBody()).isNotNull();
    assertThat(filteredGroupHistoryResponse.getBody().getEvents())
        .singleElement()
        .satisfies(
            event ->
                assertThat(event.getDomainEventType())
                    .isEqualTo(DomainEventType.APPLICATION_GROUP_JOINED));
  }

  @Test
  @Disabled("Linked applications removed from schema; orchestration retained for future endpoint")
  void givenExistingLeadAndFirstLinked_whenPostSecondLinkedApplication_thenJoinsExistingGroup() {
    UUID leadApplicationId = UUID.randomUUID();
    UUID createdLeadApplicationId =
        applicationId(
            restTemplate.postForEntity(
                "/api/v0/applications",
                new HttpEntity<>(
                    validCreateApplicationRequest(leadApplicationId, UUID.randomUUID()), headers()),
                Void.class));
    awaitProjection(createdLeadApplicationId);

    UUID firstLinkedApplicationId = UUID.randomUUID();
    UUID createdFirstLinkedApplicationId =
        applicationId(
            restTemplate.postForEntity(
                "/api/v0/applications",
                new HttpEntity<>(
                    validLinkedCreateApplicationRequest(
                        firstLinkedApplicationId, UUID.randomUUID(), createdLeadApplicationId),
                    headers()),
                Void.class));
    awaitProjection(createdFirstLinkedApplicationId);

    UUID secondLinkedApplicationId = UUID.randomUUID();
    UUID createdSecondLinkedApplicationId =
        applicationId(
            restTemplate.postForEntity(
                "/api/v0/applications",
                new HttpEntity<>(
                    validLinkedCreateApplicationRequest(
                        secondLinkedApplicationId, UUID.randomUUID(), createdLeadApplicationId),
                    headers()),
                Void.class));
    awaitProjection(createdSecondLinkedApplicationId);

    assertThat(
            awaitHistoryTypes(
                createdSecondLinkedApplicationId,
                "APPLICATION_CREATED",
                "APPLICATION_GROUP_JOINED"))
        .extracting(ApplicationHistoryReadModel::getEventType)
        .containsExactlyInAnyOrder("APPLICATION_CREATED", "APPLICATION_GROUP_JOINED");

    // All three should end up in the same group.
    await()
        .atMost(10, TimeUnit.SECONDS)
        .pollInterval(50, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () ->
                assertThat(groupReadRepository.findByLeadApplicationId(createdLeadApplicationId))
                    .isPresent()
                    .hasValueSatisfying(
                        group ->
                            assertThat(group.getMemberIds())
                                .containsExactlyInAnyOrder(
                                    createdLeadApplicationId,
                                    createdFirstLinkedApplicationId,
                                    createdSecondLinkedApplicationId)));
  }

  @Test
  void givenNoMatchingApplications_whenGetApplicationsFilteredByLaaReference_thenReturnsEmpty() {
    ResponseEntity<ApplicationSummaryResponse> response =
        restTemplate.getForEntity(
            "/api/v0/applications?laaReference=DOES-NOT-EXIST-12345",
            ApplicationSummaryResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getApplications()).isEmpty();
    assertThat(response.getBody().getPaging().getTotalRecords()).isZero();
  }

  @Test
  void givenCreatedApplication_whenGetApplications_thenReturnsSummary() {
    UUID applicationId = UUID.randomUUID();
    ResponseEntity<Void> postResponse =
        restTemplate.postForEntity(
            "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(applicationId, UUID.randomUUID()), headers()),
            Void.class);
    UUID createdApplicationId = applicationId(postResponse);
    awaitProjection(createdApplicationId);

    ResponseEntity<ApplicationSummaryResponse> response =
        restTemplate.getForEntity("/api/v0/applications", ApplicationSummaryResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getApplications())
        .anySatisfy(
            summary -> {
              assertThat(summary.getApplicationId()).isEqualTo(createdApplicationId);
              assertThat(summary.getLaaReference()).isEqualTo("LAA-123");
              assertThat(summary.getIsLead()).isTrue();
              assertThat(summary.getClientFirstName()).isEqualTo("Ada");
              assertThat(summary.getClientLastName()).isEqualTo("Lovelace");
            });
  }

  @Test
  @Disabled("Linked applications removed from schema; orchestration retained for future endpoint")
  void givenLinkedApplications_whenGetApplications_thenLinkedApplicationsPopulatedOnLead() {
    UUID leadApplicationId = UUID.randomUUID();
    UUID createdLeadApplicationId =
        applicationId(
            restTemplate.postForEntity(
                "/api/v0/applications",
                new HttpEntity<>(
                    validCreateApplicationRequest(leadApplicationId, UUID.randomUUID()), headers()),
                Void.class));
    awaitProjection(createdLeadApplicationId);

    UUID linkedApplicationId = UUID.randomUUID();
    UUID createdLinkedApplicationId =
        applicationId(
            restTemplate.postForEntity(
                "/api/v0/applications",
                new HttpEntity<>(
                    validLinkedCreateApplicationRequest(
                        linkedApplicationId, UUID.randomUUID(), createdLeadApplicationId),
                    headers()),
                Void.class));
    awaitProjection(createdLinkedApplicationId);

    // Wait for the group to be projected before asserting linked apps in the list response.
    await()
        .atMost(10, TimeUnit.SECONDS)
        .pollInterval(50, TimeUnit.MILLISECONDS)
        .until(
            () -> groupReadRepository.findByLeadApplicationId(leadApplicationId),
            Optional::isPresent);

    ResponseEntity<ApplicationSummaryResponse> response =
        restTemplate.getForEntity("/api/v0/applications", ApplicationSummaryResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getApplications())
        .anySatisfy(
            summary -> {
              assertThat(summary.getApplicationId()).isEqualTo(leadApplicationId);
              assertThat(summary.getIsLead()).isTrue();
              assertThat(summary.getLinkedApplications())
                  .singleElement()
                  .satisfies(
                      linked ->
                          assertThat(linked.getApplicationId()).isEqualTo(linkedApplicationId));
            });
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "1");
    return headers;
  }

  private UUID applicationId(ResponseEntity<Void> response) {
    assertThat(response.getStatusCode()).isIn(HttpStatus.CREATED, HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getLocation()).isNotNull();
    return UUID.fromString(
        response.getHeaders().getLocation().getPath().replace("/api/v0/applications/", ""));
  }

  private ResponseEntity<ApplicationHistoryResponse> getApplicationHistory(
      UUID applicationId, DomainEventType eventType) {
    String path = "/api/v0/applications/" + applicationId + "/history-search";
    if (eventType != null) {
      path += "?eventType=" + eventType.getValue();
    }
    return restTemplate.exchange(
        path, HttpMethod.GET, new HttpEntity<>(headers()), ApplicationHistoryResponse.class);
  }

  private ApplicationReadModel awaitProjection(UUID applicationId) {
    return await()
        .atMost(10, TimeUnit.SECONDS)
        .pollInterval(50, TimeUnit.MILLISECONDS)
        .until(
            () ->
                queryGateway
                    .query(new FindApplicationByIdQuery(applicationId), ApplicationReadModel.class)
                    .join(),
            Objects::nonNull);
  }

  private List<ApplicationHistoryReadModel> awaitHistory(UUID applicationId, int expectedCount) {
    return await()
        .atMost(10, TimeUnit.SECONDS)
        .pollInterval(50, TimeUnit.MILLISECONDS)
        .until(
            () ->
                applicationHistoryReadRepository.findAllByApplicationIdOrderByOccurredAtAsc(
                    applicationId),
            history -> history.size() == expectedCount);
  }

  private List<ApplicationHistoryReadModel> awaitHistoryTypes(
      UUID applicationId, String... expectedEventTypes) {
    List<String> expected = List.of(expectedEventTypes);
    return await()
        .atMost(10, TimeUnit.SECONDS)
        .pollInterval(50, TimeUnit.MILLISECONDS)
        .until(
            () ->
                applicationHistoryReadRepository.findAllByApplicationIdOrderByOccurredAtAsc(
                    applicationId),
            history ->
                history.size() == expected.size()
                    && new java.util.HashSet<>(
                            history.stream()
                                .map(ApplicationHistoryReadModel::getEventType)
                                .toList())
                        .equals(new java.util.HashSet<>(expected)));
  }

  private void assertRejectedApplicationWasRolledBack(UUID applicationId) {
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM domain_event_entry WHERE aggregate_identifier = ?",
                Integer.class,
                applicationId.toString()))
        .isZero();
    assertThat(applicationDataRepository.countByIdApplicationId(applicationId)).isZero();
    assertThat(applicationReadRepository.findById(applicationId)).isEmpty();
    assertThat(applicationHistoryReadRepository.countByApplicationId(applicationId)).isZero();
  }
}
