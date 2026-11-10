package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.ExpectedLinkedGroup;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.LinkType;
import uk.gov.justice.laa.dstew.access.model.ApplicationLinkRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationLinkType;
import uk.gov.justice.laa.dstew.access.model.EventHistoryRequest;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

class LinkApplicationCommandMapperTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-15T09:30:00Z");

  private final Clock fixedClock = Clock.fixed(OCCURRED_AT, ZoneOffset.UTC);
  private final LinkApplicationCommandMapper mapper = new LinkApplicationCommandMapper(fixedClock);

  @Test
  void givenValidRequest_whenMapped_thenPreservesDirectionalIds() {
    UUID sourceApplicationId = UUID.randomUUID();
    UUID targetApplicationId = UUID.randomUUID();
    var request = new ApplicationLinkRequest(targetApplicationId, ApplicationLinkType.FAMILY);

    var command = mapper.toCommand(sourceApplicationId, request);

    assertThat(command.sourceApplicationId()).isEqualTo(sourceApplicationId);
    assertThat(command.targetApplicationId()).isEqualTo(targetApplicationId);
  }

  @Test
  void givenEventDescription_whenMapped_thenMapsToCommandJustification() {
    var request = new ApplicationLinkRequest(UUID.randomUUID(), ApplicationLinkType.FAMILY);
    request.setEventHistory(new EventHistoryRequest().eventDescription("description"));

    var command = mapper.toCommand(UUID.randomUUID(), request);

    assertThat(command.justification()).isEqualTo("description");
  }

  @ParameterizedTest
  @MethodSource("absentOrEmptyEventHistory")
  void givenAbsentOrEmptyEventHistory_whenMapped_thenMapsNullJustification(
      EventHistoryRequest eventHistory) {
    var request = new ApplicationLinkRequest(UUID.randomUUID(), ApplicationLinkType.FAMILY);
    request.setEventHistory(eventHistory);

    var command = mapper.toCommand(UUID.randomUUID(), request);

    assertThat(command.justification()).isNull();
  }

  static Stream<EventHistoryRequest> absentOrEmptyEventHistory() {
    return Stream.of(null, new EventHistoryRequest());
  }

  @Test
  void givenFamilyLinkType_whenMapped_thenMapsToDomainLinkType() {
    var request = new ApplicationLinkRequest(UUID.randomUUID(), ApplicationLinkType.FAMILY);

    var command = mapper.toCommand(UUID.randomUUID(), request);

    assertThat(command.linkType()).isEqualTo(LinkType.FAMILY);
  }

  @Test
  void givenFixedClock_whenMapped_thenOccurredAtMatchesClock() {
    var request = new ApplicationLinkRequest(UUID.randomUUID(), ApplicationLinkType.FAMILY);

    var command = mapper.toCommand(UUID.randomUUID(), request);

    assertThat(command.occurredAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  void givenValidVersionToken_whenMapped_thenMapsExpectedGroup() {
    var groupId = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");
    var request = new ApplicationLinkRequest(UUID.randomUUID(), ApplicationLinkType.FAMILY);
    request.setLinkedGroupVersion(
        "djE6bGlua2VkLWdyb3VwOjdjOWU2Njc5LTc0MjUtNDBkZS05NDRiLWUwN2ZjMWY5MGFlNzo3");

    var command = mapper.toCommand(UUID.randomUUID(), request);

    assertThat(command.expectedTargetGroup()).isEqualTo(new ExpectedLinkedGroup(groupId, 7L));
  }

  @Test
  void givenNullVersionToken_whenMapped_thenMapsNullExpectedGroup() {
    var request = new ApplicationLinkRequest(UUID.randomUUID(), ApplicationLinkType.FAMILY);

    var command = mapper.toCommand(UUID.randomUUID(), request);

    assertThat(command.expectedTargetGroup()).isNull();
  }

  @Test
  void givenInvalidVersionToken_whenMapped_thenThrowsValidationException() {
    var request = new ApplicationLinkRequest(UUID.randomUUID(), ApplicationLinkType.FAMILY);
    request.setLinkedGroupVersion("not-a-token");

    assertThatThrownBy(() -> mapper.toCommand(UUID.randomUUID(), request))
        .isInstanceOfSatisfying(
            ValidationException.class,
            exception ->
                assertThat(exception.errors())
                    .containsExactly(
                        "linkedGroupVersion: must be a valid linked group version token"));
  }
}
