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

  // ---- Assign / unassign caseworker (application) ----------------------------------------------

  @Test
  void givenSubmittedApplication_whenAssigned_thenPublishesCaseworkerAssignedEvent() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();

    fixture
        .given(draftedEvent(applicationId), submittedEvent(applicationId))
        .when(new AssignApplicationCaseworkerCommand(applicationId, caseworkerId))
        .expectEvents(new CaseworkerAssignedEvent(applicationId, null, caseworkerId, FIXED_NOW));
  }

  @Test
  void givenAssignedApplication_whenAssignedSameCaseworker_thenIdempotentWithNoEvent() {
    UUID applicationId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();

    fixture
        .given(
            draftedEvent(applicationId),
            submittedEvent(applicationId),
            new CaseworkerAssignedEvent(applicationId, null, caseworkerId, FIXED_NOW))
        .when(new AssignApplicationCaseworkerCommand(applicationId, caseworkerId))
        .expectNoEvents();
  }

  @Test
  void givenAssignedApplication_whenAssignedDifferentCaseworker_thenRejectsWithConflict() {
    UUID applicationId = UUID.randomUUID();

    fixture
        .given(
            draftedEvent(applicationId),
            submittedEvent(applicationId),
            new CaseworkerAssignedEvent(applicationId, null, UUID.randomUUID(), FIXED_NOW))
        .when(new AssignApplicationCaseworkerCommand(applicationId, UUID.randomUUID()))
        .expectException(uk.gov.justice.laa.dstew.access.exception.ConflictException.class);
  }

  @Test
  void givenDraftApplication_whenAssigned_thenRejectsWithConflict() {
    UUID applicationId = UUID.randomUUID();

    fixture
        .given(draftedEvent(applicationId))
        .when(new AssignApplicationCaseworkerCommand(applicationId, UUID.randomUUID()))
        .expectException(uk.gov.justice.laa.dstew.access.exception.ConflictException.class);
  }

  @Test
  void givenAssignedApplication_whenUnassigned_thenPublishesCaseworkerUnassignedEvent() {
    UUID applicationId = UUID.randomUUID();

    fixture
        .given(
            draftedEvent(applicationId),
            submittedEvent(applicationId),
            new CaseworkerAssignedEvent(applicationId, null, UUID.randomUUID(), FIXED_NOW))
        .when(new UnassignApplicationCaseworkerCommand(applicationId))
        .expectEvents(new CaseworkerUnassignedEvent(applicationId, null, FIXED_NOW));
  }

  @Test
  void givenUnassignedApplication_whenUnassigned_thenRejectsWithConflict() {
    UUID applicationId = UUID.randomUUID();

    fixture
        .given(draftedEvent(applicationId), submittedEvent(applicationId))
        .when(new UnassignApplicationCaseworkerCommand(applicationId))
        .expectException(uk.gov.justice.laa.dstew.access.exception.ConflictException.class);
  }

  // ---- Assign / unassign caseworker (prior authority member) -----------------------------------

  @Test
  void givenSubmittedPriorAuthority_whenAssigned_thenPublishesCaseworkerAssignedEvent() {
    UUID applicationId = UUID.randomUUID();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID caseworkerId = UUID.randomUUID();

    fixture
        .given(submittedPriorAuthority(applicationId, priorAuthorityId))
        .when(
            new AssignPriorAuthorityCaseworkerCommand(
                applicationId, priorAuthorityId, caseworkerId))
        .expectEvents(
            new CaseworkerAssignedEvent(applicationId, priorAuthorityId, caseworkerId, FIXED_NOW));
  }

  @Test
  void givenAssignedPriorAuthority_whenAssignedDifferentCaseworker_thenRejectsWithConflict() {
    UUID applicationId = UUID.randomUUID();
    UUID priorAuthorityId = UUID.randomUUID();

    fixture
        .given(appendAssigned(applicationId, priorAuthorityId, UUID.randomUUID()))
        .when(
            new AssignPriorAuthorityCaseworkerCommand(
                applicationId, priorAuthorityId, UUID.randomUUID()))
        .expectException(uk.gov.justice.laa.dstew.access.exception.ConflictException.class);
  }

  @Test
  void givenDraftPriorAuthority_whenAssigned_thenRejectsWithConflict() {
    UUID applicationId = UUID.randomUUID();
    UUID priorAuthorityId = UUID.randomUUID();

    fixture
        .given(
            draftedEvent(applicationId),
            submittedEvent(applicationId),
            new PriorAuthorityDraftedEvent(applicationId, priorAuthorityId, FIXED_NOW))
        .when(
            new AssignPriorAuthorityCaseworkerCommand(
                applicationId, priorAuthorityId, UUID.randomUUID()))
        .expectException(uk.gov.justice.laa.dstew.access.exception.ConflictException.class);
  }

  @Test
  void givenAssignedPriorAuthority_whenUnassigned_thenPublishesCaseworkerUnassignedEvent() {
    UUID applicationId = UUID.randomUUID();
    UUID priorAuthorityId = UUID.randomUUID();

    fixture
        .given(appendAssigned(applicationId, priorAuthorityId, UUID.randomUUID()))
        .when(new UnassignPriorAuthorityCaseworkerCommand(applicationId, priorAuthorityId))
        .expectEvents(new CaseworkerUnassignedEvent(applicationId, priorAuthorityId, FIXED_NOW));
  }

  @Test
  void givenTwoPriorAuthorities_whenAssignedSeparately_thenEachHoldsItsOwnCaseworker() {
    UUID applicationId = UUID.randomUUID();
    UUID priorAuthorityA = UUID.randomUUID();
    UUID priorAuthorityB = UUID.randomUUID();
    UUID caseworkerA = UUID.randomUUID();
    UUID caseworkerB = UUID.randomUUID();

    // A is already assigned to caseworker A; assigning B to a different caseworker must succeed
    // (no cross-talk) and emit an event routed to member B only.
    fixture
        .given(
            draftedEvent(applicationId),
            submittedEvent(applicationId),
            new PriorAuthorityDraftedEvent(applicationId, priorAuthorityA, FIXED_NOW),
            new PriorAuthoritySubmittedEvent(applicationId, priorAuthorityA, FIXED_NOW),
            new PriorAuthorityDraftedEvent(applicationId, priorAuthorityB, FIXED_NOW),
            new PriorAuthoritySubmittedEvent(applicationId, priorAuthorityB, FIXED_NOW),
            new CaseworkerAssignedEvent(applicationId, priorAuthorityA, caseworkerA, FIXED_NOW))
        .when(
            new AssignPriorAuthorityCaseworkerCommand(applicationId, priorAuthorityB, caseworkerB))
        .expectEvents(
            new CaseworkerAssignedEvent(applicationId, priorAuthorityB, caseworkerB, FIXED_NOW));
  }

  @Test
  void givenPriorAuthorityAssignedToOne_whenAssignedToAnother_thenRejectsWithConflict() {
    UUID applicationId = UUID.randomUUID();
    UUID priorAuthorityA = UUID.randomUUID();
    UUID priorAuthorityB = UUID.randomUUID();
    UUID caseworkerA = UUID.randomUUID();

    // Member A is held by caseworker A; re-assigning A to a different caseworker is a conflict,
    // proving assignment state is per-member, not shared across prior authorities.
    fixture
        .given(
            draftedEvent(applicationId),
            submittedEvent(applicationId),
            new PriorAuthorityDraftedEvent(applicationId, priorAuthorityA, FIXED_NOW),
            new PriorAuthoritySubmittedEvent(applicationId, priorAuthorityA, FIXED_NOW),
            new PriorAuthorityDraftedEvent(applicationId, priorAuthorityB, FIXED_NOW),
            new PriorAuthoritySubmittedEvent(applicationId, priorAuthorityB, FIXED_NOW),
            new CaseworkerAssignedEvent(applicationId, priorAuthorityA, caseworkerA, FIXED_NOW))
        .when(
            new AssignPriorAuthorityCaseworkerCommand(
                applicationId, priorAuthorityA, UUID.randomUUID()))
        .expectException(uk.gov.justice.laa.dstew.access.exception.ConflictException.class);
  }

  @Test
  void givenApplicationAssigned_whenPriorAuthorityAssigned_thenAssignmentsAreIndependent() {
    UUID applicationId = UUID.randomUUID();
    UUID priorAuthorityId = UUID.randomUUID();
    UUID applicationCaseworker = UUID.randomUUID();
    UUID priorAuthorityCaseworker = UUID.randomUUID();

    // The application root is already assigned; assigning the PA member to a *different* caseworker
    // must succeed, proving the root assignee and the member assignee are separate state.
    fixture
        .given(
            draftedEvent(applicationId),
            submittedEvent(applicationId),
            new CaseworkerAssignedEvent(applicationId, null, applicationCaseworker, FIXED_NOW),
            new PriorAuthorityDraftedEvent(applicationId, priorAuthorityId, FIXED_NOW),
            new PriorAuthoritySubmittedEvent(applicationId, priorAuthorityId, FIXED_NOW))
        .when(
            new AssignPriorAuthorityCaseworkerCommand(
                applicationId, priorAuthorityId, priorAuthorityCaseworker))
        .expectEvents(
            new CaseworkerAssignedEvent(
                applicationId, priorAuthorityId, priorAuthorityCaseworker, FIXED_NOW));
  }

  // ---- helpers ---------------------------------------------------------------------------------

  private static final Instant FIXED_NOW = Instant.parse("2026-07-15T08:00:00Z");

  private Object[] submittedPriorAuthority(UUID applicationId, UUID priorAuthorityId) {
    return new Object[] {
      draftedEvent(applicationId),
      submittedEvent(applicationId),
      new PriorAuthorityDraftedEvent(applicationId, priorAuthorityId, FIXED_NOW),
      new PriorAuthoritySubmittedEvent(applicationId, priorAuthorityId, FIXED_NOW)
    };
  }

  private Object[] appendAssigned(UUID applicationId, UUID priorAuthorityId, UUID caseworkerId) {
    Object[] base = submittedPriorAuthority(applicationId, priorAuthorityId);
    Object[] all = java.util.Arrays.copyOf(base, base.length + 1);
    all[base.length] =
        new CaseworkerAssignedEvent(applicationId, priorAuthorityId, caseworkerId, FIXED_NOW);
    return all;
  }

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
