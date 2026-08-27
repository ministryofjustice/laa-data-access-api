package uk.gov.justice.laa.dstew.access.service.sds;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.function.Consumer;
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
import uk.gov.justice.laa.dstew.access.exception.FileLengthRequiredException;
import uk.gov.justice.laa.dstew.access.exception.VirusDetectedException;
import uk.gov.justice.laa.dstew.access.exception.VirusScanException;
import uk.gov.justice.laa.dstew.access.model.DocumentUploadResponse;

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

    DocumentUploadResponse actualResponse = sdsService.saveFile(applicationId, file);

    assertThat(actualResponse).isEqualTo(expectedResponse);
    verify(tokenService).getSdsAccessToken();
    verify(sdsRestClient).post();
    verify(requestBodyUriSpec).uri(endsWith("/save_file"));
  }

  @Test
  void givenFileAlreadyExists_whenSaveFile_thenThrowFileConflictException() {
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

    assertThatExceptionOfType(FileConflictException.class)
        .isThrownBy(() -> sdsService.saveFile(applicationId, file))
        .withMessage("File already exists");
  }

  @Test
  void givenSdsReturnsLengthRequired_whenSaveFile_thenThrowFileLengthRequiredException() {
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
        .thenThrow(new FileLengthRequiredException("File content length is required"));

    assertThatExceptionOfType(FileLengthRequiredException.class)
        .isThrownBy(() -> sdsService.saveFile(applicationId, file))
        .withMessage("File content length is required");
  }

  @Test
  void givenSdsReturnsVirusDetected_whenSaveFile_thenThrowVirusDetectedException() {
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
        .thenThrow(new VirusDetectedException("Virus detected in uploaded file"));

    assertThatExceptionOfType(VirusDetectedException.class)
        .isThrownBy(() -> sdsService.saveFile(applicationId, file))
        .withMessage("Virus detected in uploaded file");
  }

  @Test
  void givenSdsReturnsVirusScanError_whenSaveFile_thenThrowVirusScanException() {
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
        .thenThrow(new VirusScanException("Virus scan gave a non-standard result"));

    assertThatExceptionOfType(VirusScanException.class)
        .isThrownBy(() -> sdsService.saveFile(applicationId, file))
        .withMessage("Virus scan gave a non-standard result");
  }
}
