package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityDocument;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;

/** Retrieves streamable content and metadata for an owned Prior Authority document. */
@Service
public class DownloadPriorAuthorityDocumentUseCase {

  private final GetPriorAuthorityUseCase getPriorAuthorityUseCase;
  private final SdsService sdsService;

  /** Creates the use case with Prior Authority lookup and SDS dependencies. */
  public DownloadPriorAuthorityDocumentUseCase(
      GetPriorAuthorityUseCase getPriorAuthorityUseCase, SdsService sdsService) {
    this.getPriorAuthorityUseCase = getPriorAuthorityUseCase;
    this.sdsService = sdsService;
  }

  /**
   * Retrieves document metadata and streamable content when the document belongs to the request.
   */
  @AllowApiCaseworker
  public PriorAuthorityDocumentDownload downloadDocument(UUID priorAuthorityId, UUID documentId) {
    PriorAuthorityDocument document = getDocument(priorAuthorityId, documentId);
    return new PriorAuthorityDocumentDownload(
        document,
        sdsService.getPriorAuthorityFile(priorAuthorityId, documentId, document.fileName()));
  }

  private PriorAuthorityDocument getDocument(UUID priorAuthorityId, UUID documentId) {
    List<PriorAuthorityDocument> documents =
        getPriorAuthorityUseCase.getPriorAuthority(priorAuthorityId).uploadedDocuments();
    if (documents == null) {
      throw documentNotFound(priorAuthorityId, documentId);
    }
    return documents.stream()
        .filter(document -> documentId.equals(document.documentId()))
        .findFirst()
        .orElseThrow(() -> documentNotFound(priorAuthorityId, documentId));
  }

  private ResourceNotFoundException documentNotFound(UUID priorAuthorityId, UUID documentId) {
    return new ResourceNotFoundException(
        "No document found with ID: %s for prior authority: %s"
            .formatted(documentId, priorAuthorityId));
  }
}
