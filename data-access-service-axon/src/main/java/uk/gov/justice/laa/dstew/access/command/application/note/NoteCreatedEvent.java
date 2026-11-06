package uk.gov.justice.laa.dstew.access.command.application.note;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;

/** Records that a note was added to an Application. The note text is erasable content. */
@Event(namespace = "access", name = "NoteCreated")
public record NoteCreatedEvent(
    @EventTag(key = "ApplicationAggregate") UUID applicationId,
    long applicationDataVersion,
    Instant occurredAt,
    Erasable<String> noteText) {

  public NoteCreatedEvent {
    noteText = Erasable.orErased(noteText);
  }

  /** Creates the event from a plain note text. */
  public NoteCreatedEvent(
      UUID applicationId, long applicationDataVersion, Instant occurredAt, String noteText) {
    this(applicationId, applicationDataVersion, occurredAt, Erasable.of(noteText));
  }

  /** Creates the event without note text. */
  public NoteCreatedEvent(UUID applicationId, long applicationDataVersion, Instant occurredAt) {
    this(applicationId, applicationDataVersion, occurredAt, Erasable.<String>erased());
  }
}
