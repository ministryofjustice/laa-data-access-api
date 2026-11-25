package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.application.note.CreateNoteCommand;
import uk.gov.justice.laa.dstew.access.model.CreateNoteRequest;

/** Maps the create-note HTTP contract to an Axon command. */
@Component
public class CreateNoteCommandMapper {

  /** Maps a request for the supplied Application identifier. */
  public CreateNoteCommand toCommand(UUID applicationId, CreateNoteRequest request) {
    return new CreateNoteCommand(applicationId, request.getNotes(), Instant.now());
  }
}
