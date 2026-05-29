package uk.gov.justice.laa.dstew.access.service.applications;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.justice.laa.dstew.access.exception.FileConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.DocumentDownloadResponse;
import uk.gov.justice.laa.dstew.access.model.DocumentUpdateResponse;
import uk.gov.justice.laa.dstew.access.model.DocumentUploadResponse;
import uk.gov.justice.laa.dstew.access.model.SdsHealthResponse;
import uk.gov.justice.laa.dstew.access.service.sds.TokenService;

/** Service class for interacting with the Secure Document Storage (SDS) API. */
@Service
@RequiredArgsConstructor
@Slf4j
public class SdsService {

  private static final String SAVE_FILE_ENDPOINT = "/save_file";
  private static final String SAVE_OR_UPDATE_FILE_ENDPOINT = "/save_or_update_file";
  private static final String GET_FILE_ENDPOINT = "/get_file";
  private static final String DELETE_FILES_ENDPOINT = "/delete_files";
  private static final String HEALTH_ENDPOINT = "/health";

  private static final String FILE_KEY_PARAM = "file_key";
  private static final String FILE_KEYS_PARAM = "file_keys";
  private static final String BUCKET_NAME_FIELD = "bucketName";
  private static final String FOLDER_FIELD = "folder";
  private static final String PATH_SEPARATOR = "/";

  private final RestClient sdsRestClient;
  private final TokenService tokenService;

  @Value("${app.sds-api.url}")
  private String sdsApiUrl;

  @Value("${app.sds-api.bucket-name}")
  private String bucketName;

  /**
   * Save a file in the SDS service.
   *
   * @param applicationId the application ID used as folder name
   * @param file the file to be saved
   * @return the file URL response from SDS
   */
  public DocumentUploadResponse saveFile(UUID applicationId, MultipartFile file) {
    String folderName = applicationId.toString();
    Map<String, String> bodyMap = new HashMap<>();
    bodyMap.put(BUCKET_NAME_FIELD, bucketName);
    bodyMap.put(FOLDER_FIELD, folderName);

    MultipartBodyBuilder builder = buildMultipartBody(file, bodyMap);

    DocumentUploadResponse response =
        sdsRestClient
            .post()
            .uri(sdsApiUrl + SAVE_FILE_ENDPOINT)
            .headers(headers -> headers.setBearerAuth(tokenService.getSdsAccessToken()))
            .contentType(MULTIPART_FORM_DATA)
            .body(builder.build())
            .retrieve()
            .onStatus(
                status -> status.value() == HttpStatus.CONFLICT.value(),
                (request, response1) -> {
                  throw new FileConflictException("File already exists in SDS");
                })
            .body(DocumentUploadResponse.class);

    log.info("File saved to SDS: {}", response);
    return response;
  }

  /**
   * Save or update a file in the SDS service.
   *
   * @param file the file to be saved or updated
   * @return the file URL response from SDS
   */
  public DocumentUpdateResponse saveOrUpdateFile(MultipartFile file) {
    Map<String, String> bodyMap = Map.of(BUCKET_NAME_FIELD, bucketName);
    MultipartBodyBuilder builder = buildMultipartBody(file, bodyMap);

    DocumentUpdateResponse response =
        sdsRestClient
            .put()
            .uri(sdsApiUrl + SAVE_OR_UPDATE_FILE_ENDPOINT)
            .headers(headers -> headers.setBearerAuth(tokenService.getSdsAccessToken()))
            .contentType(MULTIPART_FORM_DATA)
            .body(builder.build())
            .retrieve()
            .body(DocumentUpdateResponse.class);

    log.info("File updated in SDS: {}", response);
    return response;
  }

  /**
   * Get the file URL from the SDS service.
   *
   * @param applicationId the application ID
   * @param documentId the document ID
   * @return the file URL response from SDS
   */
  public DocumentDownloadResponse getFile(UUID applicationId, String documentId) {
    String fileKey = buildFileKey(applicationId, documentId);
    String uri =
        UriComponentsBuilder.fromUriString(sdsApiUrl + GET_FILE_ENDPOINT)
            .queryParam(FILE_KEY_PARAM, fileKey)
            .toUriString();

    DocumentDownloadResponse response =
        sdsRestClient
            .get()
            .uri(uri)
            .headers(headers -> headers.setBearerAuth(tokenService.getSdsAccessToken()))
            .accept(APPLICATION_JSON)
            .retrieve()
            .onStatus(
                status -> status.value() == HttpStatus.NOT_FOUND.value(),
                (request, response1) -> {
                  throw new ResourceNotFoundException("File not found");
                })
            .body(DocumentDownloadResponse.class);

    log.info("File retrieved from SDS: {}", response);
    return response;
  }

  /**
   * Delete files from the SDS service.
   *
   * @param applicationId the application ID
   * @param fileIds the list of file IDs to be deleted
   */
  public void deleteFiles(UUID applicationId, List<String> fileIds) {
    List<String> fileKeys =
        fileIds.stream().map(fileId -> buildFileKey(applicationId, fileId)).toList();

    UriComponentsBuilder builder =
        UriComponentsBuilder.fromUriString(sdsApiUrl + DELETE_FILES_ENDPOINT);
    fileKeys.forEach(key -> builder.queryParam(FILE_KEYS_PARAM, key));
    String uri = builder.toUriString();

    sdsRestClient
        .delete()
        .uri(uri)
        .headers(headers -> headers.setBearerAuth(tokenService.getSdsAccessToken()))
        .retrieve()
        .onStatus(
            status -> status.value() == HttpStatus.NOT_FOUND.value(),
            (request, response) -> {
              throw new ResourceNotFoundException("File not found");
            })
        .toBodilessEntity();

    log.info("Files deleted from SDS: {}", fileKeys);
  }

  /**
   * Get the health status of the SDS service.
   *
   * @return the health response from SDS
   */
  public SdsHealthResponse getHealth() {
    SdsHealthResponse response =
        sdsRestClient
            .get()
            .uri(sdsApiUrl + HEALTH_ENDPOINT)
            .headers(headers -> headers.setBearerAuth(tokenService.getSdsAccessToken()))
            .accept(APPLICATION_JSON)
            .retrieve()
            .body(SdsHealthResponse.class);

    log.info("SDS health check: {}", response);
    return response;
  }

  /**
   * Build file key from application ID and document ID.
   *
   * @param applicationId the application ID
   * @param documentId the document ID
   * @return the file key
   */
  private String buildFileKey(UUID applicationId, String documentId) {
    return applicationId.toString() + PATH_SEPARATOR + documentId;
  }

  /**
   * Build multipart body with file and JSON body fields.
   *
   * @param file the file to upload
   * @param bodyFields the JSON body fields
   * @return the multipart body builder
   */
  private MultipartBodyBuilder buildMultipartBody(
      MultipartFile file, Map<String, String> bodyFields) {
    MultipartBodyBuilder builder = new MultipartBodyBuilder();
    builder.part("file", file.getResource()).contentType(APPLICATION_OCTET_STREAM);

    // Convert map to JSON string manually (simple format)
    StringBuilder jsonBody = new StringBuilder("{");
    bodyFields.forEach(
        (key, value) -> {
          if (jsonBody.length() > 1) {
            jsonBody.append(",");
          }
          jsonBody.append(String.format("\"%s\":\"%s\"", key, value));
        });
    jsonBody.append("}");

    builder.part("body", jsonBody.toString());
    return builder;
  }
}
