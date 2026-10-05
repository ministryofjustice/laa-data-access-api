package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.document.PriorAuthorityDocumentFormat;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.document.DocumentDetails;
import uk.gov.justice.laa.dstew.access.document.DocumentDownload;
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
  public DocumentDownload downloadDocument(UUID priorAuthorityId, UUID documentId) {
    DocumentDetails document = getDocument(priorAuthorityId, documentId);
    return new DocumentDownload(
        document, sdsService.getEvidenceFile(priorAuthorityId, documentId, document.fileName()));
  }

  private DocumentDetails getDocument(UUID priorAuthorityId, UUID documentId) {
    PriorAuthorityResult result = getPriorAuthorityUseCase.getPriorAuthority(priorAuthorityId);
    if (result.uploadedDocuments() == null) {
      throw documentNotFound(priorAuthorityId, documentId);
    }
    Map<UUID, String> filenames =
        result.documentFilenames() == null ? Map.of() : result.documentFilenames();
    return result.uploadedDocuments().stream()
        .filter(document -> !document.deleted() && documentId.equals(document.documentId()))
        .findFirst()
        .map(
            document ->
                new DocumentDetails(
                    document.documentId(),
                    document.documentType(),
                    filenames.get(documentId),
                    PriorAuthorityDocumentFormat.fromContentType(document.contentType())
                        .map(PriorAuthorityDocumentFormat::fileType)
                        .orElse(null),
                    document.contentType(),
                    document.size(),
                    document.uploadedAt(),
                    document.sourceService(),
                    document.checksum()))
        .orElseThrow(() -> documentNotFound(priorAuthorityId, documentId));
  }

  private ResourceNotFoundException documentNotFound(UUID priorAuthorityId, UUID documentId) {
    return new ResourceNotFoundException(
        "No document found with ID: %s for prior authority: %s"
            .formatted(documentId, priorAuthorityId));
  }
}
