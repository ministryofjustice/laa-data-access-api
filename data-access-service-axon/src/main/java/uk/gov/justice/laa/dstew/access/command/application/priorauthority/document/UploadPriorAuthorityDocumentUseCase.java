package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.access.model.UploadPriorAuthorityDocumentResponse;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;

/** Uploads a supporting evidence document to SDS on behalf of a Prior Authority. */
@Component
@RequiredArgsConstructor
public class UploadPriorAuthorityDocumentUseCase {

  private final SdsService sdsService;

  /**
   * Uploads the given file to SDS under the Prior Authority's folder, and mints a document id to
   * identify it by.
   *
   * @param priorAuthorityId the Prior Authority the document belongs to
   * @param file the file to upload
   * @return the response containing the minted document id
   */
  @AllowApiCaseworker
  public UploadPriorAuthorityDocumentResponse execute(UUID priorAuthorityId, MultipartFile file) {
    UUID documentId = UUID.randomUUID();
    sdsService.saveFile(priorAuthorityId, documentId, file);
    return new UploadPriorAuthorityDocumentResponse(documentId);
  }
}
