package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.model.CreateNoteRequest;
import uk.gov.justice.laa.dstew.access.usecase.createnote.CreateNoteCommand;
import uk.gov.justice.laa.dstew.access.utils.generator.DataGenerator;
import uk.gov.justice.laa.dstew.access.utils.generator.notes.CreateNoteRequestGenerator;

class CreateNoteCommandMapperTest {

  private final CreateNoteCommandMapper mapper = new CreateNoteCommandMapper(new ObjectMapper());

  @Test
  void givenFullyPopulatedRequest_whenToCreateNoteCommand_thenAllFieldsMapped() {
    UUID applicationId = UUID.randomUUID();
    CreateNoteRequest request =
        DataGenerator.createDefault(
            CreateNoteRequestGenerator.class, b -> b.notes("test note text"));

    CreateNoteCommand command = mapper.toCreateNoteCommand(applicationId, request);

    assertThat(command.applicationId()).isEqualTo(applicationId);
    assertThat(command.noteText()).isEqualTo("test note text");
    assertThat(command.serialisedNoteRequest()).isNotNull();

    JsonNode tree = new ObjectMapper().readTree(command.serialisedNoteRequest());
    assertThat(tree.get("notes").asString()).isEqualTo("test note text");
  }

  @Test
  void givenNullNotesField_whenToCreateNoteCommand_thenNoteTextIsNullAndJsonContainsNullNotes() {
    UUID applicationId = UUID.randomUUID();
    CreateNoteRequest request =
        DataGenerator.createDefault(CreateNoteRequestGenerator.class, b -> b.notes(null));

    CreateNoteCommand command = mapper.toCreateNoteCommand(applicationId, request);

    assertThat(command.applicationId()).isEqualTo(applicationId);
    assertThat(command.noteText()).isNull();
    assertThat(command.serialisedNoteRequest()).isNotNull();

    JsonNode tree = new ObjectMapper().readTree(command.serialisedNoteRequest());
    assertThat(tree.get("notes").isNull()).isTrue();
  }
}
