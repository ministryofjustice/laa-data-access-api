package uk.gov.justice.laa.dstew.access.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.access.exception.FileConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.DocumentDownloadResponse;
import uk.gov.justice.laa.dstew.access.model.DocumentUpdateResponse;
import uk.gov.justice.laa.dstew.access.model.DocumentUploadResponse;
import uk.gov.justice.laa.dstew.access.model.SdsHealthResponse;

/**
 * Service class for checking the health of the SDS service.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SdsService {

  private final RestClient sdsRestClient;
  private final TokenService tokenService;

  @Value("${app.sds-api.url}")
  private String sdsApiUrl;

  @Value("${app.sds-api.bucket-name}")
  private String bucketName;

  /**
   * Save a file in the SDS service.
   *
   * @param file the file to be saved
   * @return the file URL response from SDS
   */

  public DocumentUploadResponse saveFile(UUID applicationId, MultipartFile file) {
    MultipartBodyBuilder builder = new MultipartBodyBuilder();
    String folderName = applicationId.toString();
    builder.part("file", file.getResource()).contentType(MediaType.APPLICATION_OCTET_STREAM);
    builder.part("body", String.format("{\"bucketName\":\"%s\",\"folder\":\"%s\"}", bucketName, folderName));

    DocumentUploadResponse sdsSaveFileResponse = sdsRestClient.post()
        .uri(sdsApiUrl + "/save_file")
        .header("Authorization", "Bearer " + tokenService.getSdsAccessToken())
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(builder.build())
        .retrieve()
        .onStatus(status -> status.value() == 409, (request, response) -> {
          throw new FileConflictException("File already exists in SDS");
        })
        .body(DocumentUploadResponse.class);
    log.info("Body: {}", sdsSaveFileResponse.toString());
    return sdsSaveFileResponse;
  }

  /**
   * Save or update a file in the SDS service.
   *
   * @param file the file to be saved or updated
   * @return the file URL response from SDS
   */
  public DocumentUpdateResponse saveOrUpdateFile(MultipartFile file) {
    MultipartBodyBuilder builder = new MultipartBodyBuilder();
    builder.part("file", file.getResource()).contentType(MediaType.APPLICATION_OCTET_STREAM);
    builder.part("body", String.format("{\"bucketName\":\"%s\"}", bucketName));

    DocumentUpdateResponse sdsSaveFileResponse = sdsRestClient.put()
        .uri(sdsApiUrl + "/save_or_update_file")
        .header("Authorization", "Bearer " + tokenService.getSdsAccessToken())
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(builder.build())
        .retrieve()
        .body(DocumentUpdateResponse.class);
    log.info("Body: {}", sdsSaveFileResponse.toString());
    return sdsSaveFileResponse;
  }

  /**
   * Get the file URL from the SDS service.
   *
   * @return the file URL response from SDS
   */
  public DocumentDownloadResponse getFile(UUID applicationId, String documentId) {
    String folderName = applicationId.toString();
    String fileKey = folderName + "/" + documentId;

    DocumentDownloadResponse sdsGetFileResponse = sdsRestClient
        .get()
        .uri(sdsApiUrl + "/get_file?file_key=" + fileKey)
        .header("Authorization", "Bearer " + tokenService.getSdsAccessToken())
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .onStatus(status -> status.value() == 404, (request, response) -> {
          throw new ResourceNotFoundException("File not found");
        })
        .body(DocumentDownloadResponse.class);

    log.info("Body: {}", sdsGetFileResponse.toString());

    return sdsGetFileResponse;
  }

  /**
   * Delete files from the SDS service.
   *
   * @Param fileIds the list of file IDs to be deleted
   */
  public void deleteFiles(UUID applicationId, List<String> fileIds) {
    String folderName = applicationId.toString();
    List<String> fileKeys = fileIds.stream()
        .map(fileId -> folderName + "/" + fileId)
        .toList();
    sdsRestClient
        .delete()
        .uri(sdsApiUrl + "/delete_files?file_keys=" + String.join("&file_keys=", fileKeys))
        .header("Authorization", "Bearer " + tokenService.getSdsAccessToken())
        .retrieve()
        .onStatus(status -> status.value() == 404, (request, response) -> {
          throw new ResourceNotFoundException("File not found");
        })
        .toBodilessEntity();
  }

  /**
   * Check the health of the SDS service.
   *
   * @return the health response from SDS
   */
  public SdsHealthResponse checkHealth() {
    SdsHealthResponse sdsHealthResponse = sdsRestClient
        .get()
        .uri(sdsApiUrl + "/health")
        .header("Authorization", "Bearer " + tokenService.getSdsAccessToken())
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .body(SdsHealthResponse.class);

    return sdsHealthResponse;
  }
}
