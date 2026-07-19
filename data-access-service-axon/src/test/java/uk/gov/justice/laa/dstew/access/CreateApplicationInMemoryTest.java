package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validLinkedCreateApplicationRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.QueryGateway;
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
import org.springframework.test.annotation.DirtiesContext;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataId;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataRepository;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationHistoryResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
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
      "axon.eventstore.jpa.enabled=false",
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.properties.hibernate.default_schema=PUBLIC",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "spring.datasource.url=jdbc:h2:mem:axon-create;DB_CLOSE_DELAY=-1"
    })
@AutoConfigureTestRestTemplate
@Import(AxonInMemoryConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CreateApplicationInMemoryTest {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ApplicationReadRepository applicationReadRepository;
  @Autowired private ApplicationHistoryReadRepository applicationHistoryReadRepository;
  @Autowired private LinkedApplicationGroupReadRepository groupReadRepository;
  @Autowired private EventProcessingConfiguration eventProcessingConfiguration;
  @Autowired private EventStore eventStore;
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
  void givenValidRequest_whenPostApplication_thenReturnsCreatedAndProjectsOwnedState() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applyProceedingId = UUID.randomUUID();
    ApplicationCreateRequest request =
        validCreateApplicationRequest(applyApplicationId, applyProceedingId);
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "2");

    ResponseEntity<Void> response =
        restTemplate.postForEntity(
            "/api/v0/applications", new HttpEntity<>(request, headers), Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getLocation().getPath())
        .isEqualTo("/api/v0/applications/" + applyApplicationId);

    UUID applicationId =
        UUID.fromString(
            response.getHeaders().getLocation().getPath().replace("/api/v0/applications/", ""));
    assertThat(applicationId).isEqualTo(applyApplicationId);
    ApplicationReadModel projected = awaitProjection(applicationId);

    assertThat(projected.getApplicationId()).isEqualTo(applicationId);
    assertThat(projected.getApplyApplicationId()).isEqualTo(applyApplicationId);
    assertThat(projected.getStatus()).isEqualTo(ApplicationStatus.APPLICATION_SUBMITTED.name());
    assertThat(projected.getLaaReference()).isEqualTo("LAA-123");
    assertThat(projected.getOfficeCode()).isEqualTo("1A001B");
    assertThat(projected.getSchemaVersion()).isEqualTo(2);
    assertThat(projected.getIndividuals())
        .singleElement()
        .satisfies(
            individual -> {
              assertThat(individual.firstName()).isEqualTo("Ada");
              assertThat(individual.lastName()).isEqualTo("Lovelace");
              assertThat(individual.type()).isEqualTo(IndividualType.CLIENT.name());
            });
    assertThat(projected.getProceedings())
        .singleElement()
        .satisfies(
            proceeding -> {
              assertThat(proceeding.applyProceedingId()).isEqualTo(applyProceedingId);
              assertThat(proceeding.lead()).isTrue();
              assertThat(proceeding.description()).isEqualTo("Care order");
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

    assertThat(applicationDataRepository.findById(new ApplicationDataId(applicationId, 0L)))
        .isPresent()
        .hasValueSatisfying(
            data -> {
              assertThat(data.getPayload().laaReference()).isEqualTo("LAA-123");
              assertThat(data.getPayload().individuals()).singleElement();
              assertThat(data.getPayloadHash()).hasSize(64);
            });

    var processor = eventProcessingConfiguration.eventProcessor("application-projection");
    assertThat(processor).isPresent();
    assertThat(processor.get()).isInstanceOf(TrackingEventProcessor.class);
    assertThat(eventProcessingConfiguration.eventProcessor("application-history-projection"))
        .containsInstanceOf(TrackingEventProcessor.class);
  }

  @Test
  void givenSchemaInvalidRequest_whenPostApplication_thenReturnsBadRequestAndNoProjection() {
    UUID applyApplicationId = UUID.randomUUID();
    ApplicationCreateRequest validRequest =
        validCreateApplicationRequest(applyApplicationId, UUID.randomUUID());
    var invalidContent = new java.util.HashMap<>(validRequest.getApplicationContent());
    invalidContent.remove("id");
    validRequest.setApplicationContent(invalidContent);

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            "/api/v0/applications", new HttpEntity<>(validRequest, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("Generic Validation Error");
    assertThat(applicationReadRepository.findById(applyApplicationId)).isEmpty();
  }

  @Test
  void givenIdenticalRetry_whenPostApplicationAgain_thenReturnsCreatedIdempotently() {
    UUID applyApplicationId = UUID.randomUUID();
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "2");
    HttpEntity<ApplicationCreateRequest> request =
        new HttpEntity<>(
            validCreateApplicationRequest(applyApplicationId, UUID.randomUUID()), headers);

    ResponseEntity<Void> firstResponse =
        restTemplate.postForEntity("/api/v0/applications", request, Void.class);
    awaitProjection(applicationId(firstResponse));
    ResponseEntity<Void> retryResponse =
        restTemplate.postForEntity("/api/v0/applications", request, Void.class);

    assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(retryResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(retryResponse.getHeaders().getLocation())
        .isEqualTo(firstResponse.getHeaders().getLocation());

    UUID applicationId = applicationId(firstResponse);
    assertThat(awaitHistory(applicationId, 1))
        .singleElement()
        .satisfies(h -> assertThat(h.getEventType()).isEqualTo("APPLICATION_CREATED"));
  }

  @Test
  void givenChangedPayload_whenPostApplicationAgain_thenReturnsConflict() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applyProceedingId = UUID.randomUUID();
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "2");

    ResponseEntity<Void> firstResponse =
        restTemplate.postForEntity(
            "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(applyApplicationId, applyProceedingId), headers),
            Void.class);
    awaitProjection(applicationId(firstResponse));

    ResponseEntity<String> conflictResponse =
        restTemplate.postForEntity(
            "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(applyApplicationId, UUID.randomUUID()), headers),
            String.class);

    assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(conflictResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(awaitHistory(applyApplicationId, 1)).hasSize(1);
  }

  @Test
  void givenMissingLeadApplication_whenPostApplication_thenReturnsNotFound() {
    UUID missingApplyApplicationId = UUID.randomUUID();
    UUID rejectedApplicationId = UUID.randomUUID();
    ApplicationCreateRequest request =
        validLinkedCreateApplicationRequest(
            rejectedApplicationId, UUID.randomUUID(), missingApplyApplicationId);

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            "/api/v0/applications", new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).contains(missingApplyApplicationId.toString());
    assertRejectedApplicationWasRolledBack(rejectedApplicationId);
    assertThat(groupReadRepository.findByLeadApplicationId(missingApplyApplicationId)).isEmpty();
  }

  @Test
  void givenMissingAssociatedApplication_whenPostApplication_thenReturnsNotFound() {
    UUID leadApplyApplicationId = UUID.randomUUID();
    ResponseEntity<Void> leadResponse =
        restTemplate.postForEntity(
            "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(leadApplyApplicationId, UUID.randomUUID()),
                headers()),
            Void.class);
    awaitProjection(applicationId(leadResponse));

    UUID missingAssociatedApplyApplicationId = UUID.randomUUID();
    UUID rejectedApplicationId = UUID.randomUUID();
    ApplicationCreateRequest request =
        validLinkedCreateApplicationRequest(
            rejectedApplicationId,
            UUID.randomUUID(),
            leadApplyApplicationId,
            missingAssociatedApplyApplicationId);

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            "/api/v0/applications", new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).contains(missingAssociatedApplyApplicationId.toString());
    assertRejectedApplicationWasRolledBack(rejectedApplicationId);
    assertThat(groupReadRepository.findByLeadApplicationId(leadApplyApplicationId)).isEmpty();
  }

  @Test
  void givenExistingLeadApplication_whenPostLinkedApplication_thenProjectsLeadLink() {
    UUID leadApplyApplicationId = UUID.randomUUID();
    ResponseEntity<Void> leadResponse =
        restTemplate.postForEntity(
            "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(leadApplyApplicationId, UUID.randomUUID()),
                headers()),
            Void.class);
    UUID leadApplicationId = applicationId(leadResponse);
    awaitProjection(leadApplicationId);

    UUID linkedApplyApplicationId = UUID.randomUUID();
    ResponseEntity<Void> linkedResponse =
        restTemplate.postForEntity(
            "/api/v0/applications",
            new HttpEntity<>(
                validLinkedCreateApplicationRequest(
                    linkedApplyApplicationId, UUID.randomUUID(), leadApplyApplicationId),
                headers()),
            Void.class);

    assertThat(linkedResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    UUID linkedApplicationId = applicationId(linkedResponse);

    // ApplicationCreatedEvent carries leadApplicationId directly — projection sets it immediately.
    ApplicationReadModel projected = awaitProjection(linkedApplicationId);
    assertThat(projected.getLeadApplicationId()).isEqualTo(leadApplicationId);

    // LinkedApplicationGroupProjection (tracking) records group membership asynchronously.
    await()
        .atMost(10, TimeUnit.SECONDS)
        .pollInterval(50, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () ->
                assertThat(groupReadRepository.findByLeadApplicationId(leadApplicationId))
                    .isPresent()
                    .hasValueSatisfying(
                        group -> {
                          assertThat(group.getLeadApplicationId()).isEqualTo(leadApplicationId);
                          assertThat(group.getMemberIds())
                              .contains(leadApplicationId, linkedApplicationId);
                        }));

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

    ResponseEntity<ApplicationHistoryResponse> linkedHistoryResponse =
        getApplicationHistory(linkedApplicationId, null);
    assertThat(linkedHistoryResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(linkedHistoryResponse.getBody()).isNotNull();
    assertThat(linkedHistoryResponse.getBody().getEvents())
        .extracting(event -> event.getDomainEventType())
        .containsExactlyInAnyOrder(
            DomainEventType.APPLICATION_CREATED, DomainEventType.APPLICATION_GROUP_JOINED);

    ResponseEntity<ApplicationHistoryResponse> filteredGroupHistoryResponse =
        getApplicationHistory(linkedApplicationId, DomainEventType.APPLICATION_GROUP_JOINED);
    assertThat(filteredGroupHistoryResponse.getBody()).isNotNull();
    assertThat(filteredGroupHistoryResponse.getBody().getEvents())
        .singleElement()
        .satisfies(
            event ->
                assertThat(event.getDomainEventType())
                    .isEqualTo(DomainEventType.APPLICATION_GROUP_JOINED));
  }

  @Test
  void givenExistingLeadAndFirstLinked_whenPostSecondLinkedApplication_thenJoinsExistingGroup() {
    UUID leadApplyApplicationId = UUID.randomUUID();
    UUID leadApplicationId =
        applicationId(
            restTemplate.postForEntity(
                "/api/v0/applications",
                new HttpEntity<>(
                    validCreateApplicationRequest(leadApplyApplicationId, UUID.randomUUID()),
                    headers()),
                Void.class));
    awaitProjection(leadApplicationId);

    UUID firstLinkedApplyApplicationId = UUID.randomUUID();
    UUID firstLinkedApplicationId =
        applicationId(
            restTemplate.postForEntity(
                "/api/v0/applications",
                new HttpEntity<>(
                    validLinkedCreateApplicationRequest(
                        firstLinkedApplyApplicationId, UUID.randomUUID(), leadApplyApplicationId),
                    headers()),
                Void.class));
    awaitProjection(firstLinkedApplicationId);

    UUID secondLinkedApplyApplicationId = UUID.randomUUID();
    UUID secondLinkedApplicationId =
        applicationId(
            restTemplate.postForEntity(
                "/api/v0/applications",
                new HttpEntity<>(
                    validLinkedCreateApplicationRequest(
                        secondLinkedApplyApplicationId, UUID.randomUUID(), leadApplyApplicationId),
                    headers()),
                Void.class));
    awaitProjection(secondLinkedApplicationId);

    assertThat(
            awaitHistoryTypes(
                secondLinkedApplicationId, "APPLICATION_CREATED", "APPLICATION_GROUP_JOINED"))
        .extracting(ApplicationHistoryReadModel::getEventType)
        .containsExactlyInAnyOrder("APPLICATION_CREATED", "APPLICATION_GROUP_JOINED");

    // All three should end up in the same group.
    await()
        .atMost(10, TimeUnit.SECONDS)
        .pollInterval(50, TimeUnit.MILLISECONDS)
        .untilAsserted(
            () ->
                assertThat(groupReadRepository.findByLeadApplicationId(leadApplicationId))
                    .isPresent()
                    .hasValueSatisfying(
                        group ->
                            assertThat(group.getMemberIds())
                                .containsExactlyInAnyOrder(
                                    leadApplicationId,
                                    firstLinkedApplicationId,
                                    secondLinkedApplicationId)));
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
    UUID applyApplicationId = UUID.randomUUID();
    ResponseEntity<Void> postResponse =
        restTemplate.postForEntity(
            "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(applyApplicationId, UUID.randomUUID()), headers()),
            Void.class);
    UUID applicationId = applicationId(postResponse);
    awaitProjection(applicationId);

    ResponseEntity<ApplicationSummaryResponse> response =
        restTemplate.getForEntity("/api/v0/applications", ApplicationSummaryResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getApplications())
        .anySatisfy(
            summary -> {
              assertThat(summary.getApplicationId()).isEqualTo(applicationId);
              assertThat(summary.getLaaReference()).isEqualTo("LAA-123");
              assertThat(summary.getIsLead()).isTrue();
              assertThat(summary.getClientFirstName()).isEqualTo("Ada");
              assertThat(summary.getClientLastName()).isEqualTo("Lovelace");
            });
  }

  @Test
  void givenLinkedApplications_whenGetApplications_thenLinkedApplicationsPopulatedOnLead() {
    UUID leadApplyApplicationId = UUID.randomUUID();
    UUID leadApplicationId =
        applicationId(
            restTemplate.postForEntity(
                "/api/v0/applications",
                new HttpEntity<>(
                    validCreateApplicationRequest(leadApplyApplicationId, UUID.randomUUID()),
                    headers()),
                Void.class));
    awaitProjection(leadApplicationId);

    UUID linkedApplyApplicationId = UUID.randomUUID();
    UUID linkedApplicationId =
        applicationId(
            restTemplate.postForEntity(
                "/api/v0/applications",
                new HttpEntity<>(
                    validLinkedCreateApplicationRequest(
                        linkedApplyApplicationId, UUID.randomUUID(), leadApplyApplicationId),
                    headers()),
                Void.class));
    awaitProjection(linkedApplicationId);

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
    headers.set("X-Schema-Version", "2");
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
                    .query(
                        new FindApplicationByIdQuery(applicationId),
                        ResponseTypes.optionalInstanceOf(ApplicationReadModel.class))
                    .join(),
            Optional::isPresent)
        .get();
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
    assertThat(eventStore.readEvents(applicationId.toString()).hasNext()).isFalse();
    assertThat(applicationDataRepository.countByIdApplicationId(applicationId)).isZero();
    assertThat(applicationReadRepository.findById(applicationId)).isEmpty();
    assertThat(applicationHistoryReadRepository.countByApplicationId(applicationId)).isZero();
  }
}
