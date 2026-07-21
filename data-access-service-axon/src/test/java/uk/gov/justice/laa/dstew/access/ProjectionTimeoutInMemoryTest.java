package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;

import java.util.UUID;
import org.axonframework.common.configuration.EventProcessingConfiguration;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.eventhandling.TrackingEventProcessor;
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
      "axon.eventstore.jpa.enabled=false",
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.properties.hibernate.default_schema=PUBLIC",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "spring.datasource.url=jdbc:h2:mem:axon-timeout;DB_CLOSE_DELAY=-1",
      "application.projection.timeout=200ms"
    })
@AutoConfigureTestRestTemplate
@Import(AxonInMemoryConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProjectionTimeoutInMemoryTest {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private EventProcessingConfiguration eventProcessingConfiguration;

  @Autowired private EventStore eventStore;

  @Test
  void givenStoppedProjectionProcessor_whenPostApplication_thenReturnsAcceptedWithLocation() {
    // Stop the projection processor so no QueryUpdateEmitter.emit can be called for this command.
    eventProcessingConfiguration
        .eventProcessor("application-projection", TrackingEventProcessor.class)
        .ifPresent(TrackingEventProcessor::shutdown);

    UUID applyApplicationId = UUID.randomUUID();
    ApplicationCreateRequest request =
        validCreateApplicationRequest(applyApplicationId, UUID.randomUUID());
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "2");

    long startMs = System.currentTimeMillis();
    ResponseEntity<Void> response =
        restTemplate.postForEntity(
            "/api/v0/applications", new HttpEntity<>(request, headers), Void.class);
    long elapsedMs = System.currentTimeMillis() - startMs;

    // Command committed → 202 because projection never appeared within the 200 ms timeout.
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    assertThat(response.getHeaders().getLocation()).isNotNull();
    assertThat(response.getHeaders().getLocation().getPath())
        .isEqualTo("/api/v0/applications/" + applyApplicationId);
    assertThat(eventStore.readEvents(applyApplicationId.toString()).asStream())
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getSequenceNumber()).isZero();
              assertThat(event.payloadType().getSimpleName()).isEqualTo("ApplicationCreatedEvent");
            });

    // The test must complete well below the full 5-second default timeout.
    assertThat(elapsedMs).isLessThan(3_000L);
  }
}
