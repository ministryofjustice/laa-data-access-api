package uk.gov.justice.laa.dstew.access.command.application.update;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.applicationcontent.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;

/**
 * Replaces an Application's content. The replacement content and request are erasable content;
 * proceeding IDs are kept as linking keys.
 */
@Event(namespace = "access", name = "ApplicationUpdated")
public record ApplicationUpdatedEvent(
    @EventTag(key = "ApplicationAggregate") UUID applicationId,
    long applicationVersion,
    long applicationDataVersion,
    String previousStatus,
    String status,
    Instant occurredAt,
    List<UUID> proceedingIds,
    Erasable<ParsedAppContentDetails> content,
    Erasable<String> serialisedRequest) {

  /** Normalises missing content to erased. */
  public ApplicationUpdatedEvent {
    proceedingIds = proceedingIds == null ? List.of() : List.copyOf(proceedingIds);
    content = Erasable.orErased(content);
    serialisedRequest = Erasable.orErased(serialisedRequest);
  }

  /** Creates the event from plain content values. */
  public ApplicationUpdatedEvent(
      UUID applicationId,
      long applicationVersion,
      long applicationDataVersion,
      String previousStatus,
      String status,
      Instant occurredAt,
      List<UUID> proceedingIds,
      ParsedAppContentDetails content,
      String serialisedRequest) {
    this(
        applicationId,
        applicationVersion,
        applicationDataVersion,
        previousStatus,
        status,
        occurredAt,
        proceedingIds,
        Erasable.of(content),
        Erasable.of(serialisedRequest));
  }

  /** Creates the event without replacement content or proceedings. */
  public ApplicationUpdatedEvent(
      UUID applicationId,
      long applicationVersion,
      long applicationDataVersion,
      String previousStatus,
      String status,
      Instant occurredAt) {
    this(
        applicationId,
        applicationVersion,
        applicationDataVersion,
        previousStatus,
        status,
        occurredAt,
        List.of(),
        (ParsedAppContentDetails) null,
        null);
  }

  public boolean enteredSubmitted() {
    return !"APPLICATION_SUBMITTED".equals(previousStatus)
        && "APPLICATION_SUBMITTED".equals(status);
  }
}
