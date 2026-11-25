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

/**
 * Event emitted when a Prior Authority draft is submitted. It carries the submitted content as
 * erasable content; the same content is still written as version 0 of {@code prior_authority_data}
 * for the existing read paths.
 *
 * <p>{@code applicationDataVersion} pins the parent application's immutable data version as it
 * stood when the PA was submitted, so the work-list projection can hydrate parent-application
 * fields deterministically on replay. It is a nullable {@link Long} so that events persisted before
 * this field existed still deserialize under Jackson (which rejects {@code null} into a primitive);
 * absent is normalised to {@code 0L} — the parent application's original data version, which always
 * exists.
 */
@Event(namespace = "access", name = "PriorAuthoritySubmitted")
public record PriorAuthoritySubmittedEvent(
    @RetainedField @EventTag(key = "PriorAuthorityAggregate") UUID priorAuthorityId,
    @RetainedField UUID applicationId,
    @RetainedField String priorAuthorityType,
    @RetainedField int schemaVersion,
    @RetainedField long dataVersion,
    @RetainedField Long applicationDataVersion,
    @RetainedField Instant occurredAt,
    @RetainedField String billingType,
    @Nullable PriorAuthorityContent content)
    implements GenesisScoped {

  /** Normalises a missing (pre-field) {@code applicationDataVersion} to {@code 0L}. */
  public PriorAuthoritySubmittedEvent {
    if (applicationDataVersion == null) {
      applicationDataVersion = 0L;
    }
  }

  /** Creates the event without submitted content, as though the content had been erased. */
  public PriorAuthoritySubmittedEvent(
      UUID priorAuthorityId,
      UUID applicationId,
      String priorAuthorityType,
      int schemaVersion,
      long dataVersion,
      Long applicationDataVersion,
      Instant occurredAt,
      String billingType) {
    this(
        priorAuthorityId,
        applicationId,
        priorAuthorityType,
        schemaVersion,
        dataVersion,
        applicationDataVersion,
        occurredAt,
        billingType,
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
