package uk.gov.justice.laa.dstew.access.command.application.note;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;
import uk.gov.justice.laa.dstew.access.eventcontent.RedactedEventString;
import uk.gov.justice.laa.dstew.access.eventcontent.RetainedField;

/** Records that a note was added to an Application. The note text is erasable content. */
@Event(namespace = "access", name = "NoteCreated")
public record NoteCreatedEvent(
    @RetainedField @EventTag(key = "ApplicationAggregate") UUID applicationId,
    @RetainedField long applicationDataVersion,
    @RetainedField Instant occurredAt,
    @Nullable String noteText)
    implements GenesisScoped {

  /** Creates the event without note text. */
  public NoteCreatedEvent(UUID applicationId, long applicationDataVersion, Instant occurredAt) {
    this(applicationId, applicationDataVersion, occurredAt, null);
  }

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(applicationId);
  }

  @Override
  public String toString() {
    return RedactedEventString.of(this);
  }
}
