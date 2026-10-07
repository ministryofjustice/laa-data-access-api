package uk.gov.justice.laa.dstew.access.usecase.application;

import java.util.UUID;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.content.priorauthority.EvidenceDocument;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.query.application.FindApplicationDocumentQuery;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.EvidenceDocumentDownload;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;

/** Retrieves streamable content and metadata for a document owned by an Application. */
@Service
public class DownloadApplicationDocumentUseCase {

  private final QueryGateway queryGateway;
  private final SdsService sdsService;

  /** Creates the use case with projection and SDS dependencies. */
  public DownloadApplicationDocumentUseCase(QueryGateway queryGateway, SdsService sdsService) {
    this.queryGateway = queryGateway;
    this.sdsService = sdsService;
  }

  /** Retrieves document metadata and content when the document belongs to the Application. */
  @AllowApiCaseworker
  public EvidenceDocumentDownload downloadDocument(UUID applicationId, UUID documentId) {
    EvidenceDocument document =
        queryGateway
            .query(
                new FindApplicationDocumentQuery(applicationId, documentId), EvidenceDocument.class)
            .join();
    if (document == null) {
      throw new ResourceNotFoundException(
          "No document found with ID: %s for application: %s".formatted(documentId, applicationId));
    }
    return new EvidenceDocumentDownload(
        document,
        sdsService.getEvidenceFile(applicationId, documentId, document.storageFilename()));
  }
}
