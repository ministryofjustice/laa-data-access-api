package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentDeleteCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;

/** Marks a prior-authority draft document deleted and then attempts SDS file deletion. */
@Component
public class DeletePriorAuthorityDocumentUseCase {
  private static final Logger LOG =
      LoggerFactory.getLogger(DeletePriorAuthorityDocumentUseCase.class);

  private final PriorAuthorityDraftStore draftStore;
  private final RetryingCommandDispatcher dispatcher;
  private final SdsService sdsService;

  /** Creates the use case with draft lookup, command dispatch, and SDS dependencies. */
  public DeletePriorAuthorityDocumentUseCase(
      PriorAuthorityDraftStore draftStore,
      RetryingCommandDispatcher dispatcher,
      SdsService sdsService) {
    this.draftStore = draftStore;
    this.dispatcher = dispatcher;
    this.sdsService = sdsService;
  }

  /** Records the deletion durably before attempting best-effort SDS deletion. */
  @AllowApiCaseworker
  public void execute(UUID priorAuthorityId, UUID documentId) {
    draftStore
        .find(priorAuthorityId)
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "Prior Authority %s not found".formatted(priorAuthorityId)));
    DocumentMetadata document =
        dispatcher.dispatch(
            new PriorAuthorityDocumentDeleteCommand(priorAuthorityId, documentId, Instant.now()),
            DocumentMetadata.class);

    try {
      String fileName =
          documentId
              + PriorAuthorityDocumentFormat.fromContentType(document.contentType())
                  .orElseThrow(() -> new IllegalStateException("Unsupported document content type"))
                  .fileExtension();
      sdsService.deleteFiles(priorAuthorityId, List.of(fileName));
    } catch (RuntimeException exception) {
      LOG.error(
          "Prior Authority document was deleted but SDS file deletion failed for priorAuthorityId={} documentId={}",
          priorAuthorityId,
          documentId,
          exception);
    }
  }
}
