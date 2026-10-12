package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentTypeUpdateCommand;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Dispatches a command that sets or replaces a Prior Authority document type. */
@Component
public class UpdatePriorAuthorityDocumentTypeUseCase {

  private final RetryingCommandDispatcher dispatcher;

  /** Creates the use case with command dispatch. */
  public UpdatePriorAuthorityDocumentTypeUseCase(RetryingCommandDispatcher dispatcher) {
    this.dispatcher = dispatcher;
  }

  /** Updates the type of an uploaded document in a Prior Authority draft. */
  @AllowApiCaseworker
  public UpdatePriorAuthorityDocumentTypeResult execute(
      UUID priorAuthorityId, UUID documentId, String documentType, String serialisedRequest) {
    Instant updatedAt = Instant.now();
    dispatcher.dispatch(
        new PriorAuthorityDocumentTypeUpdateCommand(
            priorAuthorityId, documentId, documentType, serialisedRequest, updatedAt));
    return new UpdatePriorAuthorityDocumentTypeResult(documentId, updatedAt);
  }
}
