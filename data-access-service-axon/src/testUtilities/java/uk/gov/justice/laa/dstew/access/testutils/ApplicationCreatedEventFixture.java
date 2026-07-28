package uk.gov.justice.laa.dstew.access.testutils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationFinalisationDetails;

/** Builds compact Application events for aggregate and saga fixture tests. */
public final class ApplicationCreatedEventFixture {

  private ApplicationCreatedEventFixture() {}

  /** Creates a minimal event with stable values and the supplied identifiers. */
  public static ApplicationCreatedEvent applicationCreatedEvent(
      UUID applyApplicationId, UUID applicationId) {
    return applicationCreatedEvent(
        applicationId, applicationFinalisationDetails(applyApplicationId));
  }

  /** Creates an event from the supplied identifier and finalisation details. */
  public static ApplicationCreatedEvent applicationCreatedEvent(
      UUID applicationId, ApplicationFinalisationDetails details) {
    return new ApplicationCreatedEvent(
        applicationId,
        details.status(),
        details.laaReference(),
        details.applicationContent(),
        details.individuals(),
        details.schemaVersion(),
        details.applicationType(),
        details.applyApplicationId(),
        details.submittedAt(),
        details.officeCode(),
        details.usedDelegatedFunctions(),
        details.categoryOfLaw(),
        details.matterType(),
        details.proceedings(),
        details.serialisedRequest(),
        details.occurredAt());
  }

  /** Creates minimal finalisation details with stable values and the supplied Apply identifier. */
  public static ApplicationFinalisationDetails applicationFinalisationDetails(
      UUID applyApplicationId) {
    return new ApplicationFinalisationDetails(
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
