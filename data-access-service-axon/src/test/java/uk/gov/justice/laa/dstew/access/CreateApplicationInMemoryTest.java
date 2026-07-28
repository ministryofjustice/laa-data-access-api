package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validLinkedCreateApplicationRequest;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import uk.gov.justice.laa.dstew.access.command.application.CreateApplicationCommandHandler;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryReadRepository;

@SpringBootTest(
    classes = DataAccessServiceAxonApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "axon.eventstore.jpa.enabled=false",
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.properties.hibernate.default_schema=PUBLIC",
      "spring.datasource.url=jdbc:h2:mem:axon-create;DB_CLOSE_DELAY=-1"
    })
@AutoConfigureTestRestTemplate
@Import(AxonInMemoryConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CreateApplicationInMemoryTest {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ApplicationReadRepository applicationReadRepository;

  @Autowired private ApplicationHistoryReadRepository applicationHistoryReadRepository;

  @Autowired private EventProcessingConfiguration eventProcessingConfiguration;

  @MockitoSpyBean private CreateApplicationCommandHandler createApplicationCommandHandler;

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
  void givenValidRequest_whenPostApplication_thenReturnsAcceptedAndProjectsOwnedState()
      throws Exception {
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

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getLocation().getPath())
        .matches("/api/v0/applications/[a-f0-9-]{36}");

    UUID applicationId =
        UUID.fromString(
            response.getHeaders().getLocation().getPath().replace("/api/v0/applications/", ""));
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
              assertThat(history.getRequestPayload()).contains("\"laaReference\"", "\"LAA-123\"");
              assertThat(history.getServiceName()).isEqualTo("CIVIL_APPLY");
            });

    var processor = eventProcessingConfiguration.eventProcessor("application-projection");
    assertThat(processor).isPresent();
    assertThat(processor.get()).isInstanceOf(TrackingEventProcessor.class);
    assertThat(eventProcessingConfiguration.eventProcessor("application-history-projection"))
        .containsInstanceOf(TrackingEventProcessor.class);
  }

  @Test
  void givenSchemaInvalidRequest_whenPostApplication_thenReturnsBadRequestBeforeHandlerRuns()
      throws Exception {
    ApplicationCreateRequest validRequest =
        validCreateApplicationRequest(UUID.randomUUID(), UUID.randomUUID());
    var invalidContent = new java.util.HashMap<>(validRequest.getApplicationContent());
    invalidContent.remove("id");
    validRequest.setApplicationContent(invalidContent);

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            "/api/v0/applications", new HttpEntity<>(validRequest, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).contains("Generic Validation Error");
    verify(createApplicationCommandHandler, never()).handle(any());
  }

  @Test
  void
      givenExistingApplyApplicationId_whenPostApplicationAgain_thenReturnsDuplicateValidationError() {
    UUID applyApplicationId = UUID.randomUUID();
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    HttpEntity<ApplicationCreateRequest> request =
        new HttpEntity<>(
            validCreateApplicationRequest(applyApplicationId, UUID.randomUUID()), headers);

    ResponseEntity<String> firstResponse =
        restTemplate.postForEntity("/api/v0/applications", request, String.class);
    ResponseEntity<String> duplicateResponse =
        restTemplate.postForEntity("/api/v0/applications", request, String.class);

    assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(duplicateResponse.getBody())
        .contains("Generic Validation Error")
        .contains("Application already exists for Apply Application Id: " + applyApplicationId);
  }

  @Test
  void givenMissingLeadApplication_whenPostApplication_thenReturnsNotFoundBeforeClaiming()
      throws Exception {
    UUID missingApplyApplicationId = UUID.randomUUID();
    ApplicationCreateRequest request =
        validLinkedCreateApplicationRequest(
            UUID.randomUUID(), UUID.randomUUID(), missingApplyApplicationId);

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            "/api/v0/applications", new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).contains(missingApplyApplicationId.toString());
  }

  @Test
  void givenMissingAssociatedApplication_whenPostApplication_thenReturnsNotFoundBeforeClaiming()
      throws Exception {
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
    ApplicationCreateRequest request =
        validLinkedCreateApplicationRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            leadApplyApplicationId,
            missingAssociatedApplyApplicationId);

    ResponseEntity<String> response =
        restTemplate.postForEntity(
            "/api/v0/applications", new HttpEntity<>(request, headers()), String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).contains(missingAssociatedApplyApplicationId.toString());
  }

  @Test
  void givenExistingLeadApplication_whenPostLinkedApplication_thenProjectsLeadLink()
      throws Exception {
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

    assertThat(linkedResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    ApplicationReadModel projected = awaitProjection(applicationId(linkedResponse));
    assertThat(projected.getLeadApplicationId()).isEqualTo(leadApplicationId);
    assertThat(awaitHistory(projected.getApplicationId(), 2))
        .extracting(history -> history.getEventType())
        .containsExactly("APPLICATION_CREATED", "APPLICATION_LINKED");
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "2");
    return headers;
  }

  private UUID applicationId(ResponseEntity<Void> response) {
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getLocation()).isNotNull();
    return UUID.fromString(
        response.getHeaders().getLocation().getPath().replace("/api/v0/applications/", ""));
  }

  private ApplicationReadModel awaitProjection(UUID applicationId) throws Exception {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      var projected = applicationReadRepository.findById(applicationId);
      if (projected.isPresent()) {
        return projected.get();
      }
      Thread.sleep(50);
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
