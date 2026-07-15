package uk.gov.justice.laa.dstew.access.command.application;

import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationFinalisationDetails;

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
    ApplicationFinalisationDetails details = applicationFinalisationDetails(applyApplicationId);

    fixture
        .givenNoPriorActivity()
        .whenConstructing(() -> new ApplyApplicationIdAggregate(applicationId, details, null))
        .expectEvents(
            new ApplyApplicationIdClaimedEvent(applyApplicationId, applicationId, details, null));
  }

  @Test
  void givenClaimedApplyApplicationId_whenClaimedAgain_thenRejectsDuplicate() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    ApplicationFinalisationDetails claimedDetails =
        applicationFinalisationDetails(applyApplicationId);

    fixture
        .given(
            new ApplyApplicationIdClaimedEvent(
                applyApplicationId, applicationId, claimedDetails, null))
        .whenInvoking(
            applyApplicationId.toString(),
            aggregate ->
                aggregate.claim(
                    UUID.randomUUID(), applicationFinalisationDetails(applyApplicationId), null))
        .expectException(ValidationException.class)
        .expectNoEvents();
  }

  @Test
  void givenReleasedApplyApplicationId_whenClaimedAgain_thenRecordsNewOwner() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID originalApplicationId = UUID.randomUUID();
    UUID retriedApplicationId = UUID.randomUUID();
    UUID leadApplicationId = UUID.randomUUID();
    ApplicationFinalisationDetails originalDetails =
        applicationFinalisationDetails(applyApplicationId);
    ApplicationFinalisationDetails retriedDetails =
        applicationFinalisationDetails(applyApplicationId);

    fixture
        .given(
            new ApplyApplicationIdClaimedEvent(
                applyApplicationId, originalApplicationId, originalDetails, null),
            new ApplyApplicationIdReleasedEvent(applyApplicationId, originalApplicationId))
        .whenInvoking(
            applyApplicationId.toString(),
            aggregate -> aggregate.claim(retriedApplicationId, retriedDetails, leadApplicationId))
        .expectEvents(
            new ApplyApplicationIdClaimedEvent(
                applyApplicationId, retriedApplicationId, retriedDetails, leadApplicationId));
  }

  @Test
  void givenClaimedApplyApplicationId_whenReleasedByItsOwner_thenRecordsRelease() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    ApplicationFinalisationDetails details = applicationFinalisationDetails(applyApplicationId);

    fixture
        .given(new ApplyApplicationIdClaimedEvent(applyApplicationId, applicationId, details, null))
        .when(new ReleaseApplyApplicationIdCommand(applyApplicationId, applicationId))
        .expectEvents(new ApplyApplicationIdReleasedEvent(applyApplicationId, applicationId));
  }
}
