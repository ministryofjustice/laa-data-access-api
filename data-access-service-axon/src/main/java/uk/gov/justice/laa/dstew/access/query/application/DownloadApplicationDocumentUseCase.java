package uk.gov.justice.laa.dstew.access.query.application;

import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;
import uk.gov.justice.laa.dstew.access.usecase.application.ApplicationQueryUseCase;

/** Retrieves streamable content for an Application document. */
@Service
public class DownloadApplicationDocumentUseCase {

  private final ApplicationQueryUseCase applicationQueryUseCase;
  private final SdsService sdsService;

  public DownloadApplicationDocumentUseCase(
      ApplicationQueryUseCase applicationQueryUseCase, SdsService sdsService) {
    this.applicationQueryUseCase = applicationQueryUseCase;
    this.sdsService = sdsService;
  }

  /** Retrieves the document after verifying that its parent Application exists. */
  @AllowApiCaseworker
  public ApplicationDocumentDownload downloadDocument(UUID applicationId, UUID documentId) {
    applicationQueryUseCase.getApplicationById(applicationId);
    String fileName = documentId.toString();
    Resource resource = sdsService.getEvidenceFile(applicationId, documentId, fileName);
    return new ApplicationDocumentDownload(fileName, null, resource);
  }
}
