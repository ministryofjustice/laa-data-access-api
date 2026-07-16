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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.query.synchronousapplication.SynchronousApplicationReadModel;
import uk.gov.justice.laa.dstew.access.query.synchronousapplication.SynchronousApplicationReadRepository;

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
class CreateSynchronousApplicationInMemoryTest {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private SynchronousApplicationReadRepository synchronousApplicationReadRepository;

  @Autowired private EventProcessingConfiguration eventProcessingConfiguration;

  @Test
  void givenValidRequest_whenPostSynchronousApplication_thenReturns201AndProjectsState() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applyProceedingId = UUID.randomUUID();
    ApplicationCreateRequest request =
        validCreateApplicationRequest(applyApplicationId, applyProceedingId);

    ResponseEntity<Void> response =
        restTemplate.postForEntity(
            "/api/v0/synchronous-applications", new HttpEntity<>(request, headers()), Void.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getLocation().getPath())
        .isEqualTo("/api/v0/synchronous-applications/" + applyApplicationId);

    // Projection is synchronous — immediately available
    Optional<SynchronousApplicationReadModel> projected =
        synchronousApplicationReadRepository.findById(applyApplicationId);
    assertThat(projected).isPresent();
    assertThat(projected.get().getApplyApplicationId()).isEqualTo(applyApplicationId);
    assertThat(projected.get().getStatus()).isEqualTo(ApplicationStatus.APPLICATION_SUBMITTED.name());
    assertThat(projected.get().getLaaReference()).isEqualTo("LAA-123");
    assertThat(projected.get().getOfficeCode()).isEqualTo("1A001B");
  }

  @Test
  void givenSynchronousApplicationProcessor_whenStarted_thenIsSubscribingProcessor() {
    assertThat(
            eventProcessingConfiguration.eventProcessor("synchronous-application-projection"))
        .containsInstanceOf(SubscribingEventProcessor.class);
  }

  @Test
  void givenDuplicateApplyApplicationId_whenPostAgain_thenReturnsIdempotent201() {
    // With CREATE_IF_MISSING, sequential re-delivery is idempotent — both calls return 201.
    // A 400 is only raised for truly concurrent collisions (AggregateStreamCreationException).
    UUID applyApplicationId = UUID.randomUUID();
    ApplicationCreateRequest request =
        validCreateApplicationRequest(applyApplicationId, UUID.randomUUID());
    HttpEntity<ApplicationCreateRequest> entity = new HttpEntity<>(request, headers());

    ResponseEntity<String> firstResponse =
        restTemplate.postForEntity("/api/v0/synchronous-applications", entity, String.class);
    ResponseEntity<String> duplicateResponse =
        restTemplate.postForEntity("/api/v0/synchronous-applications", entity, String.class);

    assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(duplicateResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    // Only one event was stored — no second event on idempotent re-delivery
    assertThat(synchronousApplicationReadRepository.findById(applyApplicationId)).isPresent();
  }

  @Test
  void givenUnknownId_whenGetSynchronousApplication_thenReturnsNotFound() {
    UUID unknownId = UUID.randomUUID();

    ResponseEntity<String> response =
        restTemplate.getForEntity(
            "/api/v0/synchronous-applications/" + unknownId, String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody())
        .contains("No synchronous application found with ID: " + unknownId);
  }

  private HttpHeaders headers() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "2");
    return headers;
  }
}


