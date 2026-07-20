package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.note.CreateNoteCommand;
import uk.gov.justice.laa.dstew.access.model.CreateNoteRequest;

/** Maps the create-note HTTP contract to an Axon command. */
@Component
public class CreateNoteCommandMapper {

  private final ObjectMapper objectMapper;

  public CreateNoteCommandMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Maps a request for the supplied Application identifier. */
  public CreateNoteCommand toCommand(UUID applicationId, CreateNoteRequest request) {
    return new CreateNoteCommand(
        applicationId, request.getNotes(), serialise(request), Instant.now());
  }

  private String serialise(CreateNoteRequest request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Unable to serialise CreateNoteRequest", exception);
    }
  }
}
