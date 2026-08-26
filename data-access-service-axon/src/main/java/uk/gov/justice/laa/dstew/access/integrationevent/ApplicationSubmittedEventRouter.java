package uk.gov.justice.laa.dstew.access.integrationevent;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import java.time.Clock;
import java.util.UUID;
import org.axonframework.messaging.core.annotation.Namespace;
import org.axonframework.messaging.core.unitofwork.ProcessingLifecycle;
import org.axonframework.messaging.eventhandling.EventMessage;
import org.axonframework.messaging.eventhandling.annotation.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;
import uk.gov.justice.laa.dstew.access.config.interceptor.ServiceNameMetadataDispatchInterceptor;

/** Schedules non-replayable integration-event publication after the command commit. */
@Component
@ConditionalOnBean(ApplicationSubmittedPublisher.class)
@Namespace("application-submitted-publisher")
@ExcludeFromGeneratedCodeCoverage
public class ApplicationSubmittedEventRouter {

  private static final Logger LOG = LoggerFactory.getLogger(ApplicationSubmittedEventRouter.class);
  private static final String SUBMITTED = "APPLICATION_SUBMITTED";

  private final ApplicationSubmittedPublisher publisher;
  private final Clock clock;
  private final MeterRegistry meterRegistry;

  /** Creates the after-commit router using the system UTC clock. */
  @Autowired
  public ApplicationSubmittedEventRouter(
      ApplicationSubmittedPublisher publisher, MeterRegistry meterRegistry) {
    this(publisher, Clock.systemUTC(), meterRegistry);
  }

  ApplicationSubmittedEventRouter(ApplicationSubmittedPublisher publisher, Clock clock) {
    this(publisher, clock, Metrics.globalRegistry);
  }

  ApplicationSubmittedEventRouter(
      ApplicationSubmittedPublisher publisher, Clock clock, MeterRegistry meterRegistry) {
    this.publisher = publisher;
    this.clock = clock;
    this.meterRegistry = meterRegistry;
  }

  /** Schedules publication only for Applications created directly in submitted state. */
  @EventHandler
  public void on(
      ApplicationCreatedEvent event, EventMessage message, ProcessingLifecycle lifecycle) {
    if (!SUBMITTED.equals(event.status())) {
      return;
    }
    String correlationId = correlationId(message);
    lifecycle.runOnAfterCommit(
        ignored ->
            publishSafely(applicationSubmittedEvent(event.applicationId(), 0L, correlationId)));
  }

  /** Schedules publication only for updates that cross into submitted state. */
  @EventHandler
  public void on(
      ApplicationUpdatedEvent event, EventMessage message, ProcessingLifecycle lifecycle) {
    if (!event.enteredSubmitted()) {
      return;
    }
    String correlationId = correlationId(message);
    lifecycle.runOnAfterCommit(
        ignored ->
            publishSafely(
                applicationSubmittedEvent(
                    event.applicationId(), event.applicationVersion(), correlationId)));
  }

  private ApplicationSubmittedEvent applicationSubmittedEvent(
      UUID applicationId, long applicationVersion, String correlationId) {
    return new ApplicationSubmittedEvent(
        "ApplicationSubmitted",
        1,
        UUID.randomUUID(),
        clock.instant(),
        "laa-data-access-api",
        correlationId,
        new ApplicationSubmittedData(applicationId, null, applicationVersion));
  }

  private String correlationId(EventMessage message) {
    Object value =
        message.metadata().get(ServiceNameMetadataDispatchInterceptor.CORRELATION_ID_METADATA_KEY);
    return value == null || value.toString().isBlank() ? message.identifier() : value.toString();
  }

  private void publishSafely(ApplicationSubmittedEvent event) {
    try {
      publisher.publish(event);
      recordPublication("success");
      LOG.info(
          "Published ApplicationSubmitted event: eventId={}, applicationId={},"
              + " applicationVersion={}, correlationId={}, eventAgeSeconds={}",
          event.eventId(),
          event.data().applicationId(),
          event.data().applicationVersion(),
          event.correlationId(),
          eventAgeSeconds(event));
    } catch (RuntimeException exception) {
      recordPublication("failure");
      LOG.error(
          "Failed to publish ApplicationSubmitted event after commit: eventId={}, applicationId={},"
              + " applicationVersion={}, correlationId={}, eventAgeSeconds={}",
          event.eventId(),
          event.data().applicationId(),
          event.data().applicationVersion(),
          event.correlationId(),
          eventAgeSeconds(event),
          exception);
    }
  }

  private void recordPublication(String outcome) {
    meterRegistry.counter("application.submitted.publication", "outcome", outcome).increment();
  }

  private long eventAgeSeconds(ApplicationSubmittedEvent event) {
    return Math.max(0, java.time.Duration.between(event.occurredAt(), clock.instant()).toSeconds());
  }
}
