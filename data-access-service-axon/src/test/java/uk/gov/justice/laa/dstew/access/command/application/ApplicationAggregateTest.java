package uk.gov.justice.laa.dstew.access.command.application;

import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreatedEvent;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.CreateLinkedApplicationGroupCommand;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkedApplicationGroupRequested;
import uk.gov.justice.laa.dstew.access.exception.ApplicationCreationConflictException;
import uk.gov.justice.laa.dstew.access.exception.ApplicationGroupInvariantException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;

class ApplicationAggregateTest {

  private AggregateTestFixture<ApplicationAggregate> fixture;
  private ApplicationCreationDetailsFactory factory;

  @BeforeEach
  void setUp() {
    fixture = new AggregateTestFixture<>(ApplicationAggregate.class);
    factory =
        new ApplicationCreationDetailsFactory(null, null) {
          @Override
          public ApplicationCreationDetails prepare(CreateApplicationCommand command) {
            return applicationCreationDetails(command.applicationId());
          }
        };
    fixture.registerInjectableResource(factory);
  }

  @Test
  void givenNoApplication_whenCreated_thenCreatesApplication() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreationDetails details = applicationCreationDetails(applicationId);
    ApplicationCreatedEvent expected = applicationCreatedEvent(applicationId, details);

    fixture
        .givenNoPriorActivity()
        .when(createCommand(applicationId, "{}"))
        .expectResultMessagePayload(applicationId)
        .expectEvents(expected);
  }

  @Test
  void givenLeadApplication_whenCreated_thenCreatesApplicationWithLeadId() {
    UUID applicationId = UUID.randomUUID();
    UUID leadApplicationId = UUID.randomUUID();
    ApplicationCreationDetails detailsWithLead =
        new ApplicationCreationDetails(
            "APPLICATION_SUBMITTED",
            "LAA-123",
            null,
            List.of(),
            1,
            "APPLY",
            applicationId,
            java.time.Instant.parse("2026-07-14T12:30:00Z"),
            "1A001B",
            false,
            null,
            null,
            List.of(),
            "{}",
            java.time.Instant.parse("2026-07-15T08:00:00Z"),
            leadApplicationId);

    ApplicationCreationDetailsFactory factoryWithLead =
        new ApplicationCreationDetailsFactory(null, null) {
          @Override
          public ApplicationCreationDetails prepare(CreateApplicationCommand command) {
            return detailsWithLead;
          }
        };
    fixture = new AggregateTestFixture<>(ApplicationAggregate.class);
    fixture.registerInjectableResource(factoryWithLead);

    ApplicationCreatedEvent createdEvent = applicationCreatedEvent(applicationId, detailsWithLead);

    // ApplicationLinkedEvent is no longer emitted; linking is initiated by
    // ApplicationGroupEventRouter after the projection picks up ApplicationCreatedEvent.
    fixture
        .givenNoPriorActivity()
        .when(createCommand(applicationId, "{}"))
        .expectResultMessagePayload(applicationId)
        .expectEvents(createdEvent);
  }

  @Test
  void givenExistingApplication_whenIdenticalRetry_thenSucceedsIdempotently() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent existing = applicationCreatedEvent(applicationId);

    fixture
        .given(existing)
        .when(createCommand(applicationId, "{}"))
        .expectResultMessagePayload(applicationId)
        .expectNoEvents();
  }

  @Test
  void givenExistingApplication_whenDifferentSerialisedRequest_thenRejectsWithConflict() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent existing = applicationCreatedEvent(applicationId);

    fixture
        .given(existing)
        .when(createCommand(applicationId, "{\"different\":true}"))
        .expectException(ApplicationCreationConflictException.class)
        .expectNoEvents();
  }

  @Test
  void givenExistingApplication_whenDifferentSchemaVersion_thenRejectsWithConflict() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent existing = applicationCreatedEvent(applicationId);

    fixture
        .given(existing)
        .when(createCommandWithSchema(applicationId, "{}", 2))
        .expectException(ApplicationCreationConflictException.class)
        .expectNoEvents();
  }

  @Test
  void givenExistingLeadApplication_whenCreateLinkedApplicationGroupCommand_thenEmitsRequested() {
    UUID leadApplicationId = UUID.randomUUID();
    ApplicationCreatedEvent leadCreated = applicationCreatedEvent(leadApplicationId);
    List<UUID> members = List.of(leadApplicationId, UUID.randomUUID());
    java.time.Instant occurredAt = java.time.Instant.parse("2026-07-15T08:00:00Z");
    UUID expectedGroupId =
        UUID.nameUUIDFromBytes(
            ("linked-group:" + leadApplicationId).getBytes(StandardCharsets.UTF_8));

    fixture
        .given(leadCreated)
        .when(
            new CreateLinkedApplicationGroupCommand(
                leadApplicationId, members.get(1), members, "{}", occurredAt))
        .expectEvents(
            new LinkedApplicationGroupRequested(
                expectedGroupId, leadApplicationId, members, "{}", occurredAt));
  }

  @Test
  void givenMissingLeadApplication_whenCreateLinkedApplicationGroupCommand_thenThrowsNotFound() {
    UUID missingLeadId = UUID.randomUUID();
    List<UUID> members = List.of(missingLeadId, UUID.randomUUID());

    fixture
        .givenNoPriorActivity()
        .when(
            new CreateLinkedApplicationGroupCommand(
                missingLeadId,
                members.get(1),
                members,
                "{}",
                java.time.Instant.parse("2026-07-15T08:00:00Z")))
        .expectException(ResourceNotFoundException.class)
        .expectNoEvents();
  }

  @Test
  void givenSelfReferentialLead_whenCreateApplication_thenRejectsWithIllegalArgument() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreationDetailsFactory selfLeadFactory =
        new ApplicationCreationDetailsFactory(null, null) {
          @Override
          public ApplicationCreationDetails prepare(CreateApplicationCommand command) {
            return new ApplicationCreationDetails(
                "APPLICATION_SUBMITTED",
                "LAA-123",
                null,
                List.of(),
                1,
                "APPLY",
                applicationId,
                java.time.Instant.parse("2026-07-14T12:30:00Z"),
                "1A001B",
                false,
                null,
                null,
                List.of(),
                "{}",
                java.time.Instant.parse("2026-07-15T08:00:00Z"),
                applicationId); // leadApplicationId == self
          }
        };
    fixture = new AggregateTestFixture<>(ApplicationAggregate.class);
    fixture.registerInjectableResource(selfLeadFactory);

    fixture
        .givenNoPriorActivity()
        .when(createCommand(applicationId, "{}"))
        .expectException(ApplicationGroupInvariantException.class)
        .expectNoEvents();
  }

  @Test
  void
      givenAssociatedApplication_whenCreateLinkedApplicationGroupCommand_thenRejectsWithIllegalState() {
    UUID applicationId = UUID.randomUUID();
    UUID otherLeadId = UUID.randomUUID();
    // Build an event representing this app having been created as an associated member
    ApplicationCreationDetails associatedDetails =
        new ApplicationCreationDetails(
            "APPLICATION_SUBMITTED",
            "LAA-123",
            null,
            List.of(),
            1,
            "APPLY",
            applicationId,
            java.time.Instant.parse("2026-07-14T12:30:00Z"),
            "1A001B",
            false,
            null,
            null,
            List.of(),
            "{}",
            java.time.Instant.parse("2026-07-15T08:00:00Z"),
            otherLeadId); // already a member of another group
    ApplicationCreatedEvent associatedCreated =
        applicationCreatedEvent(applicationId, associatedDetails);

    fixture
        .given(associatedCreated)
        .when(
            new CreateLinkedApplicationGroupCommand(
                applicationId,
                UUID.randomUUID(),
                List.of(applicationId, UUID.randomUUID()),
                "{}",
                java.time.Instant.parse("2026-07-15T08:00:00Z")))
        .expectException(ApplicationGroupInvariantException.class)
        .expectNoEvents();
  }

  private CreateApplicationCommand createCommand(UUID applicationId, String serialisedRequest) {
    return createCommandWithSchema(applicationId, serialisedRequest, 1);
  }

  private CreateApplicationCommand createCommandWithSchema(
      UUID applicationId, String serialisedRequest, int schemaVersion) {
    return new CreateApplicationCommand(
        applicationId,
        "APPLICATION_SUBMITTED",
        "LAA-123",
        Map.of("id", applicationId.toString()),
        List.of(),
        serialisedRequest,
        schemaVersion,
        "ApplyApplication.json",
        "APPLY");
  }
}
