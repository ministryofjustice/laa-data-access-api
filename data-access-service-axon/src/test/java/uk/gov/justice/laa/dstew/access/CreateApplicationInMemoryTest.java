package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.util.Optional;
import java.util.UUID;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.eventhandling.SubscribingEventProcessor;
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
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationResponse;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadRepository;

@SpringBootTest(
    classes = DataAccessServiceAxonApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "axon.eventstore.jpa.enabled=false",
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.properties.hibernate.default_schema=PUBLIC",
      "spring.datasource.url=jdbc:h2:mem:axon-sync-create;DB_CLOSE_DELAY=-1"
    })
@AutoConfigureTestRestTemplate
@Import(AxonInMemoryConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CreateApplicationInMemoryTest {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ApplicationReadRepository applicationReadRepository;

  @Autowired private EventProcessingConfiguration eventProcessingConfiguration;

  @Test
  void givenStoredDraft_whenSubmitted_thenReturns204AndProjectsState() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applyProceedingId = UUID.randomUUID();
    ApplicationCreateRequest request =
        validCreateApplicationRequest(applyApplicationId, applyProceedingId);

    ResponseEntity<Void> putResponse = putDraft(applyApplicationId, request);
    assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    ResponseEntity<Void> submitResponse = submit(applyApplicationId);
    assertThat(submitResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // Projection is synchronous — immediately available
    Optional<ApplicationReadModel> projected =
        applicationReadRepository.findById(applyApplicationId);
    assertThat(projected).isPresent();
    assertThat(projected.get().getApplyApplicationId()).isEqualTo(applyApplicationId);
    assertThat(projected.get().getStatus())
        .isEqualTo(ApplicationStatus.APPLICATION_SUBMITTED.name());
    assertThat(projected.get().getLaaReference()).isEqualTo("LAA-123");
    assertThat(projected.get().getOfficeCode()).isEqualTo("1A001B");
  }

  @Test
  void givenApplicationProcessor_whenStarted_thenIsSubscribingProcessor() {
    assertThat(eventProcessingConfiguration.eventProcessor("application-projection"))
        .containsInstanceOf(SubscribingEventProcessor.class);
  }

  @Test
  void givenSubmittedApplication_whenSubmittedAgain_thenIdempotent204() {
    UUID applyApplicationId = UUID.randomUUID();
    ApplicationCreateRequest request =
        validCreateApplicationRequest(applyApplicationId, UUID.randomUUID());

    putDraft(applyApplicationId, request);
    ResponseEntity<Void> first = submit(applyApplicationId);
    ResponseEntity<Void> second = submit(applyApplicationId);

    assertThat(first.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(second.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(applicationReadRepository.findById(applyApplicationId)).isPresent();
  }

  @Test
  void givenNoDraft_whenSubmitted_thenReturns404() {
    UUID unknownId = UUID.randomUUID();

    ResponseEntity<String> response =
        restTemplate.exchange(
            "/api/v0/applications/" + unknownId + "/submit",
            HttpMethod.POST,
            new HttpEntity<>(null, headers()),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).contains("No draft application found with ID: " + unknownId);
  }

  @Test
  void givenSubmittedApplication_whenGet_thenRebuildsRichDetailFromSubmissionsPayload() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applyProceedingId = UUID.randomUUID();
    ApplicationCreateRequest request =
        validCreateApplicationRequest(applyApplicationId, applyProceedingId);
    putDraft(applyApplicationId, request);
    submit(applyApplicationId);

    ResponseEntity<ApplicationResponse> response =
        restTemplate.getForEntity(
            "/api/v0/applications/" + applyApplicationId, ApplicationResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    ApplicationResponse body = response.getBody();
    assertThat(body).isNotNull();
    assertThat(body.getApplicationId()).isEqualTo(applyApplicationId);
    assertThat(body.getLaaReference()).isEqualTo("LAA-123");
    assertThat(body.getProvider().getOfficeCode()).isEqualTo("1A001B");
    assertThat(body.getProceedings()).isNotEmpty();
    assertThat(body.getProceedings().get(0).getProceedingDescription()).isEqualTo("Care order");
  }

  @Test
  void givenUnknownId_whenGetApplication_thenReturnsNotFound() {
    UUID unknownId = UUID.randomUUID();

    ResponseEntity<String> response =
        restTemplate.getForEntity("/api/v0/applications/" + unknownId, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).contains("No application found with ID: " + unknownId);
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
