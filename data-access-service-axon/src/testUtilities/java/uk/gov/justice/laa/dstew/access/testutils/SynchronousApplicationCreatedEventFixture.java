package uk.gov.justice.laa.dstew.access.testutils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.command.synchronousapplication.SynchronousApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.synchronousapplication.SynchronousApplicationIndividual;
import uk.gov.justice.laa.dstew.access.command.synchronousapplication.SynchronousApplicationProceeding;

/** Builds compact SynchronousApplication events for aggregate and projection tests. */
public final class SynchronousApplicationCreatedEventFixture {

  private SynchronousApplicationCreatedEventFixture() {}

  /** Creates a minimal event with the supplied Apply Application identifier. */
  public static SynchronousApplicationCreatedEvent synchronousApplicationCreatedEvent(
      UUID applyApplicationId) {
    return new SynchronousApplicationCreatedEvent(
        applyApplicationId,
        "APPLICATION_SUBMITTED",
        "LAA-123",
        null,
        List.of(
            new SynchronousApplicationIndividual(
                UUID.randomUUID(), "Ada", "Lovelace", null, null, "CLIENT")),
        1,
        "APPLY",
        Instant.parse("2026-07-14T12:30:00Z"),
        "1A001B",
        false,
        null,
        null,
        List.of(
            new SynchronousApplicationProceeding(
                UUID.randomUUID(), UUID.randomUUID().toString(), "Care order", true, null)),
        "{}",
        Instant.parse("2026-07-15T08:00:00Z"));
  }
}
