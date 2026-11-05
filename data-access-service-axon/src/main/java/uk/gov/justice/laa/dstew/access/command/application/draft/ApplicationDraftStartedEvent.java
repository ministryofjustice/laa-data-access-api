package uk.gov.justice.laa.dstew.access.command.application.draft;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;
import uk.gov.justice.laa.dstew.access.model.PotentialDuplicate;

/**
 * Event indicating that an Application draft has been started for the given ID. It carries the
 * draft's content, which is stored apart from the event so that it can be erased.
 */
@Event(namespace = "access", name = "ApplicationDraftStarted")
public record ApplicationDraftStartedEvent(
    @EventTag(key = "ApplicationAggregate") UUID applicationId,
    int schemaVersion,
    String requestFingerprint,
    Instant occurredAt,
    String officeCode,
    String status,
    String laaReference,
    Erasable<Map<String, Object>> applicationContent,
    Erasable<String> serialisedRequest,
    Erasable<List<PotentialDuplicate>> potentialDuplicates) {

  /** Treats absent content as erased so a stored event without its content row still loads. */
  public ApplicationDraftStartedEvent {
    applicationContent = Erasable.orErased(applicationContent);
    serialisedRequest = Erasable.orErased(serialisedRequest);
    potentialDuplicates = Erasable.orErased(potentialDuplicates);
  }

  /** Creates the event from plain content values. */
  public ApplicationDraftStartedEvent(
      UUID applicationId,
      int schemaVersion,
      String requestFingerprint,
      Instant occurredAt,
      String officeCode,
      String status,
      String laaReference,
      Map<String, Object> applicationContent,
      String serialisedRequest,
      List<PotentialDuplicate> potentialDuplicates) {
    this(
        applicationId,
        schemaVersion,
        requestFingerprint,
        occurredAt,
        officeCode,
        status,
        laaReference,
        Erasable.of(applicationContent),
        Erasable.of(serialisedRequest),
        Erasable.of(potentialDuplicates));
  }
}
