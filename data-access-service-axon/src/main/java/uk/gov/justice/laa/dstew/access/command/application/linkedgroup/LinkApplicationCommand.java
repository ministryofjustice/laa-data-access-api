package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Command for explicitly linking one application to another. */
public record LinkApplicationCommand(
    UUID sourceApplicationId,
    UUID targetApplicationId,
    LinkType linkType,
    @Nullable ExpectedLinkedGroup expectedTargetGroup,
    Instant occurredAt) {

  /** Enforces the command invariants owned below the controller seam. */
  public LinkApplicationCommand {
    Objects.requireNonNull(sourceApplicationId, "sourceApplicationId must not be null");
    Objects.requireNonNull(targetApplicationId, "targetApplicationId must not be null");
    Objects.requireNonNull(occurredAt, "occurredAt must not be null");
  }
}
