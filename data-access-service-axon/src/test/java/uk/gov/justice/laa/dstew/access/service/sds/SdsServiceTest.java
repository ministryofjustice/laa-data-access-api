package uk.gov.justice.laa.dstew.access.service.sds;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import uk.gov.justice.laa.dstew.access.exception.FileConflictException;
import uk.gov.justice.laa.dstew.access.exception.FileLengthRequiredException;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.exception.VirusDetectedException;
import uk.gov.justice.laa.dstew.access.exception.VirusScanException;
import uk.gov.justice.laa.dstew.access.model.DocumentDeleteResponse;
import uk.gov.justice.laa.dstew.access.model.DocumentDownloadResponse;
import uk.gov.justice.laa.dstew.access.model.DocumentUpdateResponse;
import uk.gov.justice.laa.dstew.access.model.DocumentUploadResponse;
import uk.gov.justice.laa.dstew.access.model.SdsHealthResponse;

@ExtendWith(MockitoExtension.class)
class SdsServiceTest {

  @Mock private RestClient sdsRestClient;

  @InjectMocks private SdsService sdsService;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(sdsService, "bucketName", "test-bucket");
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
    when(requestBodySpec.contentType(MediaType.MULTIPART_FORM_DATA)).thenReturn(requestBodySpec);
    when(requestBodySpec.body(any(MultiValueMap.class))).thenReturn(requestBodySpec);
    when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(Predicate.class), any())).thenReturn(responseSpec);
    when(responseSpec.body(DocumentUploadResponse.class)).thenReturn(expectedResponse);

    DocumentUploadResponse actualResponse = sdsService.saveFile(applicationId, file);

    assertThat(actualResponse).isEqualTo(expectedResponse);
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

  @Test
  void givenValidFile_whenSaveOrUpdateFile_thenReturnDocumentUpdateResponse() {
    UUID applicationId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "test-file.pdf", "application/pdf", "test content".getBytes());
    DocumentUpdateResponse expectedResponse = mock(DocumentUpdateResponse.class);

    RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
    RestClient.RequestBodySpec requestBodySpec = mock(RestClient.RequestBodySpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(sdsRestClient.put()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(endsWith("/save_or_update_file"))).thenReturn(requestBodySpec);
    when(requestBodySpec.contentType(MediaType.MULTIPART_FORM_DATA)).thenReturn(requestBodySpec);
    when(requestBodySpec.body(any(MultiValueMap.class))).thenReturn(requestBodySpec);
    when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(any(Predicate.class), any())).thenReturn(responseSpec);
    when(responseSpec.body(DocumentUpdateResponse.class)).thenReturn(expectedResponse);

    DocumentUpdateResponse actualResponse = sdsService.saveOrUpdateFile(applicationId, file);

    assertThat(actualResponse).isEqualTo(expectedResponse);
    verify(sdsRestClient).put();
    verify(requestBodyUriSpec).uri(endsWith("/save_or_update_file"));
  }

  @Test
  void givenValidApplicationIdAndDocumentId_whenGetFile_thenReturnDocumentDownloadResponse() {
    UUID applicationId = UUID.randomUUID();
    String documentId = "test-file.pdf";
    DocumentDownloadResponse expectedResponse = mock(DocumentDownloadResponse.class);

    RestClient.RequestHeadersUriSpec requestHeadersUriSpec =
        mock(RestClient.RequestHeadersUriSpec.class);
    RestClient.RequestHeadersSpec requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(sdsRestClient.get()).thenReturn(requestHeadersUriSpec);
    when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.accept(MediaType.APPLICATION_JSON)).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(
            any(Predicate.class), any(RestClient.ResponseSpec.ErrorHandler.class)))
        .thenReturn(responseSpec);
    when(responseSpec.body(DocumentDownloadResponse.class)).thenReturn(expectedResponse);

    DocumentDownloadResponse actualResponse = sdsService.getFile(applicationId, documentId);

    assertThat(actualResponse).isEqualTo(expectedResponse);
    verify(sdsRestClient).get();
  }

  @Test
  void givenFileNotFound_whenGetFile_thenThrowResourceNotFoundException() {
    UUID applicationId = UUID.randomUUID();
    String documentId = "test-file.pdf";

    RestClient.RequestHeadersUriSpec requestHeadersUriSpec =
        mock(RestClient.RequestHeadersUriSpec.class);
    RestClient.RequestHeadersSpec requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(sdsRestClient.get()).thenReturn(requestHeadersUriSpec);
    when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.accept(MediaType.APPLICATION_JSON)).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(
            any(Predicate.class), any(RestClient.ResponseSpec.ErrorHandler.class)))
        .thenReturn(responseSpec);
    when(responseSpec.body(DocumentDownloadResponse.class))
        .thenThrow(new ResourceNotFoundException("File not found"));

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> sdsService.getFile(applicationId, documentId))
        .withMessage("File not found");
  }

  @Test
  void givenValidApplicationIdAndFileIds_whenDeleteFiles_thenReturnDeleteResponse() {
    UUID applicationId = UUID.randomUUID();
    List<String> fileIds = List.of("file-1.pdf", "file-2.pdf");
    Map<String, Integer> sdsResults =
        Map.of(
            applicationId + "/file-1.pdf", 204,
            applicationId + "/file-2.pdf", 204);

    RestClient.RequestHeadersUriSpec requestHeadersUriSpec =
        mock(RestClient.RequestHeadersUriSpec.class);
    RestClient.RequestHeadersSpec requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(sdsRestClient.delete()).thenReturn(requestHeadersUriSpec);
    when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(sdsResults);

    DocumentDeleteResponse response = sdsService.deleteFiles(applicationId, fileIds);

    assertThat(response.getResults()).isNotNull();
    assertThat(response.getResults().size()).isEqualTo(2);
    assertThat(response.getResults().stream().allMatch(r -> r.getStatus() == 204)).isTrue();
    verify(sdsRestClient).delete();
  }

  @Test
  void givenSdsReturnsNullBody_whenDeleteFiles_thenReturnEmptyResults() {
    UUID applicationId = UUID.randomUUID();
    List<String> fileIds = List.of("file-1.pdf", "file-2.pdf");

    RestClient.RequestHeadersUriSpec requestHeadersUriSpec =
        mock(RestClient.RequestHeadersUriSpec.class);
    RestClient.RequestHeadersSpec requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(sdsRestClient.delete()).thenReturn(requestHeadersUriSpec);
    when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(any(ParameterizedTypeReference.class))).thenReturn(null);

    DocumentDeleteResponse response = sdsService.deleteFiles(applicationId, fileIds);

    assertThat(response.getResults()).isNotNull();
    assertThat(response.getResults().isEmpty()).isTrue();
  }

  @Test
  void givenSdsServiceIsHealthy_whenGetHealth_thenReturnSdsHealthResponse() {
    SdsHealthResponse expectedResponse = mock(SdsHealthResponse.class);

    RestClient.RequestHeadersUriSpec requestHeadersUriSpec =
        mock(RestClient.RequestHeadersUriSpec.class);
    RestClient.RequestHeadersSpec requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    when(sdsRestClient.get()).thenReturn(requestHeadersUriSpec);
    when(requestHeadersUriSpec.uri(endsWith("/health"))).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.accept(MediaType.APPLICATION_JSON)).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.body(SdsHealthResponse.class)).thenReturn(expectedResponse);

    SdsHealthResponse actualResponse = sdsService.getHealth();

    assertThat(actualResponse).isEqualTo(expectedResponse);
    verify(sdsRestClient).get();
    verify(requestHeadersUriSpec).uri(endsWith("/health"));
  }

  @SuppressWarnings("unchecked")
  @Test
  void givenGetFileNotFoundPredicate_whenInvoked_thenMatchesExpectedStatusesAndHandlerThrows()
      throws Exception {
    UUID applicationId = UUID.randomUUID();
    String documentId = "test-file.pdf";

    RestClient.RequestHeadersUriSpec requestHeadersUriSpec =
        mock(RestClient.RequestHeadersUriSpec.class);
    RestClient.RequestHeadersSpec requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

    ArgumentCaptor<Predicate<HttpStatusCode>> predicateCaptor =
        ArgumentCaptor.forClass(Predicate.class);
    ArgumentCaptor<RestClient.ResponseSpec.ErrorHandler> handlerCaptor =
        ArgumentCaptor.forClass(RestClient.ResponseSpec.ErrorHandler.class);

    when(sdsRestClient.get()).thenReturn(requestHeadersUriSpec);
    when(requestHeadersUriSpec.uri(any(Function.class))).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.accept(MediaType.APPLICATION_JSON)).thenReturn(requestHeadersSpec);
    when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(predicateCaptor.capture(), handlerCaptor.capture()))
        .thenReturn(responseSpec);
    when(responseSpec.body(DocumentDownloadResponse.class))
        .thenReturn(mock(DocumentDownloadResponse.class));

    sdsService.getFile(applicationId, documentId);

    Predicate<HttpStatusCode> notFoundPredicate = predicateCaptor.getValue();
    assertThat(notFoundPredicate.test(HttpStatus.NOT_FOUND)).isTrue();
    assertThat(notFoundPredicate.test(HttpStatus.OK)).isFalse();

    assertThatExceptionOfType(ResourceNotFoundException.class)
        .isThrownBy(() -> handlerCaptor.getValue().handle(null, null));
  }

  @SuppressWarnings("unchecked")
  @Test
  void
      givenHandleUploadErrorsPredicates_whenInvoked_thenMatchExpectedStatusCodesAndThrowExpectedExceptions()
          throws Exception {
    UUID applicationId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());

    RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
    RestClient.RequestBodySpec requestBodySpec = mock(RestClient.RequestBodySpec.class);
    RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
    DocumentUploadResponse expectedResponse = mock(DocumentUploadResponse.class);

    ArgumentCaptor<Predicate<HttpStatusCode>> predicateCaptor =
        ArgumentCaptor.forClass(Predicate.class);
    ArgumentCaptor<RestClient.ResponseSpec.ErrorHandler> handlerCaptor =
        ArgumentCaptor.forClass(RestClient.ResponseSpec.ErrorHandler.class);

    when(sdsRestClient.post()).thenReturn(requestBodyUriSpec);
    when(requestBodyUriSpec.uri(endsWith("/save_file"))).thenReturn(requestBodySpec);
    when(requestBodySpec.contentType(MediaType.MULTIPART_FORM_DATA)).thenReturn(requestBodySpec);
    when(requestBodySpec.body(any(MultiValueMap.class))).thenReturn(requestBodySpec);
    when(requestBodySpec.retrieve()).thenReturn(responseSpec);
    when(responseSpec.onStatus(predicateCaptor.capture(), handlerCaptor.capture()))
        .thenReturn(responseSpec);
    when(responseSpec.body(DocumentUploadResponse.class)).thenReturn(expectedResponse);

    sdsService.saveFile(applicationId, file);

    List<Predicate<HttpStatusCode>> predicates = predicateCaptor.getAllValues();
    List<RestClient.ResponseSpec.ErrorHandler> handlers = handlerCaptor.getAllValues();

    // Predicate 0 (saveFile): CONFLICT (409)
    assertThat(predicates.get(0).test(HttpStatus.CONFLICT)).isTrue();
    assertThat(predicates.get(0).test(HttpStatus.OK)).isFalse();

    // Predicate 1 (handleUploadErrors): LENGTH_REQUIRED (411)
    assertThat(predicates.get(1).test(HttpStatus.LENGTH_REQUIRED)).isTrue();
    assertThat(predicates.get(1).test(HttpStatus.OK)).isFalse();

    // Predicate 2 (handleUploadErrors): 422
    assertThat(predicates.get(2).test(HttpStatus.UNPROCESSABLE_ENTITY)).isTrue();
    assertThat(predicates.get(2).test(HttpStatus.OK)).isFalse();

    // Predicate 3 (handleUploadErrors): catch-all — not 2xx, not 409, not 411, not 422
    assertThat(predicates.get(3).test(HttpStatus.INTERNAL_SERVER_ERROR)).isTrue();
    assertThat(predicates.get(3).test(HttpStatus.OK)).isFalse(); // 2xx → false
    assertThat(predicates.get(3).test(HttpStatus.CONFLICT)).isFalse(); // 409 → false
    assertThat(predicates.get(3).test(HttpStatus.LENGTH_REQUIRED)).isFalse(); // 411 → false
    assertThat(predicates.get(3).test(HttpStatus.UNPROCESSABLE_ENTITY)).isFalse(); // 422 → false

    // Handler 0: FileConflictException
    assertThatExceptionOfType(FileConflictException.class)
        .isThrownBy(() -> handlers.get(0).handle(null, null));

    // Handler 1: FileLengthRequiredException
    assertThatExceptionOfType(FileLengthRequiredException.class)
        .isThrownBy(() -> handlers.get(1).handle(null, null));

    // Handler 2: VirusDetectedException
    assertThatExceptionOfType(VirusDetectedException.class)
        .isThrownBy(() -> handlers.get(2).handle(null, null));

    // Handler 3: VirusScanException (uses res.getStatusCode() — need a real mock)
    ClientHttpResponse mockClientResponse = mock(ClientHttpResponse.class);
    doReturn(HttpStatus.INTERNAL_SERVER_ERROR).when(mockClientResponse).getStatusCode();
    assertThatExceptionOfType(VirusScanException.class)
        .isThrownBy(() -> handlers.get(3).handle(null, mockClientResponse));
  }
}
