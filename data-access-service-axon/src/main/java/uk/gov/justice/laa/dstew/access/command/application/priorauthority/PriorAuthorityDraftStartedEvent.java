package uk.gov.justice.laa.dstew.access.command.application.priorauthority;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityContent;
import uk.gov.justice.laa.dstew.access.eventcontent.RedactedEventString;
import uk.gov.justice.laa.dstew.access.eventcontent.RetainedField;

/** Event produced when a Prior Authority draft is first created; carries the draft content. */
@Event(namespace = "access", name = "PriorAuthorityDraftStarted")
public record PriorAuthorityDraftStartedEvent(
    @RetainedField @EventTag(key = "PriorAuthorityAggregate") UUID priorAuthorityId,
    @RetainedField UUID applicationId,
    @RetainedField String priorAuthorityType,
    @RetainedField int schemaVersion,
    @RetainedField Instant occurredAt,
    @RetainedField String officeCode,
    @Nullable PriorAuthorityContent content,
    @Nullable String serialisedRequest)
    implements GenesisScoped {

  public PriorAuthorityDraftStartedEvent(
      UUID priorAuthorityId,
      UUID applicationId,
      String priorAuthorityType,
      int schemaVersion,
      Instant occurredAt) {
    this(priorAuthorityId, applicationId, priorAuthorityType, schemaVersion, occurredAt, null);
  }

  /** Creates the event without draft content, as though the content had been erased. */
  public PriorAuthorityDraftStartedEvent(
      UUID priorAuthorityId,
      UUID applicationId,
      String priorAuthorityType,
      int schemaVersion,
      Instant occurredAt,
      String officeCode) {
    this(
        priorAuthorityId,
        applicationId,
        priorAuthorityType,
        schemaVersion,
        occurredAt,
        officeCode,
        null,
        null);
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
