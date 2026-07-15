package uk.gov.justice.laa.dstew.access.testutils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;

/** Builds compact Application events for aggregate and saga fixture tests. */
public final class ApplicationCreatedEventFixture {

  private ApplicationCreatedEventFixture() {}

  /** Creates a minimal event with stable values and the supplied identifiers. */
  public static ApplicationCreatedEvent applicationCreatedEvent(
      UUID applyApplicationId, UUID applicationId) {
    return new ApplicationCreatedEvent(
        applicationId,
        "APPLICATION_SUBMITTED",
        "LAA-123",
        null,
        List.of(),
        1,
        "APPLY",
        applyApplicationId,
        Instant.parse("2026-07-14T12:30:00Z"),
        "1A001B",
        false,
        null,
        null,
        List.of(),
        "{}",
        Instant.parse("2026-07-15T08:00:00Z"));
  }
}
