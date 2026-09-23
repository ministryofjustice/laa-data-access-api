package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentTypeUpdateCommand;
import uk.gov.justice.laa.dstew.access.model.UpdatePriorAuthorityDocumentTypeRequest;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.util.RequestSerialiser;

/** Dispatches a command that sets or replaces a Prior Authority document type. */
@Component
public class UpdatePriorAuthorityDocumentTypeUseCase {

  private final RetryingCommandDispatcher dispatcher;
  private final ObjectMapper objectMapper;

  /** Creates the use case with command dispatch and request serialisation dependencies. */
  public UpdatePriorAuthorityDocumentTypeUseCase(
      RetryingCommandDispatcher dispatcher, ObjectMapper objectMapper) {
    this.dispatcher = dispatcher;
    this.objectMapper = objectMapper;
  }

  /** Updates the type of an uploaded document in a Prior Authority draft. */
  @AllowApiCaseworker
  public UpdatePriorAuthorityDocumentTypeResult execute(
      UUID priorAuthorityId, UUID documentId, UpdatePriorAuthorityDocumentTypeRequest request) {
    Instant updatedAt = Instant.now();
    dispatcher.dispatch(
        new PriorAuthorityDocumentTypeUpdateCommand(
            priorAuthorityId,
            documentId,
            request.getDocumentType().getValue(),
            RequestSerialiser.serialise(objectMapper, request),
            updatedAt));
    return new UpdatePriorAuthorityDocumentTypeResult(documentId, updatedAt);
  }
}
