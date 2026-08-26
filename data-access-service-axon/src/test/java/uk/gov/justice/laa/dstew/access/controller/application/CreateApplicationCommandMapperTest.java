package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.dstew.access.model.ApplicationCreateRequest;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;

class CreateApplicationCommandMapperTest {

  private final CreateApplicationCommandMapper mapper =
      new CreateApplicationCommandMapper(JsonMapper.builder().build());

  @Test
  void givenRequest_whenMapped_thenSelectsApplySchema() {
    UUID id = UUID.randomUUID();
    var command = mapper.toCommand(request(id), 2);

    assertThat(command.schemaName()).isEqualTo("BaseCivilApplication.json");
  }

  @Test
  void givenValidId_whenMapped_thenApplicationIdEqualsRequestId() {
    UUID id = UUID.randomUUID();
    var command = mapper.toCommand(request(id), 1);

    assertThat(command.applicationId()).isEqualTo(id);
  }

  @Test
  void givenNullStatus_whenMapped_thenStatusIsNull() {
    var command = mapper.toCommand(request(UUID.randomUUID()), 1);

    assertThat(command.status()).isNull();
  }

  @Test
  void givenFullyPopulatedRequest_whenMapped_thenMapsAllFields() {
    UUID id = UUID.randomUUID();
    var request =
        ApplicationCreateRequest.builder()
            .id(id)
            .status(ApplicationStatus.APPLICATION_SUBMITTED)
            .laaReference("LAA-123")
            .applicationContent(Map.of("key", "value"))
            .build();

    var command = mapper.toCommand(request, 2);

    assertThat(command.status()).isEqualTo("APPLICATION_SUBMITTED");
    assertThat(command.laaReference()).isEqualTo("LAA-123");
    assertThat(command.applicationContent()).containsEntry("key", "value");
    assertThat(command.schemaVersion()).isEqualTo(2);
  }

  @Test
  void givenSerialisationFails_whenMapped_thenThrowsIllegalStateException() throws Exception {
    ObjectMapper mockMapper = mock(ObjectMapper.class);
    CreateApplicationCommandMapper failingMapper = new CreateApplicationCommandMapper(mockMapper);
    ApplicationCreateRequest request =
        ApplicationCreateRequest.builder()
            .id(UUID.randomUUID())
            .applicationContent(Map.of())
            .build();
    when(mockMapper.writeValueAsString(any())).thenThrow(new JacksonException("boom") {});

    assertThatThrownBy(() -> failingMapper.toCommand(request, 1))
        .isInstanceOf(IllegalStateException.class);
  }

  private ApplicationCreateRequest request(UUID id) {
    return ApplicationCreateRequest.builder().id(id).applicationContent(Map.of()).build();
  }
}
