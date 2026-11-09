package uk.gov.justice.laa.dstew.access.command.application;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.axonframework.eventsourcing.annotation.EventTag;
import org.axonframework.messaging.eventhandling.annotation.Event;
import uk.gov.justice.laa.dstew.access.applicationcontent.ParsedAppContentDetails;
import uk.gov.justice.laa.dstew.access.command.GenesisScoped;
import uk.gov.justice.laa.dstew.access.eventcontent.Erasable;
import uk.gov.justice.laa.dstew.access.model.PotentialDuplicate;

/**
 * Establishes an Application aggregate. The submitted content and request are erasable content;
 * proceeding IDs are kept as linking keys.
 */
@Event(namespace = "access", name = "ApplicationCreated")
public record ApplicationCreatedEvent(
    @EventTag(key = "ApplicationAggregate") UUID applicationId,
    String requestFingerprint,
    String status,
    int schemaVersion,
    Instant occurredAt,
    Erasable<List<PotentialDuplicate>> potentialDuplicates,
    String officeCode,
    String laaReference,
    List<UUID> proceedingIds,
    Erasable<ParsedAppContentDetails> content,
    Erasable<String> serialisedRequest)
    implements GenesisScoped {

  /** Normalises missing content to erased. */
  public ApplicationCreatedEvent {
    potentialDuplicates = Erasable.orErased(potentialDuplicates);
    proceedingIds = proceedingIds == null ? List.of() : List.copyOf(proceedingIds);
    content = Erasable.orErased(content);
    serialisedRequest = Erasable.orErased(serialisedRequest);
  }

  /** Creates the event from plain content values. */
  public ApplicationCreatedEvent(
      UUID applicationId,
      String requestFingerprint,
      String status,
      int schemaVersion,
      Instant occurredAt,
      List<PotentialDuplicate> potentialDuplicates,
      String officeCode,
      String laaReference,
      List<UUID> proceedingIds,
      ParsedAppContentDetails content,
      String serialisedRequest) {
    this(
        applicationId,
        requestFingerprint,
        status,
        schemaVersion,
        occurredAt,
        Erasable.of(potentialDuplicates),
        officeCode,
        laaReference,
        proceedingIds,
        Erasable.of(content),
        Erasable.of(serialisedRequest));
  }

  /** Creates the event without submitted content or proceedings. */
  public ApplicationCreatedEvent(
      UUID applicationId,
      String requestFingerprint,
      String status,
      int schemaVersion,
      Instant occurredAt,
      Erasable<List<PotentialDuplicate>> potentialDuplicates,
      String officeCode,
      String laaReference) {
    this(
        applicationId,
        requestFingerprint,
        status,
        schemaVersion,
        occurredAt,
        potentialDuplicates,
        officeCode,
        laaReference,
        List.of(),
        Erasable.erased(),
        Erasable.erased());
  }

  /** Creates the event without submitted content or proceedings. */
  public ApplicationCreatedEvent(
      UUID applicationId,
      String requestFingerprint,
      String status,
      int schemaVersion,
      Instant occurredAt,
      List<PotentialDuplicate> potentialDuplicates,
      String officeCode,
      String laaReference) {
    this(
        applicationId,
        requestFingerprint,
        status,
        schemaVersion,
        occurredAt,
        potentialDuplicates,
        officeCode,
        laaReference,
        List.of(),
        null,
        null);
  }

  @Override
  public Set<UUID> genesisIds() {
    return GenesisScoped.ids(applicationId);
  }
}
