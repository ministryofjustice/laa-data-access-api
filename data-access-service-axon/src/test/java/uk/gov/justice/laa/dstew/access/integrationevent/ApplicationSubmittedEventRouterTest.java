package uk.gov.justice.laa.dstew.access.integrationevent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.axonframework.messaging.core.Metadata;
import org.axonframework.messaging.core.unitofwork.ProcessingContext;
import org.axonframework.messaging.core.unitofwork.ProcessingLifecycle;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;

@ExtendWith(OutputCaptureExtension.class)
class ApplicationSubmittedEventRouterTest {

  private static final Instant COMMITTED_AT = Instant.parse("2026-08-03T09:30:00Z");

  @Test
  void givenSubmittedApplicationCreated_whenCommitCompletes_thenPublishesThinIntegrationEvent() {
    RecordingPublisher publisher = new RecordingPublisher();
    ApplicationSubmittedEventRouter router =
        new ApplicationSubmittedEventRouter(publisher, Clock.fixed(COMMITTED_AT, ZoneOffset.UTC));
    ProcessingLifecycle lifecycle = mock(ProcessingLifecycle.class);
    EventMessage message = eventMessage(Map.of("correlationId", "corr-2096"));
    ApplicationCreatedEvent event = createdEvent("APPLICATION_SUBMITTED");

    router.on(event, message, lifecycle);

    assertThat(publisher.events).isEmpty();
    ArgumentCaptor<Consumer<ProcessingContext>> callback = consumerCaptor();
    verify(lifecycle).runOnAfterCommit(callback.capture());

    callback.getValue().accept(mock(ProcessingContext.class));

    assertThat(publisher.events)
        .singleElement()
        .satisfies(
            published -> {
              assertThat(published.applicationType()).isEqualTo("APPLY");
              assertThat(published.event().eventType()).isEqualTo("ApplicationSubmitted");
              assertThat(published.event().schemaVersion()).isEqualTo(1);
              assertThat(published.event().occurredAt()).isEqualTo(COMMITTED_AT);
              assertThat(published.event().source()).isEqualTo("laa-data-access-api");
              assertThat(published.event().correlationId()).isEqualTo("corr-2096");
              assertThat(published.event().data().applicationId()).isEqualTo(event.applicationId());
              assertThat(published.event().data().applyApplicationId())
                  .isEqualTo(event.applyApplicationId());
              assertThat(published.event().data().laaReference()).isNull();
              assertThat(published.event().data().applicationVersion()).isZero();
            });
  }

  @Test
  void givenApplicationCreatedInProgress_whenHandled_thenDoesNotSchedulePublication() {
    ApplicationSubmittedPublisher publisher = mock(ApplicationSubmittedPublisher.class);
    ApplicationSubmittedEventRouter router =
        new ApplicationSubmittedEventRouter(publisher, Clock.fixed(COMMITTED_AT, ZoneOffset.UTC));
    ProcessingLifecycle lifecycle = mock(ProcessingLifecycle.class);

    router.on(createdEvent("APPLICATION_IN_PROGRESS"), eventMessage(Map.of()), lifecycle);

    verify(lifecycle, never()).runOnAfterCommit(any());
    verify(publisher, never()).publish(any(), any());
  }

  @Test
  void givenApplicationEntersSubmittedOnUpdate_whenCommitCompletes_thenPublishesCurrentVersion() {
    RecordingPublisher publisher = new RecordingPublisher();
    ApplicationSubmittedEventRouter router =
        new ApplicationSubmittedEventRouter(publisher, Clock.fixed(COMMITTED_AT, ZoneOffset.UTC));
    ProcessingLifecycle lifecycle = mock(ProcessingLifecycle.class);
    UUID id = UUID.fromString("8c9e6c2e-4f1a-4e3a-9c2b-1a2b3c4d5e6f");
    ApplicationUpdatedEvent event =
        new ApplicationUpdatedEvent(
            id,
            4L,
            7L,
            "APPLICATION_IN_PROGRESS",
            "APPLICATION_SUBMITTED",
            "APPLY",
            id,
            COMMITTED_AT.minusSeconds(1));

    router.on(event, eventMessage(Map.of("correlationId", "corr-update")), lifecycle);

    ArgumentCaptor<Consumer<ProcessingContext>> callback = consumerCaptor();
    verify(lifecycle).runOnAfterCommit(callback.capture());
    callback.getValue().accept(mock(ProcessingContext.class));
    assertThat(publisher.events)
        .singleElement()
        .satisfies(
            published -> {
              assertThat(published.event().data().applicationVersion()).isEqualTo(4L);
              assertThat(published.event().correlationId()).isEqualTo("corr-update");
            });
  }

  @Test
  void givenUpdateRemainsSubmitted_whenHandled_thenDoesNotSchedulePublication() {
    ApplicationSubmittedPublisher publisher = mock(ApplicationSubmittedPublisher.class);
    ApplicationSubmittedEventRouter router =
        new ApplicationSubmittedEventRouter(publisher, Clock.fixed(COMMITTED_AT, ZoneOffset.UTC));
    ProcessingLifecycle lifecycle = mock(ProcessingLifecycle.class);
    UUID id = UUID.randomUUID();
    ApplicationUpdatedEvent event =
        new ApplicationUpdatedEvent(
            id,
            2L,
            2L,
            "APPLICATION_SUBMITTED",
            "APPLICATION_SUBMITTED",
            "APPLY",
            id,
            COMMITTED_AT);

    router.on(event, eventMessage(Map.of()), lifecycle);

    verify(lifecycle, never()).runOnAfterCommit(any());
    verify(publisher, never()).publish(any(), any());
  }

  @Test
  void givenPublishFailsAfterCommit_whenCallbackRuns_thenFailureIsLoggedAndNotPropagated(
      CapturedOutput output) {
    ApplicationSubmittedPublisher publisher = mock(ApplicationSubmittedPublisher.class);
    doThrow(new IllegalStateException("SNS unavailable")).when(publisher).publish(any(), any());
    ApplicationSubmittedEventRouter router =
        new ApplicationSubmittedEventRouter(
            publisher, Clock.fixed(COMMITTED_AT, ZoneOffset.UTC), new SimpleMeterRegistry());
    ProcessingLifecycle lifecycle = mock(ProcessingLifecycle.class);
    router.on(createdEvent("APPLICATION_SUBMITTED"), eventMessage(Map.of()), lifecycle);
    ArgumentCaptor<Consumer<ProcessingContext>> callback = consumerCaptor();
    verify(lifecycle).runOnAfterCommit(callback.capture());

    callback.getValue().accept(mock(ProcessingContext.class));

    assertThat(output)
        .contains("Failed to publish ApplicationSubmitted event after commit")
        .contains("SNS unavailable");
  }

  @Test
  void givenPublicationCompletes_whenCallbackRuns_thenRecordsTheAttemptOutcome() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    ApplicationSubmittedEventRouter router =
        new ApplicationSubmittedEventRouter(
            new RecordingPublisher(), Clock.fixed(COMMITTED_AT, ZoneOffset.UTC), meterRegistry);
    ProcessingLifecycle lifecycle = mock(ProcessingLifecycle.class);
    router.on(
        createdEvent("APPLICATION_SUBMITTED"),
        eventMessage(Map.of("correlationId", "corr-2100")),
        lifecycle);
    ArgumentCaptor<Consumer<ProcessingContext>> callback = consumerCaptor();
    verify(lifecycle).runOnAfterCommit(callback.capture());

    callback.getValue().accept(mock(ProcessingContext.class));

    assertThat(
            meterRegistry
                .get("application.submitted.publication")
                .tag("outcome", "success")
                .counter()
                .count())
        .isEqualTo(1);
  }

  private ApplicationCreatedEvent createdEvent(String status) {
    UUID id = UUID.fromString("8c9e6c2e-4f1a-4e3a-9c2b-1a2b3c4d5e6f");
    return new ApplicationCreatedEvent(
        id,
        0L,
        "fingerprint",
        status,
        1,
        "APPLY",
        id,
        COMMITTED_AT.minusSeconds(1),
        null,
        List.of());
  }

  private EventMessage eventMessage(Map<String, String> metadata) {
    EventMessage message = mock(EventMessage.class);
    when(message.metadata()).thenReturn(Metadata.from(metadata));
    return message;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private ArgumentCaptor<Consumer<ProcessingContext>> consumerCaptor() {
    return (ArgumentCaptor) ArgumentCaptor.forClass(Consumer.class);
  }

  private static final class RecordingPublisher implements ApplicationSubmittedPublisher {
    private final java.util.ArrayList<Published> events = new java.util.ArrayList<>();

    @Override
    public void publish(ApplicationSubmittedEvent event, String applicationType) {
      events.add(new Published(event, applicationType));
    }
  }

  private record Published(ApplicationSubmittedEvent event, String applicationType) {}
}
