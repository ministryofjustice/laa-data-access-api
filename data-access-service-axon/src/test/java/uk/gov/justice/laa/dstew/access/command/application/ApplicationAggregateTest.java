package uk.gov.justice.laa.dstew.access.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApplicationAggregateTest {

  private AggregateTestFixture<ApplicationAggregate> fixture;
  private Clock clock;

  @BeforeEach
  void setUp() {
    fixture = new AggregateTestFixture<>(ApplicationAggregate.class);
    clock = Clock.fixed(Instant.parse("2026-07-15T08:00:00Z"), ZoneOffset.UTC);
    fixture.registerInjectableResource(clock);
  }

  // ---- Submit ----------------------------------------------------------------------------------

  @Test
  void givenDraftedApplication_whenSubmitted_thenTransitionsWithApplicationSubmittedEvent() {
    UUID applyApplicationId = UUID.randomUUID();
    SubmitApplicationCommand command = submitCommand(applyApplicationId);

    fixture
        .given(draftedEvent(applyApplicationId))
        .when(command)
        .expectResultMessagePayload(applyApplicationId)
        .expectEventsMatching(
            new org.hamcrest.BaseMatcher<>() {
              @Override
              public boolean matches(Object o) {
                var events = (java.util.List<?>) o;
                assertThat(events).hasSize(1);
                ApplicationSubmittedEvent event =
                    (ApplicationSubmittedEvent)
                        ((org.axonframework.messaging.Message<?>) events.getFirst()).getPayload();
                assertThat(event.applyApplicationId()).isEqualTo(applyApplicationId);
                assertThat(event.contentId()).isEqualTo(command.contentId());
                assertThat(event.status()).isEqualTo("APPLICATION_SUBMITTED");
                assertThat(event.laaReference()).isEqualTo("LAA-123");
                return true;
              }

              @Override
              public void describeTo(org.hamcrest.Description description) {
                description.appendText("ApplicationSubmittedEvent transitioning from a draft");
              }
            });
  }

  @Test
  void givenNoApplication_whenSubmitted_thenRejectsWithAggregateNotFound() {
    UUID applyApplicationId = UUID.randomUUID();

    fixture
        .givenNoPriorActivity()
        .when(submitCommand(applyApplicationId))
        .expectException(org.axonframework.modelling.command.AggregateNotFoundException.class);
  }

  @Test
  void givenSubmittedApplication_whenSubmittedAgain_thenIdempotentWithNoNewEvent() {
    UUID applyApplicationId = UUID.randomUUID();

    fixture
        .given(draftedEvent(applyApplicationId), submittedEvent(applyApplicationId))
        .when(submitCommand(applyApplicationId))
        .expectResultMessagePayload(applyApplicationId)
        .expectNoEvents();
  }

  // ---- Draft (PUT create-or-overwrite) ---------------------------------------------------------

  @Test
  void givenNoActivity_whenPutDraft_thenPublishesApplicationDraftedEvent() {
    UUID applicationId = UUID.randomUUID();

    fixture
        .givenNoPriorActivity()
        .when(new PutDraftApplicationCommand(applicationId))
        .expectEventsMatching(
            new org.hamcrest.BaseMatcher<>() {
              @Override
              public boolean matches(Object o) {
                var events = (java.util.List<?>) o;
                assertThat(events).hasSize(1);
                ApplicationDraftedEvent event =
                    (ApplicationDraftedEvent)
                        ((org.axonframework.messaging.Message<?>) events.getFirst()).getPayload();
                assertThat(event.applyApplicationId()).isEqualTo(applicationId);
                return true;
              }

              @Override
              public void describeTo(org.hamcrest.Description description) {
                description.appendText("ApplicationDraftedEvent (genesis) with expected fields");
              }
            });
  }

  @Test
  void givenExistingDraft_whenPutDraft_thenPublishesApplicationDraftUpdatedEvent() {
    UUID applicationId = UUID.randomUUID();

    fixture
        .given(draftedEvent(applicationId))
        .when(new PutDraftApplicationCommand(applicationId))
        .expectEventsMatching(
            new org.hamcrest.BaseMatcher<>() {
              @Override
              public boolean matches(Object o) {
                var events = (java.util.List<?>) o;
                assertThat(events).hasSize(1);
                ApplicationDraftUpdatedEvent event =
                    (ApplicationDraftUpdatedEvent)
                        ((org.axonframework.messaging.Message<?>) events.getFirst()).getPayload();
                assertThat(event.applyApplicationId()).isEqualTo(applicationId);
                return true;
              }

              @Override
              public void describeTo(org.hamcrest.Description description) {
                description.appendText("ApplicationDraftUpdatedEvent with expected fields");
              }
            });
  }

  @Test
  void givenSubmittedApplication_whenPutDraft_thenRejectsWithConflict() {
    UUID applicationId = UUID.randomUUID();

    fixture
        .given(draftedEvent(applicationId), submittedEvent(applicationId))
        .when(new PutDraftApplicationCommand(applicationId))
        .expectException(uk.gov.justice.laa.dstew.access.exception.ConflictException.class);
  }

  // ---- Prior authority (post-submission member) ------------------------------------------------

  @Test
  void
      givenSubmittedApplication_whenCreatePriorAuthority_thenPublishesPriorAuthorityDraftedEvent() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID priorAuthorityId = UUID.randomUUID();

    fixture
        .given(draftedEvent(applyApplicationId), submittedEvent(applyApplicationId))
        .when(new CreatePriorAuthorityDraftCommand(applyApplicationId, priorAuthorityId))
        .expectEventsMatching(
            new org.hamcrest.BaseMatcher<>() {
              @Override
              public boolean matches(Object o) {
                var events = (java.util.List<?>) o;
                assertThat(events).hasSize(1);
                PriorAuthorityDraftedEvent event =
                    (PriorAuthorityDraftedEvent)
                        ((org.axonframework.messaging.Message<?>) events.getFirst()).getPayload();
                assertThat(event.applyApplicationId()).isEqualTo(applyApplicationId);
                assertThat(event.priorAuthorityId()).isEqualTo(priorAuthorityId);
                return true;
              }

              @Override
              public void describeTo(org.hamcrest.Description description) {
                description.appendText("PriorAuthorityDraftedEvent with expected fields");
              }
            });
  }

  @Test
  void givenDraftApplication_whenCreatePriorAuthority_thenRejectsWithConflict() {
    UUID applyApplicationId = UUID.randomUUID();

    fixture
        .given(draftedEvent(applyApplicationId))
        .when(new CreatePriorAuthorityDraftCommand(applyApplicationId, UUID.randomUUID()))
        .expectException(uk.gov.justice.laa.dstew.access.exception.ConflictException.class);
  }

  @Test
  void givenNoApplication_whenCreatePriorAuthority_thenRejectsWithAggregateNotFound() {
    UUID applyApplicationId = UUID.randomUUID();

    fixture
        .givenNoPriorActivity()
        .when(new CreatePriorAuthorityDraftCommand(applyApplicationId, UUID.randomUUID()))
        .expectException(org.axonframework.modelling.command.AggregateNotFoundException.class);
  }

  @Test
  void givenExistingPriorAuthorityDraft_whenUpdated_thenPublishesUpdatedEvent() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID priorAuthorityId = UUID.randomUUID();

    fixture
        .given(
            draftedEvent(applyApplicationId),
            submittedEvent(applyApplicationId),
            new PriorAuthorityDraftedEvent(
                applyApplicationId, priorAuthorityId, Instant.parse("2026-07-15T08:00:00Z")))
        .when(new UpdatePriorAuthorityDraftCommand(applyApplicationId, priorAuthorityId))
        .expectEventsMatching(
            new org.hamcrest.BaseMatcher<>() {
              @Override
              public boolean matches(Object o) {
                var events = (java.util.List<?>) o;
                assertThat(events).hasSize(1);
                PriorAuthorityDraftUpdatedEvent event =
                    (PriorAuthorityDraftUpdatedEvent)
                        ((org.axonframework.messaging.Message<?>) events.getFirst()).getPayload();
                assertThat(event.applyApplicationId()).isEqualTo(applyApplicationId);
                assertThat(event.priorAuthorityId()).isEqualTo(priorAuthorityId);
                return true;
              }

              @Override
              public void describeTo(org.hamcrest.Description description) {
                description.appendText("PriorAuthorityDraftUpdatedEvent with expected fields");
              }
            });
  }

  @Test
  void givenUnknownPriorAuthority_whenUpdated_thenRejectsWithEntityNotFound() {
    UUID applyApplicationId = UUID.randomUUID();

    fixture
        .given(draftedEvent(applyApplicationId), submittedEvent(applyApplicationId))
        .when(new UpdatePriorAuthorityDraftCommand(applyApplicationId, UUID.randomUUID()))
        .expectException(
            org.axonframework.modelling.command.AggregateEntityNotFoundException.class);
  }

  @Test
  void givenPriorAuthorityDraft_whenSubmitted_thenPublishesPriorAuthoritySubmittedEvent() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID priorAuthorityId = UUID.randomUUID();

    fixture
        .given(
            draftedEvent(applyApplicationId),
            submittedEvent(applyApplicationId),
            new PriorAuthorityDraftedEvent(
                applyApplicationId, priorAuthorityId, Instant.parse("2026-07-15T08:00:00Z")))
        .when(new SubmitPriorAuthorityCommand(applyApplicationId, priorAuthorityId))
        .expectEventsMatching(
            new org.hamcrest.BaseMatcher<>() {
              @Override
              public boolean matches(Object o) {
                var events = (java.util.List<?>) o;
                assertThat(events).hasSize(1);
                PriorAuthoritySubmittedEvent event =
                    (PriorAuthoritySubmittedEvent)
                        ((org.axonframework.messaging.Message<?>) events.getFirst()).getPayload();
                assertThat(event.applyApplicationId()).isEqualTo(applyApplicationId);
                assertThat(event.priorAuthorityId()).isEqualTo(priorAuthorityId);
                return true;
              }

              @Override
              public void describeTo(org.hamcrest.Description description) {
                description.appendText("PriorAuthoritySubmittedEvent with expected fields");
              }
            });
  }

  @Test
  void givenSubmittedPriorAuthority_whenSubmittedAgain_thenRejectsWithConflict() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID priorAuthorityId = UUID.randomUUID();

    fixture
        .given(
            draftedEvent(applyApplicationId),
            submittedEvent(applyApplicationId),
            new PriorAuthorityDraftedEvent(
                applyApplicationId, priorAuthorityId, Instant.parse("2026-07-15T08:00:00Z")),
            new PriorAuthoritySubmittedEvent(
                applyApplicationId, priorAuthorityId, Instant.parse("2026-07-15T08:00:00Z")))
        .when(new SubmitPriorAuthorityCommand(applyApplicationId, priorAuthorityId))
        .expectException(uk.gov.justice.laa.dstew.access.exception.ConflictException.class);
  }

  // ---- helpers ---------------------------------------------------------------------------------

  private SubmitApplicationCommand submitCommand(UUID applyApplicationId) {
    return new SubmitApplicationCommand(
        applyApplicationId,
        UUID.randomUUID(),
        "APPLICATION_SUBMITTED",
        "LAA-123",
        1,
        "APPLY",
        Instant.parse("2026-07-14T12:30:00Z"),
        "1A001B",
        false,
        null,
        null);
  }

  private ApplicationDraftedEvent draftedEvent(UUID applyApplicationId) {
    return new ApplicationDraftedEvent(applyApplicationId, Instant.parse("2026-07-14T09:00:00Z"));
  }

  private ApplicationSubmittedEvent submittedEvent(UUID applyApplicationId) {
    return new ApplicationSubmittedEvent(
        applyApplicationId,
        UUID.randomUUID(),
        "APPLICATION_SUBMITTED",
        "LAA-123",
        1,
        "APPLY",
        Instant.parse("2026-07-14T12:30:00Z"),
        "1A001B",
        false,
        null,
        null,
        Instant.parse("2026-07-15T08:00:00Z"));
  }
}
