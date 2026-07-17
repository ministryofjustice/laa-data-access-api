package uk.gov.justice.laa.dstew.access.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
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

  @Test
  void givenNoApplication_whenCreated_thenPublishesCreatedEventAndReturnsApplyApplicationId() {
    UUID applyApplicationId = UUID.randomUUID();
    CreateApplicationCommand command = submitCommand(applyApplicationId);

    fixture
        .givenNoPriorActivity()
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
                description.appendText("ApplicationSubmittedEvent with expected fields");
              }
            });
  }

  @Test
  void
      givenApplicationAlreadyCreated_whenCommandRedelivered_thenReturnsIdempotentlyWithNoNewEvent() {
    UUID applyApplicationId = UUID.randomUUID();

    fixture
        .given(createdEvent(applyApplicationId))
        .when(submitCommand(applyApplicationId))
        .expectResultMessagePayload(applyApplicationId)
        .expectNoEvents();
  }

  @Test
  void givenDraftedApplication_whenSubmitted_thenTransitionsWithApplicationSubmittedEvent() {
    UUID applyApplicationId = UUID.randomUUID();

    fixture
        .given(
            new ApplicationDraftedEvent(
                applyApplicationId,
                Map.of("status", "DRAFT"),
                Instant.parse("2026-07-15T08:00:00Z")))
        .when(submitCommand(applyApplicationId))
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
                assertThat(event.status()).isEqualTo("APPLICATION_SUBMITTED");
                return true;
              }

              @Override
              public void describeTo(org.hamcrest.Description description) {
                description.appendText("ApplicationSubmittedEvent transitioning from a draft");
              }
            });
  }

  @Test
  void
      givenSubmittedApplication_whenCreatePriorAuthority_thenPublishesPriorAuthorityDraftedEvent() {
    UUID applyApplicationId = UUID.randomUUID();
    Map<String, Object> content = Map.of("reference", "PA-1", "amount", 500);

    fixture
        .given(createdEvent(applyApplicationId))
        .when(new CreatePriorAuthorityDraftCommand(applyApplicationId, content))
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
                assertThat(event.priorAuthorityId()).isNotNull();
                assertThat(event.content()).isEqualTo(content);
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
        .given(
            new ApplicationDraftedEvent(
                applyApplicationId,
                Map.of("status", "DRAFT"),
                Instant.parse("2026-07-15T08:00:00Z")))
        .when(new CreatePriorAuthorityDraftCommand(applyApplicationId, Map.of("reference", "PA-1")))
        .expectException(uk.gov.justice.laa.dstew.access.exception.ConflictException.class);
  }

  @Test
  void givenNoApplication_whenCreatePriorAuthority_thenRejectsWithAggregateNotFound() {
    UUID applyApplicationId = UUID.randomUUID();

    fixture
        .givenNoPriorActivity()
        .when(new CreatePriorAuthorityDraftCommand(applyApplicationId, Map.of("reference", "PA-1")))
        .expectException(org.axonframework.modelling.command.AggregateNotFoundException.class);
  }

  @Test
  void givenExistingPriorAuthorityDraft_whenUpdated_thenPublishesUpdatedEvent() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID priorAuthorityId = UUID.randomUUID();
    Map<String, Object> updatedContent = Map.of("reference", "PA-1", "amount", 750);

    fixture
        .given(
            createdEvent(applyApplicationId),
            new PriorAuthorityDraftedEvent(
                applyApplicationId,
                priorAuthorityId,
                Map.of("reference", "PA-1", "amount", 500),
                Instant.parse("2026-07-15T08:00:00Z")))
        .when(
            new UpdatePriorAuthorityDraftCommand(
                applyApplicationId, priorAuthorityId, updatedContent))
        .expectEventsMatching(
            new org.hamcrest.BaseMatcher<>() {
              @Override
              public boolean matches(Object o) {
                var events = (java.util.List<?>) o;
                assertThat(events).hasSize(1);
                PriorAuthorityDraftUpdatedEvent event =
                    (PriorAuthorityDraftUpdatedEvent)
                        ((org.axonframework.messaging.Message<?>) events.getFirst()).getPayload();
                assertThat(event.priorAuthorityId()).isEqualTo(priorAuthorityId);
                assertThat(event.content()).isEqualTo(updatedContent);
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
        .given(createdEvent(applyApplicationId))
        .when(
            new UpdatePriorAuthorityDraftCommand(
                applyApplicationId, UUID.randomUUID(), Map.of("reference", "PA-1")))
        .expectException(
            org.axonframework.modelling.command.AggregateEntityNotFoundException.class);
  }

  @Test
  void givenPriorAuthorityDraft_whenSubmitted_thenPublishesPriorAuthoritySubmittedEvent() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID priorAuthorityId = UUID.randomUUID();

    fixture
        .given(
            createdEvent(applyApplicationId),
            new PriorAuthorityDraftedEvent(
                applyApplicationId,
                priorAuthorityId,
                Map.of("reference", "PA-1"),
                Instant.parse("2026-07-15T08:00:00Z")))
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
            createdEvent(applyApplicationId),
            new PriorAuthorityDraftedEvent(
                applyApplicationId,
                priorAuthorityId,
                Map.of("reference", "PA-1"),
                Instant.parse("2026-07-15T08:00:00Z")),
            new PriorAuthoritySubmittedEvent(
                applyApplicationId, priorAuthorityId, Instant.parse("2026-07-15T08:00:00Z")))
        .when(new SubmitPriorAuthorityCommand(applyApplicationId, priorAuthorityId))
        .expectException(uk.gov.justice.laa.dstew.access.exception.ConflictException.class);
  }

  @Test
  void givenNoActivity_whenCreateDraftApplication_thenPublishesApplicationDraftedEvent() {
    UUID draftApplicationId = UUID.randomUUID();
    Map<String, Object> content = Map.of("status", "DRAFT", "laaReference", "LAA-DRAFT-1");

    fixture
        .givenNoPriorActivity()
        .when(new CreateDraftApplicationCommand(draftApplicationId, content))
        .expectResultMessagePayload(draftApplicationId)
        .expectEventsMatching(
            new org.hamcrest.BaseMatcher<>() {
              @Override
              public boolean matches(Object o) {
                var events = (java.util.List<?>) o;
                assertThat(events).hasSize(1);
                ApplicationDraftedEvent event =
                    (ApplicationDraftedEvent)
                        ((org.axonframework.messaging.Message<?>) events.getFirst()).getPayload();
                assertThat(event.draftApplicationId()).isEqualTo(draftApplicationId);
                assertThat(event.content()).isEqualTo(content);
                return true;
              }

              @Override
              public void describeTo(org.hamcrest.Description description) {
                description.appendText("ApplicationDraftedEvent with expected fields");
              }
            });
  }

  @Test
  void givenDraftApplication_whenCreateRedelivered_thenIdempotentWithNoNewEvent() {
    UUID draftApplicationId = UUID.randomUUID();

    fixture
        .given(
            new ApplicationDraftedEvent(
                draftApplicationId,
                Map.of("status", "DRAFT"),
                Instant.parse("2026-07-15T08:00:00Z")))
        .when(new CreateDraftApplicationCommand(draftApplicationId, Map.of("status", "DRAFT")))
        .expectResultMessagePayload(draftApplicationId)
        .expectNoEvents();
  }

  @Test
  void givenDraftApplication_whenUpdated_thenPublishesApplicationDraftUpdatedEvent() {
    UUID draftApplicationId = UUID.randomUUID();
    Map<String, Object> updatedContent = Map.of("status", "DRAFT", "laaReference", "LAA-DRAFT-2");

    fixture
        .given(
            new ApplicationDraftedEvent(
                draftApplicationId,
                Map.of("status", "DRAFT"),
                Instant.parse("2026-07-15T08:00:00Z")))
        .when(new UpdateDraftApplicationCommand(draftApplicationId, updatedContent))
        .expectEventsMatching(
            new org.hamcrest.BaseMatcher<>() {
              @Override
              public boolean matches(Object o) {
                var events = (java.util.List<?>) o;
                assertThat(events).hasSize(1);
                ApplicationDraftUpdatedEvent event =
                    (ApplicationDraftUpdatedEvent)
                        ((org.axonframework.messaging.Message<?>) events.getFirst()).getPayload();
                assertThat(event.draftApplicationId()).isEqualTo(draftApplicationId);
                assertThat(event.content()).isEqualTo(updatedContent);
                return true;
              }

              @Override
              public void describeTo(org.hamcrest.Description description) {
                description.appendText("ApplicationDraftUpdatedEvent with expected fields");
              }
            });
  }

  @Test
  void givenNoDraftApplication_whenUpdated_thenRejectsWithAggregateNotFound() {
    UUID draftApplicationId = UUID.randomUUID();

    fixture
        .givenNoPriorActivity()
        .when(new UpdateDraftApplicationCommand(draftApplicationId, Map.of("status", "DRAFT")))
        .expectException(org.axonframework.modelling.command.AggregateNotFoundException.class);
  }

  private CreateApplicationCommand submitCommand(UUID applyApplicationId) {
    return new CreateApplicationCommand(
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

  private ApplicationSubmittedEvent createdEvent(UUID applyApplicationId) {
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
