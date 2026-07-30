package uk.gov.justice.laa.dstew.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validCreateApplicationRequest;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreateRequestFixture.validLinkedCreateApplicationRequest;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.axonframework.common.configuration.AxonConfiguration;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.extension.spring.config.EventProcessorDefinition;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.axonframework.messaging.eventhandling.processing.errorhandling.PropagatingErrorHandler;
import org.axonframework.messaging.eventhandling.processing.streaming.StreamingEventProcessor;
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
      "spring.flyway.enabled=false",
      "spring.jpa.hibernate.ddl-auto=create-drop",
      "spring.jpa.properties.hibernate.default_schema=PUBLIC",
      "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
      "spring.datasource.url=jdbc:h2:mem:axon-recovery;DB_CLOSE_DELAY=-1"
    })
@AutoConfigureTestRestTemplate
@Import(EventProcessorRecoveryInMemoryTest.RecoveryConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EventProcessorRecoveryInMemoryTest {

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private AxonConfiguration axonConfiguration;
  @Autowired private EventStore eventStore;
  @Autowired private ApplicationReadRepository applicationReadRepository;
  @Autowired private ApplicationHistoryReadRepository applicationHistoryReadRepository;
  @Autowired private LinkedApplicationGroupReadRepository groupReadRepository;
  @Autowired private FailOnceProjection failOnceProjection;
  @Autowired private PermanentlyFailingProjection permanentlyFailingProjection;

  @Test
  void givenDeletedProjections_whenProcessorsReset_thenReplayRebuildsAllReadModels() {
    UUID applicationId = UUID.randomUUID();
    final UUID linkedApplicationId = UUID.randomUUID();
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
    processors.forEach(processor -> processor.shutdown().join());
    applicationReadRepository.deleteAllInBatch();
    applicationHistoryReadRepository.deleteAllInBatch();
    groupReadRepository.deleteAllInBatch();
    assertThat(applicationReadRepository.existsById(applicationId)).isFalse();
    assertThat(applicationHistoryReadRepository.count()).isZero();
    assertThat(groupReadRepository.count()).isZero();

    processors.forEach(processor -> processor.resetTokens().join());
    processors.forEach(processor -> processor.start().join());

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

    eventStore
        .publish(
            null,
            new GenericEventMessage(
                new MessageType(RecoveryTestEvent.class), new RecoveryTestEvent(eventId)))
        .join();

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              assertThat(failOnceProjection.attempts()).isGreaterThanOrEqualTo(2);
              assertThat(failOnceProjection.successfulEventId()).isEqualTo(eventId);
            });
    StreamingEventProcessor processor = processor("recovering-projection");
    assertThat(processor.isRunning()).isTrue();
    assertThat(processor.isError()).isFalse();
  }

  @Test
  void givenPermanentHandlerFailure_whenLaterEventPublished_thenTokenDoesNotAdvancePastFailure() {
    UUID failedEventId = UUID.randomUUID();
    UUID laterEventId = UUID.randomUUID();

    eventStore
        .publish(
            null,
            new GenericEventMessage(
                new MessageType(PermanentFailureTestEvent.class),
                new PermanentFailureTestEvent(failedEventId)),
            new GenericEventMessage(
                new MessageType(EventAfterPermanentFailure.class),
                new EventAfterPermanentFailure(laterEventId)))
        .join();

    await()
        .atMost(Duration.ofSeconds(10))
        .until(() -> permanentlyFailingProjection.attempts() >= 2);
    assertThat(permanentlyFailingProjection.successfulEventId()).isNull();
    processor("permanently-failing-projection").shutdown().join();
  }

  private StreamingEventProcessor processor(String name) {
    StreamingEventProcessor processor =
        axonConfiguration.getComponents(StreamingEventProcessor.class).get(name);
    if (processor == null) {
      throw new AssertionError("Missing streaming processor: " + name);
    }
    return processor;
  }

  record RecoveryTestEvent(UUID eventId) {}

  record PermanentFailureTestEvent(UUID eventId) {}

  record EventAfterPermanentFailure(UUID eventId) {}

  @Namespace("recovering-projection")
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

  @Namespace("permanently-failing-projection")
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
    EventProcessorDefinition recoveringProjectionProcessor() {
      return EventProcessorDefinition.pooledStreamingMatching("recovering-projection")
          .customized(
              configuration -> configuration.errorHandler(PropagatingErrorHandler.instance()));
    }

    @Bean
    EventProcessorDefinition permanentlyFailingProjectionProcessor() {
      return EventProcessorDefinition.pooledStreamingMatching("permanently-failing-projection")
          .customized(
              configuration ->
                  configuration
                      .initialSegmentCount(1)
                      .maxClaimedSegments(1)
                      .batchSize(1)
                      .errorHandler(PropagatingErrorHandler.instance()));
    }
  }
}
