package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.ValidateApplicationGrantedCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.UploadPriorAuthorityDocumentResponse;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;

/** Dispatches a single command that uploads and finalises prior-authority documents. */
@Component
public class UploadPriorAuthorityDocumentUseCase {

  private final PriorAuthorityDraftStore draftStore;
  private final RetryingCommandDispatcher dispatcher;

  public UploadPriorAuthorityDocumentUseCase(
      PriorAuthorityDraftStore draftStore, RetryingCommandDispatcher dispatcher) {
    this.draftStore = draftStore;
    this.dispatcher = dispatcher;
  }

  /** Uploads a file and finalises it via a single aggregate command. */
  @AllowApiCaseworker
  public UploadPriorAuthorityDocumentResponse execute(UUID priorAuthorityId, MultipartFile file) {
    var draft =
        draftStore
            .find(priorAuthorityId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Prior Authority %s not found".formatted(priorAuthorityId)));
    dispatcher.dispatch(new ValidateApplicationGrantedCommand(draft.applicationId()));

    UUID documentId =
        dispatcher.dispatch(
            new PriorAuthorityDocumentUploadCommand(priorAuthorityId, file, "{}", Instant.now()),
            UUID.class);

    return new UploadPriorAuthorityDocumentResponse().documentId(documentId);
  }
}
