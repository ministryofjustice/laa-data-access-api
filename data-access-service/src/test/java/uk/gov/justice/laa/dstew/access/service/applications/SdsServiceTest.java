package uk.gov.justice.laa.dstew.access.service.applications;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import uk.gov.justice.laa.dstew.access.exception.FileConflictException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.DocumentDownloadResponse;
import uk.gov.justice.laa.dstew.access.model.DocumentUpdateResponse;
import uk.gov.justice.laa.dstew.access.model.DocumentUploadResponse;
import uk.gov.justice.laa.dstew.access.model.SdsHealthResponse;
import uk.gov.justice.laa.dstew.access.service.sds.TokenService;

@ExtendWith(MockitoExtension.class)
class SdsServiceTest {

  @Mock private RestClient sdsRestClient;

  @Mock private TokenService tokenService;

  @InjectMocks private SdsService sdsService;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(sdsService, "bucketName", "test-bucket");
    lenient().when(tokenService.getSdsAccessToken()).thenReturn("mock-token");
  }

  @Test
  void givenValidFileAndApplicationId_whenSaveFile_thenReturnDocumentUploadResponse() {
    // Given
    UUID applicationId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "test-file.pdf", "application/pdf", "test content".getBytes());
    DocumentUploadResponse expectedResponse = mock(DocumentUploadResponse.class);

    RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
    RestClient.RequestBodySpec requestBodySpec = mock(RestClient.RequestBodySpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(sdsRestClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(endsWith("/save_file"))).thenReturn(requestBodySpec);
    when(requestBodySpec.headers(any()))
        .thenAnswer(
            invocation -> {
              Consumer<HttpHeaders> headersConsumer = invocation.getArgument(0);
              headersConsumer.accept(new HttpHeaders());
              return requestBodySpec;
            });
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
    verify(sdsRestClient).post();
    verify(requestBodyUriSpec).uri(endsWith("/save_file"));
  }

  @Test
  void givenFileAlreadyExists_whenSaveFile_thenThrowFileConflictException() {
    // Given
    UUID applicationId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "test-file.pdf", "application/pdf", "test content".getBytes());

    RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
    RestClient.RequestBodySpec requestBodySpec = mock(RestClient.RequestBodySpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(sdsRestClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(endsWith("/save_file"))).thenReturn(requestBodySpec);
    when(requestBodySpec.headers(any()))
        .thenAnswer(
            invocation -> {
              Consumer<HttpHeaders> headersConsumer = invocation.getArgument(0);
              headersConsumer.accept(new HttpHeaders());
              return requestBodySpec;
            });
    when(requestBodySpec.contentType(MediaType.MULTIPART_FORM_DATA)).thenReturn(requestBodySpec);
    when(requestBodySpec.body(any(MultiValueMap.class))).thenReturn(requestBodySpec);
    when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(Predicate.class), any())).thenReturn(responseSpec);
    when(responseSpec.body(DocumentUploadResponse.class))
        .thenThrow(new FileConflictException("File already exists"));

    // When & Then
    assertThatExceptionOfType(FileConflictException.class)
        .isThrownBy(() -> sdsService.saveFile(applicationId, file))
        .withMessage("File already exists");
  }

  @Test
  void givenValidFile_whenSaveOrUpdateFile_thenReturnDocumentUpdateResponse() {
    // Given
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "test-file.pdf", "application/pdf", "test content".getBytes());
    DocumentUpdateResponse expectedResponse = mock(DocumentUpdateResponse.class);

    RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
    RestClient.RequestBodySpec requestBodySpec = mock(RestClient.RequestBodySpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(sdsRestClient.put()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(endsWith("/save_or_update_file"))).thenReturn(requestBodySpec);
    when(requestBodySpec.headers(any()))
        .thenAnswer(
            invocation -> {
              Consumer<HttpHeaders> headersConsumer = invocation.getArgument(0);
              headersConsumer.accept(new HttpHeaders());
              return requestBodySpec;
            });
    when(requestBodySpec.contentType(MediaType.MULTIPART_FORM_DATA)).thenReturn(requestBodySpec);
    when(requestBodySpec.body(any(MultiValueMap.class))).thenReturn(requestBodySpec);
    when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(Predicate.class), any())).thenReturn(responseSpec);
    when(responseSpec.body(DocumentUpdateResponse.class)).thenReturn(expectedResponse);

    // When
    DocumentUpdateResponse actualResponse = sdsService.saveOrUpdateFile(file);

    // Then
    assertThat(actualResponse).isEqualTo(expectedResponse);
    verify(tokenService).getSdsAccessToken();
    verify(sdsRestClient).put();
    verify(requestBodyUriSpec).uri(endsWith("save_or_update_file"));
  }

  @Test
  void givenValidApplicationIdAndDocumentId_whenGetFile_thenReturnDocumentDownloadResponse() {
    // Given
    UUID applicationId = UUID.randomUUID();
    String documentId = "test-file.pdf";
    DocumentDownloadResponse expectedResponse = mock(DocumentDownloadResponse.class);

    RestClient.RequestHeadersUriSpec requestHeadersUriSpec =
        mock(RestClient.RequestHeadersUriSpec.class);
    RestClient.RequestHeadersSpec requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(sdsRestClient.get()).thenReturn(requestHeadersUriSpec);
    when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.headers(any()))
        .thenAnswer(
            invocation -> {
              Consumer<HttpHeaders> headersConsumer = invocation.getArgument(0);
              headersConsumer.accept(new HttpHeaders());
              return requestHeadersSpec;
            });
    when(requestHeadersSpec.accept(MediaType.APPLICATION_JSON)).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(
            any(Predicate.class), any(RestClient.ResponseSpec.ErrorHandler.class)))
        .thenReturn(responseSpec);
    when(responseSpec.body(DocumentDownloadResponse.class)).thenReturn(expectedResponse);

    // When
    DocumentDownloadResponse actualResponse = sdsService.getFile(applicationId, documentId);

    // Then
    assertThat(actualResponse).isEqualTo(expectedResponse);
    verify(tokenService).getSdsAccessToken();
    verify(sdsRestClient).get();
    verify(requestHeadersUriSpec).uri(any(Function.class));
  }

  @Test
  void givenFileNotFound_whenGetFile_thenThrowResourceNotFoundException() {
    // Given
    UUID applicationId = UUID.randomUUID();
    String documentId = "test-file.pdf";

    RestClient.RequestHeadersUriSpec requestHeadersUriSpec =
        mock(RestClient.RequestHeadersUriSpec.class);
    RestClient.RequestHeadersSpec requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(sdsRestClient.get()).thenReturn(requestHeadersUriSpec);
    when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.headers(any()))
        .thenAnswer(
            invocation -> {
              Consumer<HttpHeaders> headersConsumer = invocation.getArgument(0);
              headersConsumer.accept(new HttpHeaders());
              return requestHeadersSpec;
            });
    when(requestHeadersSpec.accept(MediaType.APPLICATION_JSON)).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(
            any(Predicate.class), any(RestClient.ResponseSpec.ErrorHandler.class)))
        .thenReturn(responseSpec);
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

    RestClient.RequestHeadersUriSpec requestHeadersUriSpec =
        mock(RestClient.RequestHeadersUriSpec.class);
    RestClient.RequestHeadersSpec requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(sdsRestClient.delete()).thenReturn(requestHeadersUriSpec);
    when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.headers(any()))
        .thenAnswer(
            invocation -> {
              Consumer<HttpHeaders> headersConsumer = invocation.getArgument(0);
              headersConsumer.accept(new HttpHeaders());
              return requestHeadersSpec;
            });
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(
            any(Predicate.class), any(RestClient.ResponseSpec.ErrorHandler.class)))
        .thenReturn(responseSpec);
    when(responseSpec.toBodilessEntity()).thenReturn(null);

    // When
    sdsService.deleteFiles(applicationId, fileIds);

    // Then
    verify(tokenService).getSdsAccessToken();
    verify(sdsRestClient).delete();
    verify(requestHeadersUriSpec).uri(any(Function.class));
  }

  @Test
  void givenFileNotFound_whenDeleteFiles_thenThrowResourceNotFoundException() {
    // Given
    UUID applicationId = UUID.randomUUID();
    List<String> fileIds = List.of("file-1");

    RestClient.RequestHeadersUriSpec requestHeadersUriSpec =
        mock(RestClient.RequestHeadersUriSpec.class);
    RestClient.RequestHeadersSpec requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(sdsRestClient.delete()).thenReturn(requestHeadersUriSpec);
    when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.headers(any()))
        .thenAnswer(
            invocation -> {
              Consumer<HttpHeaders> headersConsumer = invocation.getArgument(0);
              headersConsumer.accept(new HttpHeaders());
              return requestHeadersSpec;
            });
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(
            any(Predicate.class), any(RestClient.ResponseSpec.ErrorHandler.class)))
        .thenReturn(responseSpec);
    when(responseSpec.toBodilessEntity())
        .thenThrow(new ResourceNotFoundException("File not found"));

    // When & Then
    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> sdsService.deleteFiles(applicationId, fileIds))
        .withMessage("File not found");
  }

  @Test
  void givenSdsServiceIsHealthy_whenCheckHealth_thenReturnSdsHealthResponse() {
    // Given
    SdsHealthResponse expectedResponse = mock(SdsHealthResponse.class);

    RestClient.RequestHeadersUriSpec requestHeadersUriSpec =
        mock(RestClient.RequestHeadersUriSpec.class);
    RestClient.RequestHeadersSpec requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(sdsRestClient.get()).thenReturn(requestHeadersUriSpec);
    when(requestHeadersUriSpec.uri(endsWith("/health"))).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.headers(any()))
        .thenAnswer(
            invocation -> {
              Consumer<HttpHeaders> headersConsumer = invocation.getArgument(0);
              headersConsumer.accept(new HttpHeaders());
              return requestHeadersSpec;
            });
    when(requestHeadersSpec.accept(MediaType.APPLICATION_JSON)).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(SdsHealthResponse.class)).thenReturn(expectedResponse);

    // When
    SdsHealthResponse actualResponse = sdsService.getHealth();

    // Then
    assertThat(actualResponse).isEqualTo(expectedResponse);
    verify(tokenService).getSdsAccessToken();
    verify(sdsRestClient).get();
    verify(requestHeadersUriSpec).uri(endsWith("/health"));
  }
}
