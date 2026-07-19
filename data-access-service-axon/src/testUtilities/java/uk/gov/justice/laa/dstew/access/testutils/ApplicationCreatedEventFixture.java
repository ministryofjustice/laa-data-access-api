package uk.gov.justice.laa.dstew.access.testutils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreationDetails;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;

/** Builds compact Application events for aggregate and factory fixture tests. */
public final class ApplicationCreatedEventFixture {

  private ApplicationCreatedEventFixture() {}

  /** Creates a minimal event where applicationId equals applyApplicationId. */
  public static ApplicationCreatedEvent applicationCreatedEvent(UUID applicationId) {
    return applicationCreatedEvent(applicationId, applicationCreationDetails(applicationId));
  }

  /** Creates a minimal event with stable values and the supplied identifiers. */
  public static ApplicationCreatedEvent applicationCreatedEvent(
      UUID applyApplicationId, UUID applicationId) {
    return applicationCreatedEvent(applicationId, applicationCreationDetails(applyApplicationId));
  }

  /** Creates an event from the supplied identifier and creation details. */
  public static ApplicationCreatedEvent applicationCreatedEvent(
      UUID applicationId, ApplicationCreationDetails details) {
    return new ApplicationCreatedEvent(
        applicationId,
        0L,
        ApplicationDataStore.fingerprint(details.serialisedRequest()),
        details.status(),
        details.schemaVersion(),
        details.applicationType(),
        details.applyApplicationId(),
        details.occurredAt(),
        details.leadApplicationId(),
        List.of());
  }

  /** Creates minimal creation details with stable values and the supplied Apply identifier. */
  public static ApplicationCreationDetails applicationCreationDetails(UUID applyApplicationId) {
    return new ApplicationCreationDetails(
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
        Instant.parse("2026-07-15T08:00:00Z"),
        null);
  }
}
