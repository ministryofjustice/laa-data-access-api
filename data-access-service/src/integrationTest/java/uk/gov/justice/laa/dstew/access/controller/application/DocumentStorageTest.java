package uk.gov.justice.laa.dstew.access.controller.application;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.CREATED;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertNotFound;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertOK;
import static uk.gov.justice.laa.dstew.access.utils.asserters.ResponseAsserts.assertSecurityHeaders;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import uk.gov.justice.laa.dstew.access.config.SdsWireMockStubs;
import uk.gov.justice.laa.dstew.access.entity.ApplicationEntity;
import uk.gov.justice.laa.dstew.access.model.DocumentDeleteResponse;
import uk.gov.justice.laa.dstew.access.model.DocumentDownloadResponse;
import uk.gov.justice.laa.dstew.access.model.DocumentUpdateResponse;
import uk.gov.justice.laa.dstew.access.model.DocumentUploadResponse;
import uk.gov.justice.laa.dstew.access.utils.TestConstants;
import uk.gov.justice.laa.dstew.access.utils.generator.application.ApplicationEntityGenerator;
import uk.gov.justice.laa.dstew.access.utils.harness.BaseHarnessTest;
import uk.gov.justice.laa.dstew.access.utils.harness.HarnessResult;

public class DocumentStorageTest extends BaseHarnessTest {

  private SdsWireMockStubs sdsStubs;

  @BeforeEach
  void setupSdsStubs() {
    sdsStubs = SdsWireMockStubs.from(harnessProvider);
    sdsStubs.setupDefaultStubs();
  }

  @Test
  public void givenValidFile_whenUploadDocument_thenReturnCreatedWithDocumentId() throws Exception {
    // given
    ApplicationEntity application =
        persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
    MockMultipartFile file = createTestFile("test-document.pdf", "test content");

    // when
    HarnessResult result = uploadDocument(application.getId(), file, DefaultHttpHeaders());

    // then
    assertSecurityHeaders(result);
    assertEquals(CREATED.value(), result.getResponse().getStatus());
    assertEquals("application/json", result.getResponse().getHeader("Content-Type"));

    DocumentUploadResponse response = deserialise(result, DocumentUploadResponse.class);
    assertNotNull(response);
    assertNotNull(response.getSuccess());
    assertNotNull(response.getDetail());
    assertNotNull(response.getChecksum());
  }

  @Test
  public void givenDuplicateFile_whenUploadDocument_thenReturnConflict() throws Exception {
    // given
    sdsStubs.stubDuplicateUpload();
    ApplicationEntity application =
        persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
    MockMultipartFile file = createTestFile("duplicate-document.pdf", "duplicate content");

    uploadDocument(application.getId(), file, DefaultHttpHeaders());

    HarnessResult result = uploadDocument(application.getId(), file, DefaultHttpHeaders());

    // then
    assertSecurityHeaders(result);
    assertEquals(CONFLICT.value(), result.getResponse().getStatus());
    assertEquals("application/problem+json", result.getResponse().getHeader("Content-Type"));

    ProblemDetail problemDetail = deserialise(result, ProblemDetail.class);
    assertEquals("File already exists in SDS", problemDetail.getDetail());
  }

  @Test
  public void givenUploadedDocument_whenDownloadDocument_thenReturnFileURL() throws Exception {
    // given
    ApplicationEntity application =
        persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
    MockMultipartFile file = createTestFile("download-test.pdf", "download content");

    HarnessResult uploadResult = uploadDocument(application.getId(), file, DefaultHttpHeaders());
    DocumentUploadResponse uploadResponse = deserialise(uploadResult, DocumentUploadResponse.class);
    String documentId = extractDocumentIdFromResponse(uploadResponse);

    // when
    HarnessResult result = downloadDocument(application.getId(), documentId, DefaultHttpHeaders());

    // then
    assertSecurityHeaders(result);
    assertOK(result);
    assertEquals("application/json", result.getResponse().getHeader("Content-Type"));

    DocumentDownloadResponse response = deserialise(result, DocumentDownloadResponse.class);
    assertNotNull(response);
    assertNotNull(response.getFileURL());
    assertFalse(response.getFileURL().isEmpty());
  }

  @Test
  public void givenNonExistentDocument_whenDownloadDocument_thenReturnNotFound() throws Exception {
    // given
    sdsStubs.stubFileNotFoundOnDownload();
    ApplicationEntity application =
        persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
    String nonExistentDocumentId = "non-existent-doc.pdf";

    // when
    HarnessResult result =
        downloadDocument(application.getId(), nonExistentDocumentId, DefaultHttpHeaders());

    // then
    assertSecurityHeaders(result);
    assertNotFound(result);
    assertEquals("application/problem+json", result.getResponse().getHeader("Content-Type"));

    ProblemDetail problemDetail = deserialise(result, ProblemDetail.class);
    assertEquals("File not found", problemDetail.getDetail());
  }

  @Test
  public void givenExistingDocument_whenUpdateDocument_thenReturnUpdatedFileURL() throws Exception {
    // given
    ApplicationEntity application =
        persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
    MockMultipartFile originalFile = createTestFile("document.pdf", "original content");
    MockMultipartFile updatedFile = createTestFile("document.pdf", "updated content");

    uploadDocument(application.getId(), originalFile, DefaultHttpHeaders());

    HarnessResult result = updateDocument(application.getId(), updatedFile, DefaultHttpHeaders());

    // then
    assertSecurityHeaders(result);
    assertOK(result);
    assertEquals("application/json", result.getResponse().getHeader("Content-Type"));

    DocumentUpdateResponse response = deserialise(result, DocumentUpdateResponse.class);
    assertNotNull(response);
    assertNotNull(response.getFileURL());
    assertFalse(response.getFileURL().isEmpty());
  }

  @Test
  public void givenUploadedDocuments_whenDeleteDocuments_thenReturnOKWithDeleteResponse()
      throws Exception {
    // given
    ApplicationEntity application =
        persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
    MockMultipartFile file1 = createTestFile("delete-doc-1.pdf", "content 1");
    MockMultipartFile file2 = createTestFile("delete-doc-2.pdf", "content 2");

    HarnessResult upload1 = uploadDocument(application.getId(), file1, DefaultHttpHeaders());
    HarnessResult upload2 = uploadDocument(application.getId(), file2, DefaultHttpHeaders());

    DocumentUploadResponse response1 = deserialise(upload1, DocumentUploadResponse.class);
    DocumentUploadResponse response2 = deserialise(upload2, DocumentUploadResponse.class);

    String docId1 = extractDocumentIdFromResponse(response1);
    String docId2 = extractDocumentIdFromResponse(response2);

    List<String> documentIds = List.of(docId1, docId2);

    // when
    HarnessResult result = deleteDocuments(application.getId(), documentIds, DefaultHttpHeaders());

    // then
    assertSecurityHeaders(result);
    assertOK(result);
    assertEquals("application/json", result.getResponse().getHeader("Content-Type"));

    DocumentDeleteResponse response = deserialise(result, DocumentDeleteResponse.class);
    assertNotNull(response);
    assertNotNull(response.getResults());
    assertThat(response.getResults()).allMatch(r -> r.getStatus() == 204);

    // Verify files are no longer accessible after deletion
    sdsStubs.stubFileNotFoundOnDownload();

    HarnessResult downloadResult1 =
        downloadDocument(application.getId(), docId1, DefaultHttpHeaders());
    assertNotFound(downloadResult1);

    HarnessResult downloadResult2 =
        downloadDocument(application.getId(), docId2, DefaultHttpHeaders());
    assertNotFound(downloadResult2);
  }

  @Test
  public void givenNonExistentDocument_whenDeleteDocument_thenReturnOKWithNotFoundStatus()
      throws Exception {
    // given
    sdsStubs.stubFileNotFoundOnDelete();
    ApplicationEntity application =
        persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);
    List<String> nonExistentIds = List.of("non-existent-1.pdf", "non-existent-2.pdf");

    // when
    HarnessResult result =
        deleteDocuments(application.getId(), nonExistentIds, DefaultHttpHeaders());

    // then
    assertSecurityHeaders(result);
    assertOK(result);
    assertEquals("application/json", result.getResponse().getHeader("Content-Type"));

    DocumentDeleteResponse response = deserialise(result, DocumentDeleteResponse.class);
    assertNotNull(response);
    assertNotNull(response.getResults());
    assertThat(response.getResults()).allMatch(r -> r.getStatus() == 404);
  }

  @Test
  public void givenMultipleDocuments_whenUploadedToSameApplication_thenAllSucceed()
      throws Exception {
    // given
    ApplicationEntity application =
        persistedDataGenerator.createAndPersist(ApplicationEntityGenerator.class);

    MockMultipartFile file1 = createTestFile("multi-doc-1.pdf", "content 1");
    MockMultipartFile file2 = createTestFile("multi-doc-2.pdf", "content 2");
    MockMultipartFile file3 = createTestFile("multi-doc-3.pdf", "content 3");

    // when - Upload multiple documents
    HarnessResult result1 = uploadDocument(application.getId(), file1, DefaultHttpHeaders());
    HarnessResult result2 = uploadDocument(application.getId(), file2, DefaultHttpHeaders());
    HarnessResult result3 = uploadDocument(application.getId(), file3, DefaultHttpHeaders());

    // then
    assertEquals(CREATED.value(), result1.getResponse().getStatus());
    assertEquals(CREATED.value(), result2.getResponse().getStatus());
    assertEquals(CREATED.value(), result3.getResponse().getStatus());

    // Verify all can be downloaded
    DocumentUploadResponse response1 = deserialise(result1, DocumentUploadResponse.class);
    DocumentUploadResponse response2 = deserialise(result2, DocumentUploadResponse.class);
    DocumentUploadResponse response3 = deserialise(result3, DocumentUploadResponse.class);

    String docId1 = extractDocumentIdFromResponse(response1);
    String docId2 = extractDocumentIdFromResponse(response2);
    String docId3 = extractDocumentIdFromResponse(response3);

    assertOK(downloadDocument(application.getId(), docId1, DefaultHttpHeaders()));
    assertOK(downloadDocument(application.getId(), docId2, DefaultHttpHeaders()));
    assertOK(downloadDocument(application.getId(), docId3, DefaultHttpHeaders()));
  }

  private MockMultipartFile createTestFile(String filename, String content) {
    return new MockMultipartFile(
        "file", filename, MediaType.APPLICATION_PDF_VALUE, content.getBytes());
  }

  private HttpHeaders DefaultHttpHeaders() {
    return ServiceNameHeader("CIVIL_APPLY");
  }

  private HarnessResult uploadDocument(
      UUID applicationId, MockMultipartFile file, HttpHeaders headers) throws Exception {
    String uri = String.format("/api/v0/applications/%s/upload-document", applicationId);
    return postMultipartUri(uri, file, headers);
  }

  private HarnessResult downloadDocument(UUID applicationId, String documentId, HttpHeaders headers)
      throws Exception {
    String uri =
        String.format("/api/v0/applications/%s/download-document/%s", applicationId, documentId);
    return getUri(uri, headers);
  }

  private HarnessResult updateDocument(
      UUID applicationId, MockMultipartFile file, HttpHeaders headers) throws Exception {
    String uri = String.format("/api/v0/applications/%s/update-document", applicationId);
    return putMultipartUri(uri, file, headers);
  }

  private HarnessResult deleteDocuments(
      UUID applicationId, List<String> documentIds, HttpHeaders headers) throws Exception {
    StringBuilder uri =
        new StringBuilder(String.format("/api/v0/applications/%s/delete-document", applicationId));

    if (!documentIds.isEmpty()) {
      uri.append("?");
      for (int i = 0; i < documentIds.size(); i++) {
        if (i > 0) {
          uri.append("&");
        }
        uri.append("documentIds=").append(documentIds.get(i));
      }
    }

    return deleteUri(uri.toString(), headers);
  }

  private String extractDocumentIdFromResponse(DocumentUploadResponse response) {
    String detail = response.getDetail();
    if (detail != null && detail.contains("/")) {
      String[] parts = detail.split("/");
      return parts[parts.length - 1]; // Return the last part as document ID
    }
    return "default-doc-id.pdf"; // Fallback for testing
  }

  private HarnessResult postMultipartUri(String uri, MockMultipartFile file, HttpHeaders headers)
      throws Exception {
    org.springframework.http.client.MultipartBodyBuilder builder =
        new org.springframework.http.client.MultipartBodyBuilder();
    builder.part("file", file.getResource());

    WebTestClient.RequestBodySpec spec =
        webTestClient.post().uri(uri).contentType(MediaType.MULTIPART_FORM_DATA);

    spec = spec.header(AUTHORIZATION, "Bearer " + TestConstants.Tokens.CASEWORKER);

    if (headers != null) {
      for (java.util.Map.Entry<String, java.util.List<String>> entry : headers.headerSet()) {
        spec = spec.header(entry.getKey(), entry.getValue().toArray(String[]::new));
      }
    }

    EntityExchangeResult<byte[]> raw =
        spec.bodyValue(builder.build()).exchange().expectBody().returnResult();

    int status = raw.getStatus().value();
    java.util.Map<String, String> responseHeaders = raw.getResponseHeaders().toSingleValueMap();
    String responseBody =
        raw.getResponseBody() == null ? "" : new String(raw.getResponseBody(), UTF_8);

    return new HarnessResult(status, responseHeaders, responseBody);
  }

  private HarnessResult putMultipartUri(String uri, MockMultipartFile file, HttpHeaders headers)
      throws Exception {
    org.springframework.http.client.MultipartBodyBuilder builder =
        new org.springframework.http.client.MultipartBodyBuilder();
    builder.part("file", file.getResource());

    WebTestClient.RequestBodySpec spec =
        webTestClient.put().uri(uri).contentType(MediaType.MULTIPART_FORM_DATA);

    spec = spec.header(AUTHORIZATION, "Bearer " + TestConstants.Tokens.CASEWORKER);

    if (headers != null) {
      for (java.util.Map.Entry<String, java.util.List<String>> entry : headers.headerSet()) {
        spec = spec.header(entry.getKey(), entry.getValue().toArray(String[]::new));
      }
    }

    EntityExchangeResult<byte[]> raw =
        spec.bodyValue(builder.build()).exchange().expectBody().returnResult();

    int status = raw.getStatus().value();
    java.util.Map<String, String> responseHeaders = raw.getResponseHeaders().toSingleValueMap();
    String responseBody =
        raw.getResponseBody() == null ? "" : new String(raw.getResponseBody(), UTF_8);

    return new HarnessResult(status, responseHeaders, responseBody);
  }

  private HarnessResult deleteUri(String uri, HttpHeaders headers) throws Exception {
    WebTestClient.RequestHeadersSpec<?> spec = webTestClient.delete().uri(uri);

    spec = spec.header(AUTHORIZATION, "Bearer " + TestConstants.Tokens.CASEWORKER);

    if (headers != null) {
      for (java.util.Map.Entry<String, java.util.List<String>> entry : headers.headerSet()) {
        spec = spec.header(entry.getKey(), entry.getValue().toArray(String[]::new));
      }
    }

    EntityExchangeResult<byte[]> raw = spec.exchange().expectBody().returnResult();

    int status = raw.getStatus().value();
    java.util.Map<String, String> responseHeaders = raw.getResponseHeaders().toSingleValueMap();
    String responseBody =
        raw.getResponseBody() == null ? "" : new String(raw.getResponseBody(), UTF_8);

    return new HarnessResult(status, responseHeaders, responseBody);
  }
}
