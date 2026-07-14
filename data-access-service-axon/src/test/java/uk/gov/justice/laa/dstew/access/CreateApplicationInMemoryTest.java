package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

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
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.IndividualType;
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
      "spring.datasource.url=jdbc:h2:mem:axon-create;DB_CLOSE_DELAY=-1"
    })
@AutoConfigureTestRestTemplate
@Import(AxonInMemoryConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CreateApplicationInMemoryTest {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private ApplicationReadRepository applicationReadRepository;

  @Autowired private EventProcessingConfiguration eventProcessingConfiguration;

  @Test
  void givenValidRequest_whenPostApplication_thenReturnsCreatedAndProjectsOwnedState()
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

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
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

    var processor = eventProcessingConfiguration.eventProcessor("application-projection");
    assertThat(processor).isPresent();
    assertThat(processor.get()).isInstanceOf(TrackingEventProcessor.class);
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
}
