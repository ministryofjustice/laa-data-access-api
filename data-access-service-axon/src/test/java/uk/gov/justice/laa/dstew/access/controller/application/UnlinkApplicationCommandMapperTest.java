package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.ExpectedLinkedGroup;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.UnlinkApplicationCommand;
import uk.gov.justice.laa.dstew.access.model.LinkedGroupChangeRequest;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

class UnlinkApplicationCommandMapperTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-15T09:30:00Z");

  private final UnlinkApplicationCommandMapper mapper =
      new UnlinkApplicationCommandMapper(Clock.fixed(OCCURRED_AT, ZoneOffset.UTC));

  @Test
  void givenValidRequest_whenMapped_thenPreservesExpectedGroupAndTime() {
    UUID applicationId = UUID.randomUUID();

    UnlinkApplicationCommand command =
        mapper.toCommand(
            applicationId,
            new LinkedGroupChangeRequest(
                "djE6bGlua2VkLWdyb3VwOjdjOWU2Njc5LTc0MjUtNDBkZS05NDRiLWUwN2ZjMWY5MGFlNzo3"));

    assertThat(command.applicationId()).isEqualTo(applicationId);
    assertThat(command.expectedGroup())
        .isEqualTo(
            new ExpectedLinkedGroup(UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7"), 7L));
    assertThat(command.occurredAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  void givenNullVersion_whenMapped_thenRejectsRequest() {
    assertThatThrownBy(() -> mapper.toCommand(UUID.randomUUID(), new LinkedGroupChangeRequest()))
        .isInstanceOf(ValidationException.class)
        .extracting(exception -> ((ValidationException) exception).errors())
        .isEqualTo(List.of("linkedGroupVersion: must not be null"));
  }

  @Test
  void givenInvalidVersionToken_whenMapped_thenRejectsRequest() {
    var request = new LinkedGroupChangeRequest("not-a-token");

    assertThatThrownBy(() -> mapper.toCommand(UUID.randomUUID(), request))
        .isInstanceOf(ValidationException.class)
        .extracting(exception -> ((ValidationException) exception).errors())
        .isEqualTo(List.of("linkedGroupVersion: must be a valid linked group version token"));
  }
}
