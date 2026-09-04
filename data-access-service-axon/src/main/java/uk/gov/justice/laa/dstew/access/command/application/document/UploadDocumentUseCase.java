package uk.gov.justice.laa.dstew.access.command.application.document;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.access.model.DocumentUploadResponse;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;

/** Uploads a document to SDS on behalf of an Application. */
@Component
@RequiredArgsConstructor
public class UploadDocumentUseCase {

  private final SdsService sdsService;

  /**
   * Uploads the given file to SDS under the application's folder.
   *
   * @param applicationId the application the document belongs to
   * @param file the file to upload
   * @return the upload response from SDS containing the file key and checksum
   */
  @AllowApiCaseworker
  public DocumentUploadResponse execute(UUID applicationId, MultipartFile file) {
    return sdsService.saveFile(applicationId, UUID.randomUUID(), file);
  }
}
