package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Requests that an application be removed from its linked group. */
public record UnlinkApplicationCommand(
    UUID applicationId, long expectedGroupVersion, Instant occurredAt) {

  /** Validates the application and expected linked-group version. */
  public UnlinkApplicationCommand {
    Objects.requireNonNull(applicationId, "applicationId must not be null");
    if (expectedGroupVersion < 0) {
      throw new IllegalArgumentException("expectedGroupVersion must not be negative");
    }
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }
}
