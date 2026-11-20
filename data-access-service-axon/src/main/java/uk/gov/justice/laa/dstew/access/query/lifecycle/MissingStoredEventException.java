package uk.gov.justice.laa.dstew.access.query.lifecycle;

import java.util.UUID;
import lombok.Getter;

/** Thrown when a lifecycle stream points to an event that is not in the event store. */
@Getter
public class MissingStoredEventException extends IllegalStateException {

  private final UUID genesisId;
  private final String eventIdentifier;

  /** Creates the exception for the genesis whose stream holds the dangling pointer. */
  public MissingStoredEventException(UUID genesisId, String eventIdentifier) {
    super("Lifecycle stream of " + genesisId + " points to missing event " + eventIdentifier);
    this.genesisId = genesisId;
    this.eventIdentifier = eventIdentifier;
  }
}
