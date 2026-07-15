package uk.gov.justice.laa.dstew.access.command.application;

import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreatedEvent;

import java.util.UUID;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.test.saga.SagaTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreateApplicationSagaTest {

  private SagaTestFixture<CreateApplicationSaga> fixture;

  @BeforeEach
  void setUp() {
    fixture = new SagaTestFixture<>(CreateApplicationSaga.class);
    fixture.registerCommandGateway(CommandGateway.class);
  }

  @Test
  void givenClaimEvent_whenHandled_thenStartsSagaAndDispatchesFinalisation() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent application =
        applicationCreatedEvent(applyApplicationId, applicationId);
    fixture.setCallbackBehavior((command, metadata) -> ApplicationFinalisationResult.CREATED);

    fixture
        .givenNoPriorActivity()
        .whenAggregate(applyApplicationId.toString())
        .publishes(
            new ApplyApplicationIdClaimedEvent(
                applyApplicationId, applicationId, application, null))
        .expectActiveSagas(1)
        .expectAssociationWith("applicationId", applicationId.toString())
        .expectDispatchedCommands(
            new FinaliseApplicationCreationCommand(applicationId, application, null));
  }

  @Test
  void givenStartedSaga_whenApplicationCreated_thenEndsSaga() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent application =
        applicationCreatedEvent(applyApplicationId, applicationId);

    fixture
        .givenAggregate(applyApplicationId.toString())
        .published(
            new ApplyApplicationIdClaimedEvent(
                applyApplicationId, applicationId, application, null))
        .whenAggregate(applicationId.toString())
        .publishes(application)
        .expectActiveSagas(0);
  }

  @Test
  void givenFinalisationFails_whenClaimHandled_thenReleasesClaimAndEndsSaga() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent application =
        applicationCreatedEvent(applyApplicationId, applicationId);
    FinaliseApplicationCreationCommand finalise =
        new FinaliseApplicationCreationCommand(applicationId, application, null);
    fixture.setCallbackBehavior(
        (command, metadata) -> {
          if (command instanceof FinaliseApplicationCreationCommand) {
            throw new IllegalStateException("Finalisation failed");
          }
          return null;
        });

    fixture
        .givenNoPriorActivity()
        .whenAggregate(applyApplicationId.toString())
        .publishes(
            new ApplyApplicationIdClaimedEvent(
                applyApplicationId, applicationId, application, null))
        .expectActiveSagas(0)
        .expectDispatchedCommands(
            finalise, new ReleaseApplyApplicationIdCommand(applyApplicationId, applicationId));
  }

  @Test
  void givenCompensationFails_whenClaimHandled_thenStillEndsSaga() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent application =
        applicationCreatedEvent(applyApplicationId, applicationId);
    FinaliseApplicationCreationCommand finalise =
        new FinaliseApplicationCreationCommand(applicationId, application, null);
    ReleaseApplyApplicationIdCommand release =
        new ReleaseApplyApplicationIdCommand(applyApplicationId, applicationId);
    fixture.setCallbackBehavior(
        (command, metadata) -> {
          throw new IllegalStateException("Command failed");
        });

    fixture
        .givenNoPriorActivity()
        .whenAggregate(applyApplicationId.toString())
        .publishes(
            new ApplyApplicationIdClaimedEvent(
                applyApplicationId, applicationId, application, null))
        .expectActiveSagas(0)
        .expectDispatchedCommands(finalise, release);
  }

  @Test
  void givenFinalisationWasAlreadyCommitted_whenClaimRedelivered_thenEndsWithoutCompensation() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent application =
        applicationCreatedEvent(applyApplicationId, applicationId);
    FinaliseApplicationCreationCommand finalise =
        new FinaliseApplicationCreationCommand(applicationId, application, null);
    fixture.setCallbackBehavior(
        (command, metadata) -> ApplicationFinalisationResult.ALREADY_CREATED);

    fixture
        .givenNoPriorActivity()
        .whenAggregate(applyApplicationId.toString())
        .publishes(
            new ApplyApplicationIdClaimedEvent(
                applyApplicationId, applicationId, application, null))
        .expectActiveSagas(0)
        .expectDispatchedCommands(finalise);
  }
}
