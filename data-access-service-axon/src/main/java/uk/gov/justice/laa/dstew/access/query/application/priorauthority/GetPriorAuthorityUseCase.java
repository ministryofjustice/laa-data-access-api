package uk.gov.justice.laa.dstew.access.query.application.priorauthority;

import java.util.UUID;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityDocument;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityResult;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;

/** Retrieves a Prior Authority submission from its projection. */
@Service
public class GetPriorAuthorityUseCase {

  private final QueryGateway queryGateway;
  private final SdsService sdsService;

  /**
   * Constructor for GetPriorAuthorityUseCase.
   *
   * @param queryGateway The Axon QueryGateway used to query Prior Authority read models
   * @param sdsService service used to retrieve document content from SDS
   */
  public GetPriorAuthorityUseCase(QueryGateway queryGateway, SdsService sdsService) {
    this.queryGateway = queryGateway;
    this.sdsService = sdsService;
  }

  /** Retrieves the Prior Authority identified by its submission ID. */
  public PriorAuthorityResult getPriorAuthority(UUID priorAuthorityId) {
    PriorAuthorityResult priorAuthority =
        queryGateway
            .query(
                new FindPriorAuthorityByPriorAuthorityIdQuery(priorAuthorityId),
                PriorAuthorityResult.class)
            .join();
    if (priorAuthority != null) {
      return priorAuthority;
    }
    throw new ResourceNotFoundException("No prior authority found with ID: " + priorAuthorityId);
  }

  /** Retrieves a document only when it belongs to the supplied Prior Authority. */
  public PriorAuthorityDocument getDocument(UUID priorAuthorityId, UUID documentId) {
    return getPriorAuthority(priorAuthorityId).uploadedDocuments().stream()
        .filter(document -> documentId.equals(document.documentId()))
        .findFirst()
        .orElseThrow(
            () ->
                new ResourceNotFoundException(
                    "No document found with ID: %s for prior authority: %s"
                        .formatted(documentId, priorAuthorityId)));
  }

  /** Retrieves streamable content and metadata for an owned Prior Authority document. */
  public PriorAuthorityDocumentDownload downloadDocument(UUID priorAuthorityId, UUID documentId) {
    PriorAuthorityDocument document = getDocument(priorAuthorityId, documentId);
    return new PriorAuthorityDocumentDownload(
        document,
        sdsService.getPriorAuthorityFile(priorAuthorityId, documentId, document.fileName()));
  }
}
