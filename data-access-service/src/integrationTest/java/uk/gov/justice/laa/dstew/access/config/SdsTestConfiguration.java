package uk.gov.justice.laa.dstew.access.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.access.exception.FileConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.DocumentDownloadResponse;
import uk.gov.justice.laa.dstew.access.model.DocumentUpdateResponse;
import uk.gov.justice.laa.dstew.access.model.DocumentUploadResponse;
import uk.gov.justice.laa.dstew.access.model.SdsHealthResponse;
import uk.gov.justice.laa.dstew.access.service.applications.SdsService;

/**
 * Test configuration that provides a mocked SdsService for integration tests. This allows tests to
 * run without requiring an actual SDS API endpoint.
 */
@TestConfiguration
public class SdsTestConfiguration {

  private static final Set<String> uploadedFiles = new HashSet<>();

  @Bean
  @Primary
  public SdsService mockSdsService() {
    SdsService mockService = mock(SdsService.class);

    when(mockService.saveFile(any(UUID.class), any(MultipartFile.class)))
        .thenAnswer(
            invocation -> {
              UUID appId = invocation.getArgument(0);
              MultipartFile file = invocation.getArgument(1);
              String fileKey = appId + "/" + file.getOriginalFilename();

              if (file.getSize() == 0) {
                throw new IllegalArgumentException("File cannot be empty");
              }

              if (uploadedFiles.contains(fileKey)) {
                throw new FileConflictException("File already exists in SDS");
              }

              uploadedFiles.add(fileKey);

              DocumentUploadResponse response = new DocumentUploadResponse();
              response.setDetail(appId + "/" + file.getOriginalFilename());
              response.setSuccess("File uploaded successfully");
              response.setChecksum("mock-checksum-" + file.getOriginalFilename());
              return response;
            });

    when(mockService.getFile(any(UUID.class), anyString()))
        .thenAnswer(
            invocation -> {
              UUID appId = invocation.getArgument(0);
              String docId = invocation.getArgument(1);
              String fileKey = appId + "/" + docId;

              if (!uploadedFiles.contains(fileKey)) {
                throw new ResourceNotFoundException("File not found");
              }

              DocumentDownloadResponse response = new DocumentDownloadResponse();
              response.setFileURL("https://sds-mock.example.com/files/" + fileKey);
              return response;
            });

    when(mockService.saveOrUpdateFile(any(MultipartFile.class)))
        .thenAnswer(
            invocation -> {
              MultipartFile file = invocation.getArgument(0);
              DocumentUpdateResponse response = new DocumentUpdateResponse();
              response.setFileURL(
                  "https://sds-mock.example.com/files/updated/" + file.getOriginalFilename());
              return response;
            });

    doAnswer(
            invocation -> {
              UUID appId = invocation.getArgument(0);
              List<String> fileIds = invocation.getArgument(1);

              for (String fileId : fileIds) {
                String fileKey = appId + "/" + fileId;
                if (!uploadedFiles.contains(fileKey)) {
                  throw new ResourceNotFoundException("File not found");
                }
              }

              for (String fileId : fileIds) {
                uploadedFiles.remove(appId + "/" + fileId);
              }
              return null;
            })
        .when(mockService)
        .deleteFiles(any(UUID.class), anyList());

    when(mockService.getHealth())
        .thenAnswer(
            invocation -> {
              SdsHealthResponse response = new SdsHealthResponse();
              response.setHealth("UP");
              return response;
            });

    return mockService;
  }

  /** Reset the uploaded files tracker between tests. */
  public static void reset() {
    uploadedFiles.clear();
  }
}
