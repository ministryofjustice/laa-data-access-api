package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.dstew.access.command.application.linkedgroup.UnlinkApplicationCommand;
import uk.gov.justice.laa.dstew.access.model.LinkedGroupChangeRequest;
import uk.gov.justice.laa.dstew.access.validation.ValidationException;

class UnlinkApplicationCommandMapperTest {

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-15T09:30:00Z");

  private final UnlinkApplicationCommandMapper mapper =
      new UnlinkApplicationCommandMapper(Clock.fixed(OCCURRED_AT, ZoneOffset.UTC));

  @Test
  void givenValidRequest_whenMapped_thenPreservesApplicationVersionAndTime() {
    UUID applicationId = UUID.randomUUID();

    UnlinkApplicationCommand command =
        mapper.toCommand(applicationId, new LinkedGroupChangeRequest(7L));

    assertThat(command.applicationId()).isEqualTo(applicationId);
    assertThat(command.expectedGroupVersion()).isEqualTo(7L);
    assertThat(command.occurredAt()).isEqualTo(OCCURRED_AT);
  }

  @Test
  void givenNullVersion_whenMapped_thenRejectsRequest() {
    assertThatThrownBy(() -> mapper.toCommand(UUID.randomUUID(), new LinkedGroupChangeRequest()))
        .isInstanceOf(ValidationException.class)
        .extracting(exception -> ((ValidationException) exception).errors())
        .isEqualTo(List.of("linkedGroupVersion: must not be null"));
  }
}
