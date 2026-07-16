package uk.gov.justice.laa.dstew.access.command.synchronousapplication;

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

class SynchronousApplicationAggregateTest {

  private AggregateTestFixture<SynchronousApplicationAggregate> fixture;
  private ApplicationContentParser parser;
  private Clock clock;

  @BeforeEach
  void setUp() {
    fixture = new AggregateTestFixture<>(SynchronousApplicationAggregate.class);
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
    CreateSynchronousApplicationCommand command =
        new CreateSynchronousApplicationCommand(
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
                SynchronousApplicationCreatedEvent event =
                    (SynchronousApplicationCreatedEvent)
                        ((org.axonframework.messaging.Message<?>) events.getFirst()).getPayload();
                assertThat(event.applyApplicationId()).isEqualTo(applyApplicationId);
                assertThat(event.status()).isEqualTo("APPLICATION_SUBMITTED");
                assertThat(event.laaReference()).isEqualTo("LAA-123");
                return true;
              }

              @Override
              public void describeTo(org.hamcrest.Description description) {
                description.appendText("SynchronousApplicationCreatedEvent with expected fields");
              }
            });
  }

  @Test
  void
      givenApplicationAlreadyCreated_whenCommandRedelivered_thenReturnsIdempotentlyWithNoNewEvent() {
    UUID applyApplicationId = UUID.randomUUID();
    UUID applyProceedingId = UUID.randomUUID();
    Map<String, Object> content = validContent(applyApplicationId, applyProceedingId);
    SynchronousApplicationCreatedEvent existingEvent =
        new SynchronousApplicationCreatedEvent(
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
    CreateSynchronousApplicationCommand command =
        new CreateSynchronousApplicationCommand(
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
