package uk.gov.justice.laa.dstew.access.service;

import java.util.List;
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
import uk.gov.justice.laa.dstew.access.model.SdsGetFileResponse;
import uk.gov.justice.laa.dstew.access.model.SdsHealthResponse;
import uk.gov.justice.laa.dstew.access.model.SdsSaveFileResponse;

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

  public SdsSaveFileResponse saveFile(MultipartFile file) {
    MultipartBodyBuilder builder = new MultipartBodyBuilder();
    builder.part("file", file.getResource()).contentType(MediaType.APPLICATION_OCTET_STREAM);
    builder.part("body", String.format("{\"bucketName\":\"%s\"}", bucketName));

    SdsSaveFileResponse sdsSaveFileResponse = sdsRestClient.post()
        .uri(sdsApiUrl + "/save_file")
        .header("Authorization", "Bearer " + tokenService.getSdsAccessToken())
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(builder.build())
        .retrieve()
        .onStatus(status -> status.value() == 409, (request, response) -> {
          throw new FileConflictException("File already exists in SDS");
        })
        .body(SdsSaveFileResponse.class);
    return sdsSaveFileResponse;
  }

  /**
   * Save or update a file in the SDS service.
   *
   * @param file the file to be saved or updated
   * @return the file URL response from SDS
   */
  public SdsSaveFileResponse saveOrUpdateFile(MultipartFile file) {
    MultipartBodyBuilder builder = new MultipartBodyBuilder();
    builder.part("file", file.getResource()).contentType(MediaType.APPLICATION_OCTET_STREAM);
    builder.part("body", String.format("{\"bucketName\":\"%s\"}", bucketName));

    SdsSaveFileResponse sdsSaveFileResponse = sdsRestClient.put()
        .uri(sdsApiUrl + "/save_or_update_file")
        .header("Authorization", "Bearer " + tokenService.getSdsAccessToken())
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(builder.build())
        .retrieve()
        .body(SdsSaveFileResponse.class);
    return sdsSaveFileResponse;
  }

  /**
   * Get the file URL from the SDS service.
   *
   * @return the file URL response from SDS
   */
  public SdsGetFileResponse getFile(String id) {

    SdsGetFileResponse sdsGetFileResponse = sdsRestClient
        .get()
        .uri(sdsApiUrl + "/get_file?file_key=" + id)
        .header("Authorization", "Bearer " + tokenService.getSdsAccessToken())
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .onStatus(status -> status.value() == 404, (request, response) -> {
          throw new ResourceNotFoundException("File not found");
        })
        .body(SdsGetFileResponse.class);

    return sdsGetFileResponse;
  }

  /**
   * Delete files from the SDS service.
   *
   * @Param fileIds the list of file IDs to be deleted
   */
  public void deleteFiles(List<String> fileIds) {
    String response = sdsRestClient
        .delete()
        .uri(sdsApiUrl + "/delete_files?file_keys=" + String.join("&file_keys=", fileIds))
        .header("Authorization", "Bearer " + tokenService.getSdsAccessToken())
        .retrieve()
        .body(String.class);

    if (response == null) {
      throw new RuntimeException("Failed to get health status from SDS");
    }
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
