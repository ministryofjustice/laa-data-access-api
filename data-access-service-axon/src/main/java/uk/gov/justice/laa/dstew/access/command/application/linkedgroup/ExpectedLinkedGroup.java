package uk.gov.justice.laa.dstew.access.command.application.linkedgroup;

import java.util.Objects;
import java.util.UUID;

/** The linked group, and its version, that a caller's command was based on. */
public record ExpectedLinkedGroup(UUID groupId, long version) {

  /** Validates the expected group identity and version. */
  public ExpectedLinkedGroup {
    Objects.requireNonNull(groupId, "groupId must not be null");
    if (version < 0) {
      throw new IllegalArgumentException("version must not be negative");
    }
  }
}
