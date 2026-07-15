package uk.gov.justice.laa.dstew.access.command.application;

import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreatedEvent;

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
    ApplicationCreatedEvent application =
        applicationCreatedEvent(UUID.randomUUID(), UUID.randomUUID());

    fixture
        .givenNoPriorActivity()
        .when(new FinaliseApplicationCreationCommand(application.applicationId(), application))
        .expectResultMessagePayload(ApplicationFinalisationResult.CREATED)
        .expectEvents(application);
  }

  @Test
  void givenApplicationCreatedForClaim_whenFinalisationRedelivered_thenSucceedsIdempotently() {
    ApplicationCreatedEvent application =
        applicationCreatedEvent(UUID.randomUUID(), UUID.randomUUID());

    fixture
        .given(application)
        .when(new FinaliseApplicationCreationCommand(application.applicationId(), application))
        .expectResultMessagePayload(ApplicationFinalisationResult.ALREADY_CREATED)
        .expectNoEvents();
  }

  @Test
  void givenApplicationCreatedForAnotherClaim_whenFinalisedThenRejectsConflict() {
    UUID applicationId = UUID.randomUUID();
    ApplicationCreatedEvent existing = applicationCreatedEvent(UUID.randomUUID(), applicationId);
    ApplicationCreatedEvent conflicting = applicationCreatedEvent(UUID.randomUUID(), applicationId);

    fixture
        .given(existing)
        .when(new FinaliseApplicationCreationCommand(applicationId, conflicting))
        .expectException(IllegalStateException.class)
        .expectNoEvents();
  }
}
