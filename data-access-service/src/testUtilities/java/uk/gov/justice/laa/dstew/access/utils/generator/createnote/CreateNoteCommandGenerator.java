package uk.gov.justice.laa.dstew.access.utils.generator.createnote;

import java.util.UUID;
import uk.gov.justice.laa.dstew.access.usecase.createnote.CreateNoteCommand;
import uk.gov.justice.laa.dstew.access.utils.generator.BaseGenerator;

/** Generator for {@link CreateNoteCommand} test objects. */
public class CreateNoteCommandGenerator
    extends BaseGenerator<CreateNoteCommand, CreateNoteCommand.CreateNoteCommandBuilder> {

  /** Constructs the generator. */
  public CreateNoteCommandGenerator() {
    super(CreateNoteCommand::toBuilder, CreateNoteCommand.CreateNoteCommandBuilder::build);
  }

  @Override
  public CreateNoteCommand createDefault() {
    return CreateNoteCommand.builder()
        .applicationId(UUID.randomUUID())
        .noteText("a default test note")
        .serialisedNoteRequest("{\"notes\":\"a default test note\"}")
        .build();
  }
}
