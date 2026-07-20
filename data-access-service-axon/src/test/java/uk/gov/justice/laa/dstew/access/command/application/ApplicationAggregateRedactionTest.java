package uk.gov.justice.laa.dstew.access.command.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreatedEvent;
import static uk.gov.justice.laa.dstew.access.testutils.ApplicationCreatedEventFixture.applicationCreationDetails;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataPayload;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationDataStore;
import uk.gov.justice.laa.dstew.access.command.application.data.ApplicationPiiRepository;
import uk.gov.justice.laa.dstew.access.exception.ApplicationVersionConflictException;

class ApplicationAggregateRedactionTest {

  private AggregateTestFixture<ApplicationAggregate> fixture;
  private ApplicationDataStore applicationDataStore;
  private ApplicationPiiRepository piiRepository;

  @BeforeEach
  void setUp() {
    fixture = new AggregateTestFixture<>(ApplicationAggregate.class);
    applicationDataStore = mock(ApplicationDataStore.class);
    piiRepository = mock(ApplicationPiiRepository.class);
    fixture.registerInjectableResource(
        new ApplicationCreationDetailsFactory(null, null) {
          @Override
          public ApplicationCreationDetails prepare(CreateApplicationCommand command) {
            return applicationCreationDetails(command.applicationId());
          }
        });
    fixture.registerInjectableResource(applicationDataStore);
    fixture.registerInjectableResource(piiRepository);
    when(applicationDataStore.append(any(), anyLong(), any(ApplicationDataPayload.class), any(), any()))
        .thenReturn("hash");
  }

  @Test
  void givenCreatedEvent_whenDecisionMade_thenDecisionEventEmitted() {
    UUID applicationId = UUID.randomUUID();
    UUID proceedingId = UUID.randomUUID();
    ApplicationCreationDetails details = detailsWithProceeding(applicationId, proceedingId);
    when(applicationDataStore.get(applicationId, 0L))
        .thenReturn(ApplicationDataPayload.from(details));

    fixture
        .given(applicationCreatedEvent(applicationId, details))
        .when(
            new uk.gov.justice.laa.dstew.access.command.application.decision
                .MakeApplicationDecisionCommand(
                applicationId,
                0L,
                "REFUSED",
                false,
                List.of(
                    new uk.gov.justice.laa.dstew.access.command.application.decision
                        .MakeDecisionProceeding(
                        proceedingId, "REFUSED", "reason", "justification")),
                null,
                "{}",
                null,
                Instant.parse("2026-07-20T10:00:00Z")))
        .expectEvents(
            new uk.gov.justice.laa.dstew.access.command.application.decision
                .ApplicationDecisionMadeEvent(
                applicationId,
                1L,
                1L,
                "REFUSED",
                false,
                Instant.parse("2026-07-20T10:00:00Z")));
  }

  @Test
  void givenPresentApplication_whenRedacted_thenDeletesPiiAfterCommit() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-20T11:00:00Z");

    fixture
        .given(applicationCreatedEvent(applicationId, applicationCreationDetails(applicationId)))
        .when(new RedactApplicationPiiCommand(applicationId, 0L, "gdpr", "tester", occurredAt))
        .expectEvents(
            new ApplicationPiiRedactedEvent(applicationId, 1L, "gdpr", "tester", occurredAt));

    verify(piiRepository).redactAllForApplication(applicationId, "gdpr", "tester");
  }

  @Test
  void givenAlreadyRedactedApplication_whenRedactedAgain_thenNoEventEmitted() {
    UUID applicationId = UUID.randomUUID();
    Instant occurredAt = Instant.parse("2026-07-20T11:00:00Z");

    fixture
        .given(
            applicationCreatedEvent(applicationId, applicationCreationDetails(applicationId)),
            new ApplicationPiiRedactedEvent(applicationId, 1L, "gdpr", "tester", occurredAt))
        .when(new RedactApplicationPiiCommand(applicationId, 1L, "gdpr", "tester", occurredAt))
        .expectNoEvents();
  }

  @Test
  void givenWrongExpectedVersion_whenRedactionAttempted_thenVersionConflictThrown() {
    UUID applicationId = UUID.randomUUID();

    fixture
        .given(applicationCreatedEvent(applicationId, applicationCreationDetails(applicationId)))
        .when(
            new RedactApplicationPiiCommand(
                applicationId, 1L, "gdpr", "tester", Instant.parse("2026-07-20T11:00:00Z")))
        .expectException(ApplicationVersionConflictException.class)
        .expectNoEvents();
  }

  private ApplicationCreationDetails detailsWithProceeding(UUID applicationId, UUID proceedingId) {
    ApplicationCreationDetails original = applicationCreationDetails(applicationId);
    return new ApplicationCreationDetails(
        original.status(),
        original.laaReference(),
        original.applicationContent(),
        original.individuals(),
        original.schemaVersion(),
        original.applicationType(),
        original.applyApplicationId(),
        original.submittedAt(),
        original.officeCode(),
        original.usedDelegatedFunctions(),
        original.categoryOfLaw(),
        original.matterType(),
        List.of(new ApplicationProceeding(proceedingId, proceedingId, "Proceeding", true, null)),
        original.serialisedRequest(),
        original.occurredAt(),
        original.leadApplicationId());
  }
}
