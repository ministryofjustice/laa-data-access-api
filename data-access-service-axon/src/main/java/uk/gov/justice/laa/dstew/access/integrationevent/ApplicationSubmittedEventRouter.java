package uk.gov.justice.laa.dstew.access.integrationevent;

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
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.update.ApplicationUpdatedEvent;
import uk.gov.justice.laa.dstew.access.config.interceptor.ServiceNameMetadataDispatchInterceptor;

/** Schedules non-replayable integration-event publication after the command commit. */
@Component
@ConditionalOnBean(ApplicationSubmittedPublisher.class)
@Namespace("application-submitted-publisher")
public class ApplicationSubmittedEventRouter {

  private static final Logger LOG = LoggerFactory.getLogger(ApplicationSubmittedEventRouter.class);
  private static final String SUBMITTED = "APPLICATION_SUBMITTED";

  private final ApplicationSubmittedPublisher publisher;
  private final Clock clock;

  /** Creates the after-commit router using the system UTC clock. */
  @Autowired
  public ApplicationSubmittedEventRouter(ApplicationSubmittedPublisher publisher) {
    this(publisher, Clock.systemUTC());
  }

  ApplicationSubmittedEventRouter(ApplicationSubmittedPublisher publisher, Clock clock) {
    this.publisher = publisher;
    this.clock = clock;
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
            publishSafely(
                applicationSubmittedEvent(
                    event.applicationId(), event.applyApplicationId(), 0L, correlationId),
                event.applicationType()));
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
                    event.applicationId(),
                    event.applyApplicationId(),
                    event.applicationVersion(),
                    correlationId),
                event.applicationType()));
  }

  private ApplicationSubmittedEvent applicationSubmittedEvent(
      UUID applicationId, UUID applyApplicationId, long applicationVersion, String correlationId) {
    return new ApplicationSubmittedEvent(
        "ApplicationSubmitted",
        1,
        UUID.randomUUID(),
        clock.instant(),
        "laa-data-access-api",
        correlationId,
        new ApplicationSubmittedData(applicationId, applyApplicationId, null, applicationVersion));
  }

  private String correlationId(EventMessage message) {
    Object value =
        message.metadata().get(ServiceNameMetadataDispatchInterceptor.CORRELATION_ID_METADATA_KEY);
    return value == null || value.toString().isBlank() ? message.identifier() : value.toString();
  }

  private void publishSafely(ApplicationSubmittedEvent event, String applicationType) {
    try {
      publisher.publish(event, applicationType);
      LOG.info(
          "Published ApplicationSubmitted event: eventId={}, applicationId={}, correlationId={}",
          event.eventId(),
          event.data().applicationId(),
          event.correlationId());
    } catch (RuntimeException exception) {
      LOG.error(
          "Failed to publish ApplicationSubmitted event after commit: eventId={}, applicationId={}, correlationId={}",
          event.eventId(),
          event.data().applicationId(),
          event.correlationId(),
          exception);
    }
  }
}
