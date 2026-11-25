package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.FindPriorAuthorityDraftQuery;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentDeleteCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftSummary;
import uk.gov.justice.laa.dstew.access.document.DocumentMetadata;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.observability.ErrorReporter;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCaller;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;

/** Marks a prior-authority draft document deleted and then attempts SDS file deletion. */
@Component
public class DeletePriorAuthorityDocumentUseCase {
  private final QueryGateway queryGateway;
  private final RetryingCommandDispatcher dispatcher;
  private final SdsService sdsService;
  private final ErrorReporter errorReporter;

  /**
   * Convenience constructor retained for existing call sites/tests that do not need to verify
   * Sentry reporting explicitly.
   */
  public DeletePriorAuthorityDocumentUseCase(
      QueryGateway queryGateway, RetryingCommandDispatcher dispatcher, SdsService sdsService) {
    this(queryGateway, dispatcher, sdsService, new ErrorReporter());
  }

  /** Creates the use case with draft lookup, command dispatch, and SDS dependencies. */
  @Autowired
  public DeletePriorAuthorityDocumentUseCase(
      QueryGateway queryGateway,
      RetryingCommandDispatcher dispatcher,
      SdsService sdsService,
      ErrorReporter errorReporter) {
    this.queryGateway = queryGateway;
    this.dispatcher = dispatcher;
    this.sdsService = sdsService;
    this.errorReporter = errorReporter;
  }

  /** Records the deletion durably before attempting best-effort SDS deletion. */
  @AllowApiCaseworker
  public void execute(UUID priorAuthorityId, UUID documentId, AuthenticatedCaller caller) {
    PriorAuthorityDraftSummary draft =
        queryGateway
            .query(
                new FindPriorAuthorityDraftQuery(priorAuthorityId, documentId),
                PriorAuthorityDraftSummary.class)
            .join();
    if (draft == null || !draft.exists()) {
      if (draft != null) {
        draft.requireDocumentsChangeable();
      }
      throw new ResourceNotFoundException(
          "Prior Authority %s not found".formatted(priorAuthorityId));
    }
    String originalFileName = draft.originalFilename();
    DocumentMetadata document =
        dispatcher.dispatch(
            new PriorAuthorityDocumentDeleteCommand(
                priorAuthorityId, documentId, Instant.now(), caller),
            DocumentMetadata.class);
    String storageFilename =
        document.fileSuffix() == null ? originalFileName : documentId + document.fileSuffix();

    try {
      sdsService.deleteEvidenceFile(priorAuthorityId, documentId, storageFilename);
    } catch (RuntimeException exception) {
      errorReporter.report(
          "Prior Authority document was deleted but SDS file deletion failed for"
              + " priorAuthorityId=%s documentId=%s".formatted(priorAuthorityId, documentId),
          exception);
    }
  }
}
