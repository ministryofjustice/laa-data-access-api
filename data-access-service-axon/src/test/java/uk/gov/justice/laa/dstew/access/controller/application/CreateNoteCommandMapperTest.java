package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import uk.gov.justice.laa.dstew.access.command.application.note.CreateNoteCommand;
import uk.gov.justice.laa.dstew.access.model.CreateNoteRequest;

class CreateNoteCommandMapperTest {

  private final CreateNoteCommandMapper mapper =
      new CreateNoteCommandMapper(JsonMapper.builder().build());

  @Test
  void givenValidRequest_whenMapped_thenApplicationIdMatches() {
    UUID applicationId = UUID.randomUUID();
    CreateNoteCommand command = mapper.toCommand(applicationId, new CreateNoteRequest("Test note"));

    assertThat(command.applicationId()).isEqualTo(applicationId);
  }

  @Test
  void givenValidRequest_whenMapped_thenNoteTextMatchesRequestNotes() {
    CreateNoteCommand command =
        mapper.toCommand(UUID.randomUUID(), new CreateNoteRequest("My note content"));

    assertThat(command.noteText()).isEqualTo("My note content");
  }

  @Test
  void givenValidRequest_whenMapped_thenSerialisedRequestIsValidJsonContainingNoteText() {
    CreateNoteCommand command =
        mapper.toCommand(UUID.randomUUID(), new CreateNoteRequest("Serialised note"));

    assertThat(command.serialisedNoteRequest()).contains("Serialised note");
    assertThatCode(() -> JsonMapper.builder().build().readTree(command.serialisedNoteRequest()))
        .doesNotThrowAnyException();
  }

  @Test
  void givenValidRequest_whenMapped_thenOccurredAtIsNonNull() {
    CreateNoteCommand command =
        mapper.toCommand(UUID.randomUUID(), new CreateNoteRequest("Any note"));

    assertThat(command.occurredAt()).isNotNull();
  }
}
