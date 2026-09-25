package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.dstew.access.command.application.draft.CreateApplicationDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.draft.UpdateApplicationDraftCommand;
import uk.gov.justice.laa.dstew.access.model.ApplicationStatus;
import uk.gov.justice.laa.dstew.access.model.CreateApplicationDraftRequest;
import uk.gov.justice.laa.dstew.access.model.SaveApplicationDraftRequest;

class SaveApplicationDraftCommandMapperTest {

  private final SaveApplicationDraftCommandMapper mapper =
      new SaveApplicationDraftCommandMapper(JsonMapper.builder().build());

  @Test
  void givenValidRequest_whenCreateMapped_thenMapsAllFields() {
    UUID applicationId = UUID.randomUUID();
    CreateApplicationDraftRequest request =
        CreateApplicationDraftRequest.builder()
            .id(applicationId)
            .status(ApplicationStatus.APPLICATION_SUBMITTED)
            .laaReference("LAA-123")
            .applicationContent(Map.of("key", "value"))
            .build();

    CreateApplicationDraftCommand command = mapper.toCreateCommand(request, 1);

    assertThat(command.applicationId()).isEqualTo(applicationId);
    assertThat(command.status()).isEqualTo("APPLICATION_SUBMITTED");
    assertThat(command.laaReference()).isEqualTo("LAA-123");
    assertThat(command.applicationContent()).containsEntry("key", "value");
    assertThat(command.schemaVersion()).isEqualTo(1);
    assertThat(command.occurredAt()).isNotNull();
  }

  @Test
  void givenNullStatus_whenCreateMapped_thenStatusIsNull() {
    CreateApplicationDraftRequest request =
        CreateApplicationDraftRequest.builder()
            .id(UUID.randomUUID())
            .applicationContent(Map.of())
            .build();

    CreateApplicationDraftCommand command = mapper.toCreateCommand(request, 1);

    assertThat(command.status()).isNull();
  }

  @Test
  void givenRequest_whenCreateMapped_thenContentIsSerialised() {
    CreateApplicationDraftRequest request =
        CreateApplicationDraftRequest.builder()
            .id(UUID.randomUUID())
            .laaReference("LAA-123")
            .applicationContent(Map.of())
            .build();

    CreateApplicationDraftCommand command = mapper.toCreateCommand(request, 1);

    assertThat(command.serialisedRequest()).contains("LAA-123");
    assertThatCode(() -> JsonMapper.builder().build().readTree(command.serialisedRequest()))
        .doesNotThrowAnyException();
  }

  @Test
  void givenApplicationId_whenUpdateMapped_thenApplicationIdMatches() {
    UUID applicationId = UUID.randomUUID();
    SaveApplicationDraftRequest request =
        SaveApplicationDraftRequest.builder().laaReference("LAA-999").build();

    UpdateApplicationDraftCommand command = mapper.toUpdateCommand(applicationId, request);

    assertThat(command.applicationId()).isEqualTo(applicationId);
    assertThat(command.laaReference()).isEqualTo("LAA-999");
  }

  @Test
  void givenSerialisationFailure_whenCreateMapped_thenWrapsInIllegalStateException() {
    ObjectMapper objectMapper = mock(ObjectMapper.class);
    CreateApplicationDraftRequest request =
        CreateApplicationDraftRequest.builder().id(UUID.randomUUID()).build();
    SaveApplicationDraftCommandMapper failingMapper =
        new SaveApplicationDraftCommandMapper(objectMapper);

    when(objectMapper.writeValueAsString(request)).thenThrow(new JacksonException("boom") {});

    assertThatThrownBy(() -> failingMapper.toCreateCommand(request, 1))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unable to serialise CreateApplicationDraftRequest")
        .hasCauseInstanceOf(JacksonException.class);
  }

  @Test
  void givenSerialisationFailure_whenUpdateMapped_thenWrapsInIllegalStateException() {
    ObjectMapper objectMapper = mock(ObjectMapper.class);
    UUID applicationId = UUID.randomUUID();
    SaveApplicationDraftRequest request = SaveApplicationDraftRequest.builder().build();
    SaveApplicationDraftCommandMapper failingMapper =
        new SaveApplicationDraftCommandMapper(objectMapper);

    when(objectMapper.writeValueAsString(request)).thenThrow(new JacksonException("boom") {});

    assertThatThrownBy(() -> failingMapper.toUpdateCommand(applicationId, request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Unable to serialise SaveApplicationDraftRequest")
        .hasCauseInstanceOf(JacksonException.class);
  }
}
