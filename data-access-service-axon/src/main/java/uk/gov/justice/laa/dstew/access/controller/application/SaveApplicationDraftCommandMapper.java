package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.draft.CreateApplicationDraftCommand;
import uk.gov.justice.laa.dstew.access.command.application.draft.UpdateApplicationDraftCommand;
import uk.gov.justice.laa.dstew.access.model.CreateApplicationDraftRequest;
import uk.gov.justice.laa.dstew.access.model.SaveApplicationDraftRequest;

/** Maps the generated HTTP request model to the Axon save-application-draft commands. */
@Component
public class SaveApplicationDraftCommandMapper {

  private final ObjectMapper objectMapper;

  public SaveApplicationDraftCommandMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Creates a save-draft command for a new draft, with the client-supplied Application ID. */
  public CreateApplicationDraftCommand toCreateCommand(
      CreateApplicationDraftRequest request, int schemaVersion) {
    return new CreateApplicationDraftCommand(
        request.getId(),
        request.getStatus() == null ? null : request.getStatus().name(),
        request.getLaaReference(),
        request.getApplicationContent(),
        serialise(request),
        schemaVersion,
        Instant.now());
  }

  /** Creates a save-draft command for an existing draft identified by the given Application ID. */
  public UpdateApplicationDraftCommand toUpdateCommand(
      UUID applicationId, SaveApplicationDraftRequest request) {
    return new UpdateApplicationDraftCommand(
        applicationId,
        request.getStatus() == null ? null : request.getStatus().name(),
        request.getLaaReference(),
        request.getApplicationContent(),
        serialise(request),
        Instant.now());
  }

  private String serialise(CreateApplicationDraftRequest request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JacksonException exception) {
      throw new IllegalStateException(
          "Unable to serialise CreateApplicationDraftRequest", exception);
    }
  }

  private String serialise(SaveApplicationDraftRequest request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Unable to serialise SaveApplicationDraftRequest", exception);
    }
  }
}
