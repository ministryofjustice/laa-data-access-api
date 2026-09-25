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
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.UploadedDocumentData;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.query.SubscriptionProjectionGateway;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.FindPriorAuthorityDocumentDataQuery;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityDocumentPresentQuery;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;
import uk.gov.justice.laa.dstew.access.util.RequestSerialiser;

/** Removes a document from a prior-authority draft and then attempts SDS file deletion. */
@Component
public class DeletePriorAuthorityDocumentUseCase {
  private static final Logger LOG =
      LoggerFactory.getLogger(DeletePriorAuthorityDocumentUseCase.class);

  private final PriorAuthorityDraftStore draftStore;
  private final RetryingCommandDispatcher dispatcher;
  private final SdsService sdsService;
  private final ObjectMapper objectMapper;
  private final SubscriptionProjectionGateway projectionGateway;

  /** Creates the use case with draft lookup, command dispatch, and SDS dependencies. */
  public DeletePriorAuthorityDocumentUseCase(
      PriorAuthorityDraftStore draftStore,
      RetryingCommandDispatcher dispatcher,
      SdsService sdsService,
      ObjectMapper objectMapper,
      SubscriptionProjectionGateway projectionGateway) {
    this.draftStore = draftStore;
    this.dispatcher = dispatcher;
    this.sdsService = sdsService;
    this.objectMapper = objectMapper;
    this.projectionGateway = projectionGateway;
  }

  /** Removes the document reference durably before attempting best-effort SDS deletion. */
  @AllowApiCaseworker
  public boolean execute(UUID priorAuthorityId, UUID documentId) {
    var draft =
        draftStore
            .find(priorAuthorityId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Prior Authority %s not found".formatted(priorAuthorityId)));
    Instant deletedAt = Instant.now();
    boolean projected =
        projectionGateway.awaitProjection(
            new PriorAuthorityDocumentPresentQuery(priorAuthorityId, documentId),
            Boolean.FALSE::equals,
            () ->
                dispatcher.dispatch(
                    new PriorAuthorityDocumentDeleteCommand(
                        priorAuthorityId,
                        documentId,
                        RequestSerialiser.serialise(
                            objectMapper, new DeletePriorAuthorityDocumentRequest(documentId)),
                        deletedAt)));
    UploadedDocumentData document =
        projectionGateway
            .findProjection(
                new FindPriorAuthorityDocumentDataQuery(priorAuthorityId, documentId),
                UploadedDocumentData.class)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Document %s not found for Prior Authority %s"
                            .formatted(documentId, priorAuthorityId)));
    String extension =
        PriorAuthorityDocumentFormat.fromFileType(document.fileType()).fileExtension();

    try {
      String fileName = documentId + extension;
      sdsService.deleteFiles(draft.priorAuthorityId(), List.of(fileName));
    } catch (RuntimeException exception) {
      LOG.error(
          "Prior Authority document was deleted but SDS file deletion failed for priorAuthorityId={} documentId={}",
          priorAuthorityId,
          documentId,
          exception);
    }
    return projected;
  }
}
