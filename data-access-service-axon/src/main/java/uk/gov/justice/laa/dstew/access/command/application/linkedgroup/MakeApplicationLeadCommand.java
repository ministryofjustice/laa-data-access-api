package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCaller;

/** Requests that an application become the lead of its linked group. */
public record MakeApplicationLeadCommand(
    UUID applicationId,
    ExpectedLinkedGroup expectedGroup,
    Instant occurredAt,
    @Nullable AuthenticatedCaller caller) {

  /** Validates the application and expected linked group. */
  public MakeApplicationLeadCommand {
    Objects.requireNonNull(applicationId, "applicationId must not be null");
    Objects.requireNonNull(expectedGroup, "expectedGroup must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }
}
