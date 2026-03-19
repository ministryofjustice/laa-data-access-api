package uk.gov.justice.laa.dstew.access.service;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import uk.gov.justice.laa.dstew.access.exception.FileConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.DocumentDownloadResponse;
import uk.gov.justice.laa.dstew.access.model.DocumentUpdateResponse;
import uk.gov.justice.laa.dstew.access.model.DocumentUploadResponse;
import uk.gov.justice.laa.dstew.access.model.SdsHealthResponse;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
class SdsServiceTest {

  @MockitoBean
  private RestClient restClient;

  @MockitoBean
  private TokenService tokenService;

  @Autowired
  private SdsService sdsService;

  @BeforeEach
  void setUp() {
    when(tokenService.getSdsAccessToken()).thenReturn("mock-token");
  }

  @Test
  void givenValidFileAndApplicationId_whenSaveFile_thenReturnDocumentUploadResponse() {
    // Given
    UUID applicationId = UUID.randomUUID();
    MockMultipartFile file = new MockMultipartFile("file", "test-file.pdf", "application/pdf", "test content".getBytes());
    DocumentUploadResponse expectedResponse = mock(DocumentUploadResponse.class);

    RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
    RestClient.RequestBodySpec requestBodySpec = mock(RestClient.RequestBodySpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(endsWith("/save_file"))).thenReturn(requestBodySpec);
    when(requestBodySpec.header("Authorization", "Bearer mock-token")).thenReturn(requestBodySpec);
    when(requestBodySpec.contentType(MediaType.MULTIPART_FORM_DATA)).thenReturn(requestBodySpec);
    when(requestBodySpec.body(any(MultiValueMap.class))).thenReturn(requestBodySpec);
    when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(Predicate.class), any())).thenReturn(responseSpec);
    when(responseSpec.body(DocumentUploadResponse.class)).thenReturn(expectedResponse);

    // When
    DocumentUploadResponse actualResponse = sdsService.saveFile(applicationId, file);

    // Then
    assertThat(actualResponse).isEqualTo(expectedResponse);
    verify(tokenService).getSdsAccessToken();
    verify(restClient).post();
    verify(requestBodyUriSpec).uri(endsWith("/save_file"));
    verify(requestBodySpec).header("Authorization", "Bearer mock-token");
  }

  @Test
  void givenFileAlreadyExists_whenSaveFile_thenThrowFileConflictException() {
    // Given
    UUID applicationId = UUID.randomUUID();
    MockMultipartFile file = new MockMultipartFile("file", "test-file.pdf", "application/pdf", "test content".getBytes());

    RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
    RestClient.RequestBodySpec requestBodySpec = mock(RestClient.RequestBodySpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(endsWith("/save_file"))).thenReturn(requestBodySpec);
    when(requestBodySpec.header("Authorization", "Bearer mock-token")).thenReturn(requestBodySpec);
    when(requestBodySpec.contentType(MediaType.MULTIPART_FORM_DATA)).thenReturn(requestBodySpec);
    when(requestBodySpec.body(any(MultiValueMap.class))).thenReturn(requestBodySpec);
    when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(Predicate.class), any())).thenReturn(responseSpec);
    when(responseSpec.body(DocumentUploadResponse.class)).thenThrow(new FileConflictException("File already exists"));

    // When & Then
    assertThatExceptionOfType(FileConflictException.class)
        .isThrownBy(() -> sdsService.saveFile(applicationId, file))
        .withMessage("File already exists");
  }

  @Test
  void givenValidFile_whenSaveOrUpdateFile_thenReturnDocumentUpdateResponse() {
    // Given
    MockMultipartFile file = new MockMultipartFile("file", "test-file.pdf", "application/pdf", "test content".getBytes());
    DocumentUpdateResponse expectedResponse = mock(DocumentUpdateResponse.class);

    RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
    RestClient.RequestBodySpec requestBodySpec = mock(RestClient.RequestBodySpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.put()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(endsWith("/save_or_update_file"))).thenReturn(requestBodySpec);
    when(requestBodySpec.header("Authorization", "Bearer mock-token")).thenReturn(requestBodySpec);
    when(requestBodySpec.contentType(MediaType.MULTIPART_FORM_DATA)).thenReturn(requestBodySpec);
    when(requestBodySpec.body(any(MultiValueMap.class))).thenReturn(requestBodySpec);
    when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(DocumentUpdateResponse.class)).thenReturn(expectedResponse);

    // When
    DocumentUpdateResponse actualResponse = sdsService.saveOrUpdateFile(file);

    // Then
    assertThat(actualResponse).isEqualTo(expectedResponse);
    verify(tokenService).getSdsAccessToken();
    verify(restClient).put();
    verify(requestBodyUriSpec).uri(endsWith("save_or_update_file"));
    verify(requestBodySpec).header("Authorization", "Bearer mock-token");
  }

  @Test
  void givenValidApplicationIdAndDocumentId_whenGetFile_thenReturnDocumentDownloadResponse() {
    // Given
    UUID applicationId = UUID.randomUUID();
    String documentId = "test-file.pdf";
    DocumentDownloadResponse expectedResponse = mock(DocumentDownloadResponse.class);

    RestClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
    RestClient.RequestHeadersSpec requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.get()).thenReturn(requestHeadersUriSpec);
    when(requestHeadersUriSpec.uri(contains("/get_file?file_key="))).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.header("Authorization", "Bearer mock-token")).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.accept(MediaType.APPLICATION_JSON)).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(Predicate.class), any(RestClient.ResponseSpec.ErrorHandler.class))).thenReturn(responseSpec);
    when(responseSpec.body(DocumentDownloadResponse.class)).thenReturn(expectedResponse);

    // When
    DocumentDownloadResponse actualResponse = sdsService.getFile(applicationId, documentId);

    // Then
    assertThat(actualResponse).isEqualTo(expectedResponse);
    verify(tokenService).getSdsAccessToken();
    verify(restClient).get();
    verify(requestHeadersUriSpec).uri(endsWith("get_file?file_key=" + applicationId + "/" + documentId));
    verify(requestHeadersSpec).header("Authorization", "Bearer mock-token");
  }

  @Test
  void givenFileNotFound_whenGetFile_thenThrowResourceNotFoundException() {
    // Given
    UUID applicationId = UUID.randomUUID();
    String documentId = "test-file.pdf";

    RestClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
    RestClient.RequestHeadersSpec requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.get()).thenReturn(requestHeadersUriSpec);
    when(requestHeadersUriSpec.uri(contains("/get_file?file_key="))).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.header("Authorization", "Bearer mock-token")).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.accept(MediaType.APPLICATION_JSON)).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(Predicate.class), any(RestClient.ResponseSpec.ErrorHandler.class))).thenReturn(responseSpec);
    when(responseSpec.body(DocumentDownloadResponse.class))
        .thenThrow(new ResourceNotFoundException("File not found"));

    // When & Then
    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> sdsService.getFile(applicationId, documentId))
        .withMessage("File not found");
  }

  @Test
  void givenValidApplicationIdAndFileIds_whenDeleteFiles_thenDeleteSuccessfully() {
    // Given
    UUID applicationId = UUID.randomUUID();
    List<String> fileIds = List.of("file-1", "file-2");

    RestClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
    RestClient.RequestHeadersSpec requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.delete()).thenReturn(requestHeadersUriSpec);
    when(requestHeadersUriSpec.uri(contains("/delete_files?file_keys="))).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.header("Authorization", "Bearer mock-token")).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(Predicate.class), any(RestClient.ResponseSpec.ErrorHandler.class))).thenReturn(responseSpec);

    // When
    sdsService.deleteFiles(applicationId, fileIds);

    // Then
    verify(tokenService).getSdsAccessToken();
    verify(restClient).delete();
    verify(requestHeadersUriSpec).uri(endsWith("delete_files?file_keys="
        + applicationId + "/file-1&file_keys=" + applicationId + "/file-2"));
    verify(requestHeadersSpec).header("Authorization", "Bearer mock-token");
  }

  @Test
  void givenFileNotFound_whenDeleteFiles_thenThrowResourceNotFoundException() {
    // Given
    UUID applicationId = UUID.randomUUID();
    List<String> fileIds = List.of("file-1");

    RestClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
    RestClient.RequestHeadersSpec requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.delete()).thenReturn(requestHeadersUriSpec);
    when(requestHeadersUriSpec.uri(contains("/delete_files?file_keys="))).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.header("Authorization", "Bearer mock-token")).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(Predicate.class), any(RestClient.ResponseSpec.ErrorHandler.class))).thenReturn(responseSpec);
    when(responseSpec.toBodilessEntity()).thenThrow(new ResourceNotFoundException("File not found"));

    // When & Then
    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> sdsService.deleteFiles(applicationId, fileIds))
        .withMessage("File not found");
  }

  @Test
  void givenSdsServiceIsHealthy_whenCheckHealth_thenReturnSdsHealthResponse() {
    // Given
    SdsHealthResponse expectedResponse = mock(SdsHealthResponse.class);

    RestClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
    RestClient.RequestHeadersSpec requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(restClient.get()).thenReturn(requestHeadersUriSpec);
    when(requestHeadersUriSpec.uri(endsWith("/health"))).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.header("Authorization", "Bearer mock-token")).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.accept(MediaType.APPLICATION_JSON)).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(SdsHealthResponse.class)).thenReturn(expectedResponse);

    // When
    SdsHealthResponse actualResponse = sdsService.checkHealth();

    // Then
    assertThat(actualResponse).isEqualTo(expectedResponse);
    verify(tokenService).getSdsAccessToken();
    verify(restClient).get();
    verify(requestHeadersUriSpec).uri(endsWith("/health"));
    verify(requestHeadersSpec).header("Authorization", "Bearer mock-token");
  }
}