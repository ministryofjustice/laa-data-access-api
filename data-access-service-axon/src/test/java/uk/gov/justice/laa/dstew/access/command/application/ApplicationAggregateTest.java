package uk.gov.justice.laa.dstew.access.command.application;

import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreatedEvent;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationFinalisationDetails;

import java.util.UUID;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApplicationAggregateTest {

  private AggregateTestFixture<ApplicationAggregate> fixture;

  @BeforeEach
  void setUp() {
    fixture = new AggregateTestFixture<>(ApplicationAggregate.class);
  }

  @Test
  void givenNoApplication_whenFinalised_thenCreatesApplication() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent application =
        applicationCreatedEvent(applyApplicationId, applicationId);
    ApplicationFinalisationDetails details = applicationFinalisationDetails(applyApplicationId);

    fixture
        .givenNoPriorActivity()
        .when(new FinaliseApplicationCreationCommand(applicationId, details, null))
        .expectResultMessagePayload(ApplicationFinalisationResult.CREATED)
        .expectEvents(application);
  }

  @Test
  void givenApplicationCreatedForClaim_whenFinalisationRedelivered_thenSucceedsIdempotently() {
    UUID applyApplicationId = UUID.randomUUID();
    ApplicationCreatedEvent application =
        applicationCreatedEvent(applyApplicationId, UUID.randomUUID());
    ApplicationFinalisationDetails details = applicationFinalisationDetails(applyApplicationId);

    fixture
        .given(application)
        .when(new FinaliseApplicationCreationCommand(application.applicationId(), details, null))
        .expectResultMessagePayload(ApplicationFinalisationResult.ALREADY_CREATED)
        .expectNoEvents();
  }

  @Test
  void givenApplicationCreatedForAnotherClaim_whenFinalisedThenRejectsConflict() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent existing = applicationCreatedEvent(UUID.randomUUID(), applicationId);
    ApplicationFinalisationDetails conflicting = applicationFinalisationDetails(UUID.randomUUID());

    fixture
        .given(existing)
        .when(new FinaliseApplicationCreationCommand(applicationId, conflicting, null))
        .expectException(IllegalStateException.class)
        .expectNoEvents();
  }

  @Test
  void givenLeadApplication_whenFinalised_thenCreatesAndLinksApplication() {
    UUID applyApplicationId = UUID.randomUUID();
    ApplicationCreatedEvent application =
        applicationCreatedEvent(applyApplicationId, UUID.randomUUID());
    ApplicationFinalisationDetails details = applicationFinalisationDetails(applyApplicationId);
    UUID leadApplicationId = UUID.randomUUID();

    fixture
        .givenNoPriorActivity()
        .when(
            new FinaliseApplicationCreationCommand(
                application.applicationId(), details, leadApplicationId))
        .expectResultMessagePayload(ApplicationFinalisationResult.CREATED)
        .expectEvents(
            application,
            new ApplicationLinkedEvent(
                application.applicationId(),
                leadApplicationId,
                application.serialisedRequest(),
                application.occurredAt()));
  }
}
