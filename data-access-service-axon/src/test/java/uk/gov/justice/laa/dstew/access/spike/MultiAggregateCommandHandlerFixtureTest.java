package uk.gov.justice.laa.dstew.access.spike;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.eventsourcing.configuration.EventSourcedEntityModule;
import org.axonframework.eventsourcing.configuration.EventSourcingConfigurer;
import org.axonframework.messaging.commandhandling.configuration.CommandHandlingModule;
import org.axonframework.test.fixture.AxonTestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationAggregate;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationCreatedEvent;
import uk.gov.justice.laa.dstew.access.command.application.ready.ApplicationReadyForManualAssessmentEvent;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemAssigned;
import uk.gov.justice.laa.dstew.access.command.worklist.WorkItemType;

class MultiAggregateCommandHandlerFixtureTest {

  private AxonTestFixture fixture;

  @BeforeEach
  void setUp() {
    fixture =
        AxonTestFixture.with(
            EventSourcingConfigurer.create()
                .registerEntity(
                    EventSourcedEntityModule.autodetected(UUID.class, ApplicationAggregate.class))
                .registerCommandHandlingModule(
                    CommandHandlingModule.named("multi-aggregate-spike")
                        .commandHandlers(
                            handlers ->
                                handlers.autodetectedCommandHandlingComponent(
                                    configuration ->
                                        new AppendToTwoApplicationsCommandHandler()))));
  }

  @AfterEach
  void tearDown() {
    fixture.stop();
  }

  @Test
  void givenTwoReadyApplications_whenCommandHandled_thenBothAreAssigned() {
    var firstApplicationId = UUID.randomUUID();
    var secondApplicationId = UUID.randomUUID();
    var caseworkerId = UUID.randomUUID();
    var occurredAt = Instant.parse("2026-10-27T15:00:00Z");

    fixture
        .given()
        .events(
            created(firstApplicationId, occurredAt),
            ready(firstApplicationId, occurredAt),
            created(secondApplicationId, occurredAt),
            ready(secondApplicationId, occurredAt))
        .when()
        .command(
            new AppendToTwoApplicationsCommand(
                firstApplicationId, secondApplicationId, caseworkerId, occurredAt))
        .then()
        .events(
            new WorkItemAssigned(
                firstApplicationId, WorkItemType.APPLICATION, 1L, 1L, caseworkerId, occurredAt),
            new WorkItemAssigned(
                secondApplicationId, WorkItemType.APPLICATION, 1L, 1L, caseworkerId, occurredAt));
  }

  private ApplicationCreatedEvent created(UUID applicationId, Instant occurredAt) {
    return new ApplicationCreatedEvent(
        applicationId, 0L, "hash", "APPLICATION_SUBMITTED", 1, occurredAt, null, null);
  }

  private ApplicationReadyForManualAssessmentEvent ready(UUID applicationId, Instant occurredAt) {
    return new ApplicationReadyForManualAssessmentEvent(applicationId, 1L, 1L, occurredAt);
  }
}
