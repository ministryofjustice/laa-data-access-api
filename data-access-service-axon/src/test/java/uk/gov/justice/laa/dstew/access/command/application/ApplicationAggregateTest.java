package uk.gov.justice.laa.dstew.access.command.application;

import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreatedEvent;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.exception.ApplicationCreationConflictException;

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
  void givenLeadApplication_whenCreated_thenCreatesAndLinksApplication() {
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

    fixture
        .givenNoPriorActivity()
        .when(createCommand(applicationId, "{}"))
        .expectResultMessagePayload(applicationId)
        .expectEvents(
            createdEvent,
            new ApplicationLinkedEvent(
                applicationId,
                leadApplicationId,
                createdEvent.serialisedRequest(),
                createdEvent.occurredAt()));
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
