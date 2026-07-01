package uk.gov.justice.laa.dstew.access.controller.application;

import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.ExcludeFromGeneratedCodeCoverage;
import uk.gov.justice.laa.dstew.access.exception.DomainEventPublishException;
import uk.gov.justice.laa.dstew.access.model.CreateNoteRequest;
import uk.gov.justice.laa.dstew.access.usecase.createnote.CreateNoteCommand;

/** Maps {@link CreateNoteRequest} and path variables to {@link CreateNoteCommand}. */
public class CreateNoteCommandMapper {

  private final ObjectMapper objectMapper;

  /**
   * Constructs the mapper with the provided Jackson ObjectMapper.
   *
   * @param objectMapper the Jackson ObjectMapper to use for serialisation
   */
  public CreateNoteCommandMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Converts a {@link CreateNoteRequest} and the application path variable to a {@link
   * CreateNoteCommand}.
   *
   * @param applicationId the UUID from the request path
   * @param createNoteRequest the HTTP request body
   * @return the command record
   */
  public CreateNoteCommand toCreateNoteCommand(
      UUID applicationId, CreateNoteRequest createNoteRequest) {
    return CreateNoteCommand.builder()
        .applicationId(applicationId)
        .noteText(createNoteRequest.getNotes())
        .serialisedNoteRequest(serialise(createNoteRequest))
        .build();
  }

  @ExcludeFromGeneratedCodeCoverage
  private String serialise(CreateNoteRequest createNoteRequest) {
    try {
      return objectMapper.writeValueAsString(createNoteRequest);
    } catch (JacksonException e) {
      throw new DomainEventPublishException(
          "Unable to serialise CreateNoteRequest for domain event");
    }
  }
}
