package uk.gov.justice.laa.dstew.access.command.application;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.dstew.access.applicationcontent.ApplicationContentParser;
import uk.gov.justice.laa.dstew.access.applicationcontent.PayloadValidator;

class ApplicationAggregateTest {

  private AggregateTestFixture<ApplicationAggregate> fixture;
  private ApplicationContentParser parser;
  private Clock clock;

  @BeforeEach
  void setUp() {
    fixture = new AggregateTestFixture<>(ApplicationAggregate.class);
    var objectMapper = JsonMapper.builder().findAndAddModules().build();
    var validator = Validation.buildDefaultValidatorFactory().getValidator();
    parser = new ApplicationContentParser(new PayloadValidator(objectMapper, validator));
    clock = Clock.fixed(Instant.parse("2026-07-15T08:00:00Z"), ZoneOffset.UTC);
    fixture.registerInjectableResource(parser);
    fixture.registerInjectableResource(clock);
  }

  @Test
  void givenNoApplication_whenCreated_thenPublishesCreatedEventAndReturnsApplyApplicationId() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applyProceedingId = UUID.randomUUID();
    CreateApplicationCommand command =
        new CreateApplicationCommand(
            "APPLICATION_SUBMITTED",
            "LAA-123",
            validContent(applyApplicationId, applyProceedingId),
            List.of(),
            "{}",
            1,
            "ApplyApplication.json",
            "APPLY");

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
    UUID applyProceedingId = UUID.randomUUID();
    Map<String, Object> content = validContent(applyApplicationId, applyProceedingId);
    ApplicationSubmittedEvent existingEvent =
        new ApplicationSubmittedEvent(
            applyApplicationId,
            "APPLICATION_SUBMITTED",
            "LAA-123",
            null,
            List.of(),
            1,
            "APPLY",
            Instant.parse("2026-07-14T12:30:00Z"),
            "1A001B",
            false,
            null,
            null,
            List.of(),
            "{}",
            Instant.parse("2026-07-15T08:00:00Z"));
    CreateApplicationCommand command =
        new CreateApplicationCommand(
            "APPLICATION_SUBMITTED",
            "LAA-123",
            content,
            List.of(),
            "{}",
            1,
            "ApplyApplication.json",
            "APPLY");

    fixture
        .given(existingEvent)
        .when(command)
        .expectResultMessagePayload(applyApplicationId)
        .expectNoEvents();
  }

  @Test
  void givenDraftedApplication_whenSubmitted_thenTransitionsWithApplicationSubmittedEvent() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applyProceedingId = UUID.randomUUID();
    CreateApplicationCommand command =
        new CreateApplicationCommand(
            "APPLICATION_SUBMITTED",
            "LAA-123",
            validContent(applyApplicationId, applyProceedingId),
            List.of(),
            "{}",
            1,
            "ApplyApplication.json",
            "APPLY");

    fixture
        .given(
            new ApplicationDraftedEvent(
                applyApplicationId,
                Map.of("status", "DRAFT"),
                Instant.parse("2026-07-15T08:00:00Z")))
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
  void givenApplication_whenCreatePriorAuthority_thenPublishesDraftPriorAuthorityCreatedEvent() {
    UUID applyApplicationId = UUID.randomUUID();
    Map<String, Object> content = Map.of("reference", "PA-1", "amount", 500);

    fixture
        .given(createdEvent(applyApplicationId))
        .when(new CreateDraftPriorAuthorityCommand(applyApplicationId, content))
        .expectEventsMatching(
            new org.hamcrest.BaseMatcher<>() {
              @Override
              public boolean matches(Object o) {
                var events = (java.util.List<?>) o;
                assertThat(events).hasSize(1);
                DraftPriorAuthorityCreatedEvent event =
                    (DraftPriorAuthorityCreatedEvent)
                        ((org.axonframework.messaging.Message<?>) events.getFirst()).getPayload();
                assertThat(event.applyApplicationId()).isEqualTo(applyApplicationId);
                assertThat(event.priorAuthorityId()).isNotNull();
                assertThat(event.content()).isEqualTo(content);
                return true;
              }

              @Override
              public void describeTo(org.hamcrest.Description description) {
                description.appendText("DraftPriorAuthorityCreatedEvent with expected fields");
              }
            });
  }

  @Test
  void givenNoApplication_whenCreatePriorAuthority_thenRejectsWithAggregateNotFound() {
    UUID applyApplicationId = UUID.randomUUID();

    fixture
        .givenNoPriorActivity()
        .when(new CreateDraftPriorAuthorityCommand(applyApplicationId, Map.of("reference", "PA-1")))
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
            new DraftPriorAuthorityCreatedEvent(
                applyApplicationId,
                priorAuthorityId,
                Map.of("reference", "PA-1", "amount", 500),
                Instant.parse("2026-07-15T08:00:00Z")))
        .when(
            new UpdateDraftPriorAuthorityCommand(
                applyApplicationId, priorAuthorityId, updatedContent))
        .expectEventsMatching(
            new org.hamcrest.BaseMatcher<>() {
              @Override
              public boolean matches(Object o) {
                var events = (java.util.List<?>) o;
                assertThat(events).hasSize(1);
                DraftPriorAuthorityUpdatedEvent event =
                    (DraftPriorAuthorityUpdatedEvent)
                        ((org.axonframework.messaging.Message<?>) events.getFirst()).getPayload();
                assertThat(event.priorAuthorityId()).isEqualTo(priorAuthorityId);
                assertThat(event.content()).isEqualTo(updatedContent);
                return true;
              }

              @Override
              public void describeTo(org.hamcrest.Description description) {
                description.appendText("DraftPriorAuthorityUpdatedEvent with expected fields");
              }
            });
  }

  @Test
  void givenUnknownPriorAuthority_whenUpdated_thenRejectsWithResourceNotFound() {
    UUID applyApplicationId = UUID.randomUUID();

    fixture
        .given(createdEvent(applyApplicationId))
        .when(
            new UpdateDraftPriorAuthorityCommand(
                applyApplicationId, UUID.randomUUID(), Map.of("reference", "PA-1")))
        .expectException(uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException.class);
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

  private ApplicationSubmittedEvent createdEvent(UUID applyApplicationId) {
    return new ApplicationSubmittedEvent(
        applyApplicationId,
        "APPLICATION_SUBMITTED",
        "LAA-123",
        null,
        List.of(),
        1,
        "APPLY",
        Instant.parse("2026-07-14T12:30:00Z"),
        "1A001B",
        false,
        null,
        null,
        List.of(),
        "{}",
        Instant.parse("2026-07-15T08:00:00Z"));
  }

  private Map<String, Object> validContent(UUID applyApplicationId, UUID applyProceedingId) {
    return Map.of(
        "id", applyApplicationId.toString(),
        "submittedAt", "2026-07-14T12:30:00Z",
        "office", Map.of("code", "1A001B"),
        "applicant",
            Map.of(
                "id",
                UUID.randomUUID().toString(),
                "addresses",
                List.of(Map.of("id", UUID.randomUUID().toString()))),
        "proceedings",
            List.of(
                Map.of(
                    "id",
                    applyProceedingId.toString(),
                    "leadProceeding",
                    true,
                    "description",
                    "Care order",
                    "categoryOfLawEnum",
                    "FAMILY",
                    "matterTypeEnum",
                    "SPECIAL_CHILDREN_ACT",
                    "usedDelegatedFunctions",
                    false)));
  }
}
