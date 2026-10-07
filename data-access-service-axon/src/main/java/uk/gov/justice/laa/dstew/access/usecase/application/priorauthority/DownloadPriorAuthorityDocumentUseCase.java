package uk.gov.justice.laa.dstew.access.usecase.application.priorauthority;

import java.util.UUID;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.content.priorauthority.EvidenceDocument;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.EvidenceDocumentDownload;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.FindPriorAuthorityDocumentQuery;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;

/** Retrieves streamable content and metadata for an owned Prior Authority document. */
@Service
public class DownloadPriorAuthorityDocumentUseCase {

  private final QueryGateway queryGateway;
  private final SdsService sdsService;

  /** Creates the use case with Prior Authority lookup and SDS dependencies. */
  public DownloadPriorAuthorityDocumentUseCase(QueryGateway queryGateway, SdsService sdsService) {
    this.queryGateway = queryGateway;
    this.sdsService = sdsService;
  }

  /**
   * Retrieves document metadata and streamable content when the document belongs to the request.
   */
  @AllowApiCaseworker
  public EvidenceDocumentDownload downloadDocument(UUID priorAuthorityId, UUID documentId) {
    EvidenceDocument document =
        queryGateway
            .query(
                new FindPriorAuthorityDocumentQuery(priorAuthorityId, documentId),
                EvidenceDocument.class)
            .join();
    if (document == null) {
      throw new ResourceNotFoundException(
          "No document found with ID: %s for prior authority: %s"
              .formatted(documentId, priorAuthorityId));
    }
    return new EvidenceDocumentDownload(
        document,
        sdsService.getEvidenceFile(priorAuthorityId, documentId, document.storageFilename()));
  }
}
