package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validLinkedCreateApplicationRequest;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.messaging.eventhandling.processing.streaming.StreamingEventProcessor;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
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
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationSummaryResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;
import uk.gov.justice.laa.dstew.access.model.AutoGrantOutcome;
import uk.gov.justice.laa.dstew.access.model.AutograntedOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.DecisionStatus;
import uk.gov.justice.laa.dstew.access.model.DomainEventType;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.model.IndividualsResponse;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionProceedingRequest;
import uk.gov.justice.laa.dstew.access.model.MakeDecisionRequest;
import uk.gov.justice.laa.dstew.access.model.ManualOutcomeRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionDetailsRequest;
import uk.gov.justice.laa.dstew.access.model.MeritsDecisionStatus;
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
  void givenApplicationInProgress_whenUpdatedToSubmitted_thenCurrentStateAdvancesVersion() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreateRequest submitted =
        validCreateApplicationRequest(applicationId, UUID.randomUUID());
    ApplicationCreateRequest inProgress =
        ApplicationCreateRequest.builder()
            .applicationType(submitted.getApplicationType())
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .applicationContent(submitted.getApplicationContent())
            .laaReference(submitted.getLaaReference())
            .individuals(submitted.getIndividuals())
            .build();
    applicationId(
        restTemplate.postForEntity(
            "/api/v0/applications", new HttpEntity<>(inProgress, headers()), Void.class));
    awaitProjection(applicationId);
    ApplicationUpdateRequest update =
        new ApplicationUpdateRequest()
            .status(ApplicationStatus.APPLICATION_SUBMITTED)
            .applicationContent(submitted.getApplicationContent());

    ResponseEntity<Void> updateResponse =
        restTemplate.exchange(
            "/api/v0/applications/" + applicationId,
            HttpMethod.PATCH,
            new HttpEntity<>(update, headers()),
            Void.class);

    assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    ApplicationReadModel current = awaitApplicationVersion(applicationId, 1L);
    assertThat(current.getStatus()).isEqualTo("APPLICATION_SUBMITTED");
    assertThat(current.getAutoGranted())
        .isEqualTo(uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState.PENDING);
    assertThat(current.getApplicationDataVersion()).isEqualTo(1L);
    assertThat(awaitHistoryTypes(applicationId, "APPLICATION_CREATED", "APPLICATION_UPDATED"))
        .hasSize(2);
  }

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
  void givenCreatedApplication_whenGetIndividualsForApplication_thenReturnsCurrentIndividual() {
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
              assertThat(individual.getType()).isEqualTo(IndividualType.CLIENT);
              assertThat(individual.getClientId()).isNull();
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
    assertThat(projected.getAutoGranted())
        .isEqualTo(uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState.PENDING);
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

    var processors = axonConfiguration.getComponents(StreamingEventProcessor.class);
    assertThat(processors.get("application-projection")).isNotNull();
    assertThat(processors.get("application-history-projection")).isNotNull();
  }

  @Test
  void givenSubmittedApplication_whenMarkedReady_thenOnlyFalseOutcomeEntersManualList() {
    UUID applicationId = UUID.randomUUID();
    applicationId(
        restTemplate.postForEntity(
            "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(applicationId, UUID.randomUUID()), headers()),
            Void.class));
    awaitProjection(applicationId);

    ResponseEntity<ApplicationResponse> directBeforeReady =
        restTemplate.exchange(
            "/api/v0/applications/" + applicationId,
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            ApplicationResponse.class);
    assertThat(directBeforeReady.getBody()).isNotNull();
    assertThat(directBeforeReady.getBody().getAutoGranted())
        .isEqualTo(uk.gov.justice.laa.dstew.access.model.AutoGranted.PENDING);

    ResponseEntity<ApplicationSummaryResponse> before =
        restTemplate.exchange(
            "/api/v0/applications?status=APPLICATION_SUBMITTED&autoGranted=MANUAL",
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            ApplicationSummaryResponse.class);
    assertThat(before.getBody()).isNotNull();
    assertThat(before.getBody().getApplications())
        .extracting(application -> application.getApplicationId())
        .doesNotContain(applicationId);

    ManualOutcomeRequest request = new ManualOutcomeRequest(AutoGrantOutcome.MANUAL, 0L);
    ResponseEntity<Void> ready =
        restTemplate.exchange(
            "/api/v0/applications/" + applicationId + "/auto-grant-outcome",
            HttpMethod.PATCH,
            new HttpEntity<>(request, headers()),
            Void.class);
    assertThat(ready.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ApplicationReadModel projected = awaitApplicationVersion(applicationId, 1L);
    assertThat(projected.getStatus()).isEqualTo(ApplicationStatus.APPLICATION_SUBMITTED.name());
    assertThat(projected.getAutoGranted())
        .isEqualTo(uk.gov.justice.laa.dstew.access.command.application.AutoGrantedState.MANUAL);

    ResponseEntity<ApplicationResponse> direct =
        restTemplate.exchange(
            "/api/v0/applications/" + applicationId,
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            ApplicationResponse.class);
    assertThat(direct.getBody()).isNotNull();
    assertThat(direct.getBody().getStatus()).isEqualTo(ApplicationStatus.APPLICATION_SUBMITTED);
    assertThat(direct.getBody().getAutoGranted())
        .isEqualTo(uk.gov.justice.laa.dstew.access.model.AutoGranted.MANUAL);

    ResponseEntity<ApplicationSummaryResponse> after =
        restTemplate.exchange(
            "/api/v0/applications?status=APPLICATION_SUBMITTED&autoGranted=MANUAL",
            HttpMethod.GET,
            new HttpEntity<>(headers()),
            ApplicationSummaryResponse.class);
    assertThat(after.getBody()).isNotNull();
    assertThat(after.getBody().getApplications())
        .filteredOn(application -> application.getApplicationId().equals(applicationId))
        .singleElement()
        .satisfies(
            application ->
                assertThat(application.getAutoGranted())
                    .isEqualTo(uk.gov.justice.laa.dstew.access.model.AutoGranted.MANUAL));

    ResponseEntity<Void> replay =
        restTemplate.exchange(
            "/api/v0/applications/" + applicationId + "/auto-grant-outcome",
            HttpMethod.PATCH,
            new HttpEntity<>(request, headers()),
            Void.class);
    assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  void givenSubmittedApplication_whenAutomaticallyGranted_thenCompleteDecisionIsRecorded() {
    UUID applicationId = UUID.randomUUID();
    applicationId(
        restTemplate.postForEntity(
            "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(applicationId, UUID.randomUUID()), headers()),
            Void.class));
    ApplicationResponse created =
        restTemplate
            .exchange(
                "/api/v0/applications/" + applicationId,
                HttpMethod.GET,
                new HttpEntity<>(headers()),
                ApplicationResponse.class)
            .getBody();
    assertThat(created).isNotNull();
    UUID proceedingId = created.getProceedings().getFirst().getProceedingId();
    var request =
        new AutograntedOutcomeRequest(
            AutoGrantOutcome.AUTOGRANTED,
            0L,
            AutograntedOutcomeRequest.OverallDecisionEnum.GRANTED,
            List.of(
                new MakeDecisionProceedingRequest(
                    proceedingId,
                    new MeritsDecisionDetailsRequest(MeritsDecisionStatus.GRANTED, "Autogranted"))),
            new EventHistoryRequest().eventDescription("Automatic assessment passed"),
            Map.of("certificateNumber", "AUTO-2126"));

    ResponseEntity<Void> response =
        restTemplate.exchange(
            "/api/v0/applications/" + applicationId + "/auto-grant-outcome",
            HttpMethod.PATCH,
            new HttpEntity<>(request, headers()),
            Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    awaitApplicationVersion(applicationId, 1L);
    ApplicationResponse granted =
        restTemplate
            .exchange(
                "/api/v0/applications/" + applicationId,
                HttpMethod.GET,
                new HttpEntity<>(headers()),
                ApplicationResponse.class)
            .getBody();
    assertThat(granted).isNotNull();
    assertThat(granted.getAutoGranted())
        .isEqualTo(uk.gov.justice.laa.dstew.access.model.AutoGranted.AUTOGRANTED);
    assertThat(granted.getDecisionStatus()).isEqualTo(DecisionStatus.GRANTED);
    assertThat(granted.getProceedings())
        .singleElement()
        .satisfies(
            proceeding ->
                assertThat(proceeding.getMeritsDecision()).isEqualTo(MeritsDecisionStatus.GRANTED));
    assertThat(applicationDataRepository.findById(new ApplicationDataId(applicationId, 1L)))
        .get()
        .extracting(data -> data.getPayload().certificate())
        .isEqualTo(Map.of("certificateNumber", "AUTO-2126"));

    ResponseEntity<Void> repeated =
        restTemplate.exchange(
            "/api/v0/applications/" + applicationId + "/auto-grant-outcome",
            HttpMethod.PATCH,
            new HttpEntity<>(request, headers()),
            Void.class);
    assertThat(repeated.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(applicationDataRepository.countByIdApplicationId(applicationId)).isEqualTo(2L);
  }

  @Test
  void givenStaleVersion_whenMarkedReady_thenReturnsConflict() {
    UUID applicationId = UUID.randomUUID();
    applicationId(
        restTemplate.postForEntity(
            "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(applicationId, UUID.randomUUID()), headers()),
            Void.class));

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v0/applications/" + applicationId + "/auto-grant-outcome",
            HttpMethod.PATCH,
            new HttpEntity<>(new ManualOutcomeRequest(AutoGrantOutcome.MANUAL, 9L), headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).contains("version 9 not found");
  }

  @Test
  void givenApplicationInProgress_whenMarkedReady_thenReturnsUnprocessableEntity() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreateRequest submitted =
        validCreateApplicationRequest(applicationId, UUID.randomUUID());
    ApplicationCreateRequest inProgress =
        ApplicationCreateRequest.builder()
            .applicationType(submitted.getApplicationType())
            .status(ApplicationStatus.APPLICATION_IN_PROGRESS)
            .applicationContent(submitted.getApplicationContent())
            .laaReference(submitted.getLaaReference())
            .individuals(submitted.getIndividuals())
            .build();
    applicationId(
        restTemplate.postForEntity(
            "/api/v0/applications", new HttpEntity<>(inProgress, headers()), Void.class));

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v0/applications/" + applicationId + "/auto-grant-outcome",
            HttpMethod.PATCH,
            new HttpEntity<>(new ManualOutcomeRequest(AutoGrantOutcome.MANUAL, 0L), headers()),
            String.class);

    assertThat(response.getStatusCode().value()).isEqualTo(422);
    assertThat(response.getBody()).contains("APPLICATION_IN_PROGRESS");
  }

  @Test
  void givenAutomaticallyGrantedApplication_whenMarkedReady_thenReturnsConflict() {
    UUID applicationId = UUID.randomUUID();
    applicationId(
        restTemplate.postForEntity(
            "/api/v0/applications",
            new HttpEntity<>(
                validCreateApplicationRequest(applicationId, UUID.randomUUID()), headers()),
            Void.class));
    UUID proceedingId = awaitProjection(applicationId).getProceedings().getFirst().proceedingId();
    MakeDecisionRequest decision =
        MakeDecisionRequest.builder()
            .applicationVersion(0L)
            .overallDecision(DecisionStatus.GRANTED)
            .autoGranted(true)
            .certificate(java.util.Map.of("certificateNumber", "AUTO-1"))
            .eventHistory(EventHistoryRequest.builder().eventDescription("Auto-granted").build())
            .proceedings(
                List.of(
                    MakeDecisionProceedingRequest.builder()
                        .proceedingId(proceedingId)
                        .meritsDecision(
                            MeritsDecisionDetailsRequest.builder()
                                .decision(MeritsDecisionStatus.GRANTED)
                                .justification("All automatic checks passed")
                                .build())
                        .build()))
            .build();
    restTemplate.exchange(
        "/api/v0/applications/" + applicationId + "/decision",
        HttpMethod.PATCH,
        new HttpEntity<>(decision, headers()),
        Void.class);

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v0/applications/" + applicationId + "/auto-grant-outcome",
            HttpMethod.PATCH,
            new HttpEntity<>(new ManualOutcomeRequest(AutoGrantOutcome.MANUAL, 1L), headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody()).contains("incompatible auto-grant outcome");
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
                    .query(new FindApplicationByIdQuery(applicationId), ApplicationReadModel.class)
                    .join(),
            Objects::nonNull);
  }

  private ApplicationReadModel awaitApplicationVersion(UUID applicationId, long version) {
    return await()
        .atMost(10, TimeUnit.SECONDS)
        .pollInterval(50, TimeUnit.MILLISECONDS)
        .until(
            () ->
                queryGateway
                    .query(new FindApplicationByIdQuery(applicationId), ApplicationReadModel.class)
                    .join(),
            application -> application != null && application.getApplicationVersion() == version);
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
