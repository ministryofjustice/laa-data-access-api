package uk.gov.justice.laa.dstew.access.controller.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import uk.gov.justice.laa.dstew.access.command.application.draft.CreateApplicationDraftV2Command;
import uk.gov.justice.laa.dstew.access.command.application.draft.SubmitApplicationDraftV2Command;
import uk.gov.justice.laa.dstew.access.command.application.draft.UpdateApplicationDraftCommand;
import uk.gov.justice.laa.dstew.access.model.CreateApplicationDraftV2Request;
import uk.gov.justice.laa.dstew.access.model.UpdateApplicationDraftV2Request;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCaller;

/** Maps the generated v2 draft HTTP models to Axon commands. */
@Component
public class ApplicationDraftV2CommandMapper {

  private static final String METADATA_FIELD = "metadata";

  private final ObjectMapper objectMapper;

  public ApplicationDraftV2CommandMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** Creates a command for a new v2 draft using the client-supplied Application ID. */
  public CreateApplicationDraftV2Command toCreateCommand(
      CreateApplicationDraftV2Request request, int schemaVersion) {
    return new CreateApplicationDraftV2Command(
        request.getId(),
        request.getStatus() == null ? null : request.getStatus().name(),
        request.getLaaReference(),
        request.getApplicationContent(),
        serialiseBusinessRequest(request),
        schemaVersion,
        Instant.now(),
        request.getPotentialDuplicates(),
        request.getMetadata());
  }

  /** Creates a command replacing the body of the v2 draft for the given Application ID. */
  public UpdateApplicationDraftCommand toUpdateCommand(
      UUID applicationId, UpdateApplicationDraftV2Request request, AuthenticatedCaller caller) {
    return new UpdateApplicationDraftCommand(
        applicationId,
        request.getStatus() == null ? null : request.getStatus().name(),
        request.getLaaReference(),
        request.getApplicationContent(),
        serialiseBusinessRequest(request),
        Instant.now(),
        request.getPotentialDuplicates(),
        request.getMetadata(),
        caller);
  }

  /** Creates a submit command for the given v2 draft. */
  public SubmitApplicationDraftV2Command toSubmitCommand(UUID applicationId) {
    return new SubmitApplicationDraftV2Command(applicationId, Instant.now());
  }

  private String serialiseBusinessRequest(Object request) {
    try {
      ObjectNode node = objectMapper.valueToTree(request);
      node.remove(METADATA_FIELD);
      return objectMapper.writeValueAsString(node);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Unable to serialise draft request", exception);
    }
  }
}
