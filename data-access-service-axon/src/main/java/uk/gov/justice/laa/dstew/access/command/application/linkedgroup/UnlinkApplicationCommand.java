package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCaller;
import uk.gov.justice.laa.dstew.access.security.CallerCommand;

/** Requests that an application be removed from its linked group. */
public record UnlinkApplicationCommand(
    UUID applicationId,
    ExpectedLinkedGroup expectedGroup,
    Instant occurredAt,
    @Nullable AuthenticatedCaller caller)
    implements CallerCommand {

  /** Validates the application and expected linked group. */
  public UnlinkApplicationCommand {
    Objects.requireNonNull(applicationId, "applicationId must not be null");
    Objects.requireNonNull(expectedGroup, "expectedGroup must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }
}
