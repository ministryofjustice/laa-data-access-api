package uk.gov.justice.laa.dstew.access.testutils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationProvider;
import uk.gov.justice.laa.dstew.access.applicationcontent.Proceeding;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreationDetails;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;

/** Builds compact Application events for aggregate and factory fixture tests. */
public final class ApplicationCreatedEventFixture {

  private ApplicationCreatedEventFixture() {}

  /** Creates a minimal event with stable values for the supplied identifier. */
  public static ApplicationCreatedEvent applicationCreatedEvent(UUID applicationId) {
    return applicationCreatedEvent(applicationId, applicationCreationDetails(applicationId));
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
        details.occurredAt(),
        details.potentialDuplicates());
  }

  /** Creates minimal creation details with stable values for the supplied identifier. */
  public static ApplicationCreationDetails applicationCreationDetails(UUID applicationId) {
    return new ApplicationCreationDetails(
        "APPLICATION_SUBMITTED",
        "LAA-123",
        null,
        ApplicationProvider.builder().officeCode("1A001B").build(),
        List.of(),
        1,
        Instant.parse("2026-07-14T12:30:00Z"),
        false,
        "Family",
        "SPECIAL_CHILDREN_ACT",
        "MAT",
        "KPBLW",
        List.of(
            Proceeding.builder()
                .id(UUID.nameUUIDFromBytes(("proceeding-" + applicationId).getBytes()))
                .leadProceeding(true)
                .code("SE003")
                .description("Care order")
                .categoryOfLaw("Family")
                .categoryOfLawCode("MAT")
                .matterType("SPECIAL_CHILDREN_ACT")
                .matterTypeCode("KPBLW")
                .build()),
        "{}",
        Instant.parse("2026-07-15T08:00:00Z"),
        List.of());
  }
}
