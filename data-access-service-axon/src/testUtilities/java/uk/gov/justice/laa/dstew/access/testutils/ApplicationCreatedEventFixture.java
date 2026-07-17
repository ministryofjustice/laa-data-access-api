package uk.gov.justice.laa.dstew.access.testutils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationIndividual;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationProceeding;

/** Builds compact Application events for aggregate and projection tests. */
public final class ApplicationCreatedEventFixture {

  private ApplicationCreatedEventFixture() {}

  /** Creates a minimal event with the supplied Apply Application identifier. */
  public static ApplicationCreatedEvent applicationCreatedEvent(UUID applyApplicationId) {
    return new ApplicationCreatedEvent(
        applyApplicationId,
        "APPLICATION_SUBMITTED",
        "LAA-123",
        null,
        List.of(
            new ApplicationIndividual(UUID.randomUUID(), "Ada", "Lovelace", null, null, "CLIENT")),
        1,
        "APPLY",
        Instant.parse("2026-07-14T12:30:00Z"),
        "1A001B",
        false,
        null,
        null,
        List.of(
            new ApplicationProceeding(
                UUID.randomUUID(), UUID.randomUUID().toString(), "Care order", true, null)),
        "{}",
        Instant.parse("2026-07-15T08:00:00Z"));
  }
}
