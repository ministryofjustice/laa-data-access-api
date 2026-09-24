package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentDeleteCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityDocument;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;
import uk.gov.justice.laa.dstew.access.util.RequestSerialiser;

/** Removes a document from a prior-authority draft and then attempts SDS file deletion. */
@Component
public class DeletePriorAuthorityDocumentUseCase {
  private static final Logger LOG =
      LoggerFactory.getLogger(DeletePriorAuthorityDocumentUseCase.class);

  private final PriorAuthorityDraftStore draftStore;
  private final UploadedDocumentStore uploadedDocumentStore;
  private final RetryingCommandDispatcher dispatcher;
  private final SdsService sdsService;
  private final ObjectMapper objectMapper;

  /** Creates the use case with draft lookup, command dispatch, and SDS dependencies. */
  public DeletePriorAuthorityDocumentUseCase(
      PriorAuthorityDraftStore draftStore,
      UploadedDocumentStore uploadedDocumentStore,
      RetryingCommandDispatcher dispatcher,
      SdsService sdsService,
      ObjectMapper objectMapper) {
    this.draftStore = draftStore;
    this.uploadedDocumentStore = uploadedDocumentStore;
    this.dispatcher = dispatcher;
    this.sdsService = sdsService;
    this.objectMapper = objectMapper;
  }

  /** Removes the document reference durably before attempting best-effort SDS deletion. */
  @AllowApiCaseworker
  public void execute(UUID priorAuthorityId, UUID documentId) {
    var draft =
        draftStore
            .find(priorAuthorityId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Prior Authority %s not found".formatted(priorAuthorityId)));
    PriorAuthorityDocument document =
        uploadedDocumentStore.findAllInOrder(List.of(documentId)).stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Document %s not found for Prior Authority %s"
                            .formatted(documentId, priorAuthorityId)));
    Instant deletedAt = Instant.now();
    dispatcher.dispatch(
        new PriorAuthorityDocumentDeleteCommand(
            priorAuthorityId,
            documentId,
            RequestSerialiser.serialise(
                objectMapper, new DeletePriorAuthorityDocumentRequest(documentId)),
            deletedAt));

    try {
      String fileName =
          documentId
              + PriorAuthorityDocumentFormat.fromFileType(document.fileType()).fileExtension();
      sdsService.deleteFiles(draft.priorAuthorityId(), List.of(fileName));
    } catch (RuntimeException exception) {
      LOG.error(
          "Prior Authority document was deleted but SDS file deletion failed for priorAuthorityId={} documentId={}",
          priorAuthorityId,
          documentId,
          exception);
    }
  }
}
