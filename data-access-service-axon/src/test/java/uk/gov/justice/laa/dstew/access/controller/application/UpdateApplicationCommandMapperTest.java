package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.ApplicationUpdateRequest;

class UpdateApplicationCommandMapperTest {

  private final Clock fixedClock =
      Clock.fixed(Instant.parse("2026-08-21T10:00:00Z"), ZoneOffset.UTC);
  private final UpdateApplicationCommandMapper mapper =
      new UpdateApplicationCommandMapper(JsonMapper.builder().build(), fixedClock);

  @Test
  void givenNullStatus_whenMapped_thenStatusIsNull() {
    var command = mapper.toCommand(UUID.randomUUID(), new ApplicationUpdateRequest());

    assertThat(command.status()).isNull();
  }

  @Test
  void givenNonNullStatus_whenMapped_thenStatusNameIsMapped() {
    var request = new ApplicationUpdateRequest();
    request.setStatus(ApplicationStatus.APPLICATION_SUBMITTED);

    var command = mapper.toCommand(UUID.randomUUID(), request);

    assertThat(command.status()).isEqualTo("APPLICATION_SUBMITTED");
  }

  @Test
  void givenApplicationContent_whenMapped_thenContentPassedThrough() {
    var request = new ApplicationUpdateRequest();
    request.setApplicationContent(Map.of("key", "value"));

    var command = mapper.toCommand(UUID.randomUUID(), request);

    assertThat(command.applicationContent()).containsEntry("key", "value");
  }

  @Test
  void givenFixedClock_whenMapped_thenOccurredAtMatchesClock() {
    var command = mapper.toCommand(UUID.randomUUID(), new ApplicationUpdateRequest());

    assertThat(command.occurredAt()).isEqualTo(Instant.parse("2026-08-21T10:00:00Z"));
  }

  @Test
  void givenSerialisationFails_whenMapped_thenThrowsIllegalStateException() throws Exception {
    ObjectMapper mockMapper = mock(ObjectMapper.class);
    UpdateApplicationCommandMapper failingMapper =
        new UpdateApplicationCommandMapper(mockMapper, fixedClock);
    when(mockMapper.writeValueAsString(any())).thenThrow(new JacksonException("boom") {});

    assertThatThrownBy(
            () -> failingMapper.toCommand(UUID.randomUUID(), new ApplicationUpdateRequest()))
        .isInstanceOf(IllegalStateException.class);
  }
}
