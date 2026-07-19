package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validLinkedCreateApplicationRequest;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.axonframework.config.ConfigurerModule;
import org.axonframework.config.EventProcessingConfiguration;
import org.axonframework.config.ProcessingGroup;
import org.axonframework.eventhandling.EventHandler;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.PropagatingErrorHandler;
import org.axonframework.eventhandling.TrackingEventProcessor;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.query.application.ApplicationReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.history.ApplicationHistoryReadRepository;
import uk.gov.justice.laa.dstew.access.query.application.linkedgroup.LinkedApplicationGroupReadRepository;

/** Verifies replay and transient-failure recovery using real tracking event processors. */
@SpringBootTest(
    classes = DataAccessServiceAxonApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "axon.eventstore.jpa.enabled=false",
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.properties.hibernate.default_schema=PUBLIC",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "spring.datasource.url=jdbc:h2:mem:axon-recovery;DB_CLOSE_DELAY=-1"
    })
@AutoConfigureTestRestTemplate
@Import({AxonInMemoryConfig.class, EventProcessorRecoveryInMemoryTest.RecoveryConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EventProcessorRecoveryInMemoryTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private EventProcessingConfiguration eventProcessingConfiguration;
  @Autowired private EventStore eventStore;
  @Autowired private ApplicationReadRepository applicationReadRepository;
  @Autowired private ApplicationHistoryReadRepository applicationHistoryReadRepository;
  @Autowired private LinkedApplicationGroupReadRepository groupReadRepository;
  @Autowired private FailOnceProjection failOnceProjection;
  @Autowired private PermanentlyFailingProjection permanentlyFailingProjection;

  @Test
  void givenDeletedProjections_whenProcessorsReset_thenReplayRebuildsAllReadModels() {
    UUID applicationId = UUID.randomUUID();
    UUID linkedApplicationId = UUID.randomUUID();
    ApplicationCreateRequest request =
        validCreateApplicationRequest(applicationId, UUID.randomUUID());
    HttpHeaders headers = new HttpHeaders();
    headers.set("X-Service-Name", "CIVIL_APPLY");
    headers.set("X-Schema-Version", "2");
    assertThat(
            restTemplate
                .postForEntity(
                    "/api/v0/applications", new HttpEntity<>(request, headers), Void.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    assertThat(
            restTemplate
                .postForEntity(
                    "/api/v0/applications",
                    new HttpEntity<>(
                        validLinkedCreateApplicationRequest(
                            linkedApplicationId, UUID.randomUUID(), applicationId),
                        headers),
                    Void.class)
                .getStatusCode())
        .isEqualTo(HttpStatus.CREATED);
    await()
        .atMost(Duration.ofSeconds(5))
        .until(
            () ->
                applicationReadRepository.existsById(applicationId)
                    && applicationReadRepository.existsById(linkedApplicationId)
                    && groupReadRepository.findByLeadApplicationId(applicationId).isPresent()
                    && applicationHistoryReadRepository
                            .findAllByApplicationIdOrderByOccurredAtAsc(linkedApplicationId)
                            .size()
                        >= 2);

    var processors =
        java.util.List.of(
            processor("application-projection"),
            processor("application-history-projection"),
            processor("linked-application-group-projection"));
    processors.forEach(TrackingEventProcessor::shutDown);
    applicationReadRepository.deleteAllInBatch();
    applicationHistoryReadRepository.deleteAllInBatch();
    groupReadRepository.deleteAllInBatch();
    assertThat(applicationReadRepository.existsById(applicationId)).isFalse();
    assertThat(applicationHistoryReadRepository.count()).isZero();
    assertThat(groupReadRepository.count()).isZero();

    processors.forEach(TrackingEventProcessor::resetTokens);
    processors.forEach(TrackingEventProcessor::start);

    await()
        .atMost(Duration.ofSeconds(5))
        .until(
            () ->
                applicationReadRepository.existsById(applicationId)
                    && applicationReadRepository.existsById(linkedApplicationId)
                    && groupReadRepository.findByLeadApplicationId(applicationId).isPresent()
                    && applicationHistoryReadRepository
                            .findAllByApplicationIdOrderByOccurredAtAsc(linkedApplicationId)
                            .size()
                        >= 2);
    assertThat(processors)
        .allSatisfy(
            processor -> {
              assertThat(processor.isRunning()).isTrue();
              assertThat(processor.isError()).isFalse();
            });
  }

  @Test
  void givenTransientHandlerFailure_whenEventRetried_thenProcessorRecoversWithoutEventLoss() {
    UUID eventId = UUID.randomUUID();

    eventStore.publish(GenericEventMessage.asEventMessage(new RecoveryTestEvent(eventId)));

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(failOnceProjection.attempts()).isGreaterThanOrEqualTo(2);
              assertThat(failOnceProjection.successfulEventId()).isEqualTo(eventId);
            });
    TrackingEventProcessor processor = processor("recovering-projection");
    assertThat(processor.isRunning()).isTrue();
    assertThat(processor.isError()).isFalse();
  }

  @Test
  void givenPermanentHandlerFailure_whenLaterEventPublished_thenTokenDoesNotAdvancePastFailure() {
    UUID failedEventId = UUID.randomUUID();
    UUID laterEventId = UUID.randomUUID();

    eventStore.publish(
        GenericEventMessage.asEventMessage(new PermanentFailureTestEvent(failedEventId)),
        GenericEventMessage.asEventMessage(new EventAfterPermanentFailure(laterEventId)));

    await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> permanentlyFailingProjection.attempts() >= 2);
    assertThat(permanentlyFailingProjection.successfulEventId()).isNull();
    processor("permanently-failing-projection").shutDown();
  }

  private TrackingEventProcessor processor(String name) {
    return eventProcessingConfiguration
        .eventProcessor(name, TrackingEventProcessor.class)
        .orElseThrow(() -> new AssertionError("Missing tracking processor: " + name));
  }

  record RecoveryTestEvent(UUID eventId) {}

  record PermanentFailureTestEvent(UUID eventId) {}

  record EventAfterPermanentFailure(UUID eventId) {}

  @ProcessingGroup("recovering-projection")
  static class FailOnceProjection {

    private final AtomicInteger attempts = new AtomicInteger();
    private volatile UUID successfulEventId;

    @EventHandler
    public void on(RecoveryTestEvent event) {
      if (attempts.incrementAndGet() == 1) {
        throw new IllegalStateException("Transient projection failure");
      }
      successfulEventId = event.eventId();
    }

    int attempts() {
      return attempts.get();
    }

    UUID successfulEventId() {
      return successfulEventId;
    }
  }

  @ProcessingGroup("permanently-failing-projection")
  static class PermanentlyFailingProjection {

    private final AtomicInteger attempts = new AtomicInteger();
    private volatile UUID successfulEventId;

    @EventHandler
    public void on(PermanentFailureTestEvent event) {
      attempts.incrementAndGet();
      throw new IllegalStateException("Permanent projection failure for " + event.eventId());
    }

    @EventHandler
    public void on(EventAfterPermanentFailure event) {
      successfulEventId = event.eventId();
    }

    int attempts() {
      return attempts.get();
    }

    UUID successfulEventId() {
      return successfulEventId;
    }
  }

  @TestConfiguration
  static class RecoveryConfig {

    @Bean
    FailOnceProjection failOnceProjection() {
      return new FailOnceProjection();
    }

    @Bean
    PermanentlyFailingProjection permanentlyFailingProjection() {
      return new PermanentlyFailingProjection();
    }

    @Bean
    ConfigurerModule recoveringProjectionConfiguration() {
      return configurer -> {
        var eventProcessing = configurer.eventProcessing();
        eventProcessing.registerTrackingEventProcessor("recovering-projection");
        eventProcessing.registerListenerInvocationErrorHandler(
            "recovering-projection", config -> PropagatingErrorHandler.instance());
        eventProcessing.registerTrackingEventProcessor("permanently-failing-projection");
        eventProcessing.registerListenerInvocationErrorHandler(
            "permanently-failing-projection", config -> PropagatingErrorHandler.instance());
      };
    }
  }
}
