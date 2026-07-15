package uk.gov.justice.laa.dstew.access.command.application;

import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreatedEvent;

import java.util.UUID;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

class ApplyApplicationIdAggregateTest {

  private AggregateTestFixture<ApplyApplicationIdAggregate> fixture;

  @BeforeEach
  void setUp() {
    fixture = new AggregateTestFixture<>(ApplyApplicationIdAggregate.class);
  }

  @Test
  void givenUnclaimedApplyApplicationId_whenClaimed_thenRecordsApplicationId() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent application =
        applicationCreatedEvent(applyApplicationId, applicationId);

    fixture
        .givenNoPriorActivity()
        .whenConstructing(() -> new ApplyApplicationIdAggregate(application, null))
        .expectEvents(
            new ApplyApplicationIdClaimedEvent(
                applyApplicationId, applicationId, application, null));
  }

  @Test
  void givenClaimedApplyApplicationId_whenClaimedAgain_thenRejectsDuplicate() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent claimedApplication =
        applicationCreatedEvent(applyApplicationId, applicationId);

    fixture
        .given(
            new ApplyApplicationIdClaimedEvent(
                applyApplicationId, applicationId, claimedApplication, null))
        .whenInvoking(
            applyApplicationId.toString(),
            aggregate ->
                aggregate.claim(
                    applicationCreatedEvent(applyApplicationId, UUID.randomUUID()), null))
        .expectException(ValidationException.class)
        .expectNoEvents();
  }

  @Test
  void givenReleasedApplyApplicationId_whenClaimedAgain_thenRecordsNewOwner() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID originalApplicationId = UUID.randomUUID();
    UUID retriedApplicationId = UUID.randomUUID();
    UUID leadApplicationId = UUID.randomUUID();
    ApplicationCreatedEvent originalApplication =
        applicationCreatedEvent(applyApplicationId, originalApplicationId);
    ApplicationCreatedEvent retriedApplication =
        applicationCreatedEvent(applyApplicationId, retriedApplicationId);

    fixture
        .given(
            new ApplyApplicationIdClaimedEvent(
                applyApplicationId, originalApplicationId, originalApplication, null),
            new ApplyApplicationIdReleasedEvent(applyApplicationId, originalApplicationId))
        .whenInvoking(
            applyApplicationId.toString(),
            aggregate -> aggregate.claim(retriedApplication, leadApplicationId))
        .expectEvents(
            new ApplyApplicationIdClaimedEvent(
                applyApplicationId, retriedApplicationId, retriedApplication, leadApplicationId));
  }

  @Test
  void givenClaimedApplyApplicationId_whenReleasedByItsOwner_thenRecordsRelease() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent application =
        applicationCreatedEvent(applyApplicationId, applicationId);

    fixture
        .given(
            new ApplyApplicationIdClaimedEvent(
                applyApplicationId, applicationId, application, null))
        .when(new ReleaseApplyApplicationIdCommand(applyApplicationId, applicationId))
        .expectEvents(new ApplyApplicationIdReleasedEvent(applyApplicationId, applicationId));
  }
}
