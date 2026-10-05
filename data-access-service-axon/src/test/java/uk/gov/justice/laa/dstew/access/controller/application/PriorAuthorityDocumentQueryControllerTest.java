package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.access.document.DocumentDetails;
import uk.gov.justice.laa.dstew.access.document.DocumentDownload;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.DownloadPriorAuthorityDocumentUseCase;

/** Verifies that the document controller delegates and maps download metadata. */
@ExtendWith(MockitoExtension.class)
class PriorAuthorityDocumentQueryControllerTest {

  @Mock private DownloadPriorAuthorityDocumentUseCase downloadPriorAuthorityDocumentUseCase;

  @InjectMocks private PriorAuthorityDocumentQueryController controller;

  @Test
  void givenOwnedDocument_whenDownloaded_thenUsesOriginalFilenameAndMetadata() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    DocumentDetails document = document(documentId, "application/pdf", 123L);
    Resource resource = mock(Resource.class);
    when(downloadPriorAuthorityDocumentUseCase.downloadDocument(priorAuthorityId, documentId))
        .thenReturn(new DocumentDownload(document, resource));

    ResponseEntity<Resource> response =
        controller.downloadPriorAuthorityDocument(null, priorAuthorityId, documentId);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isSameAs(resource);
    assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/pdf");
    assertThat(response.getHeaders().getContentLength()).isEqualTo(123L);
    assertThat(response.getHeaders().getFirst("Content-Disposition"))
        .contains("attachment")
        .contains("original evidence.pdf");
    assertThat(response.getHeaders().getFirst("X-Document-Type")).isNull();
    verify(downloadPriorAuthorityDocumentUseCase).downloadDocument(priorAuthorityId, documentId);
  }

  @Test
  void givenDocumentMetadata_whenDownloaded_thenReturnsMetadataHeaders() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Instant uploadedAt = Instant.parse("2026-09-08T12:00:00Z");
    DocumentDetails document =
        new DocumentDetails(
            documentId,
            "GATEWAY_EVIDENCE",
            "evidence.pdf",
            "PDF",
            "application/pdf",
            123L,
            uploadedAt,
            "CIVIL_APPLY",
            "checksum");
    Resource resource = mock(Resource.class);
    when(downloadPriorAuthorityDocumentUseCase.downloadDocument(priorAuthorityId, documentId))
        .thenReturn(new DocumentDownload(document, resource));

    ResponseEntity<Resource> response =
        controller.downloadPriorAuthorityDocument(null, priorAuthorityId, documentId);

    assertThat(response.getHeaders().getFirst("X-Document-Uploaded-At"))
        .isEqualTo("2026-09-08T12:00:00Z");
    assertThat(response.getHeaders().getFirst("X-Document-Type")).isEqualTo("GATEWAY_EVIDENCE");
  }

  @Test
  void givenDocumentWithoutMediaTypeOrSize_whenDownloaded_thenUsesDefaultMediaType() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Resource resource = mock(Resource.class);
    when(downloadPriorAuthorityDocumentUseCase.downloadDocument(priorAuthorityId, documentId))
        .thenReturn(new DocumentDownload(document(documentId, null, null), resource));

    ResponseEntity<Resource> response =
        controller.downloadPriorAuthorityDocument(null, priorAuthorityId, documentId);

    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    assertThat(response.getHeaders().getContentLength()).isNegative();
  }

  @Test
  void givenDocumentWithInvalidMediaType_whenDownloaded_thenUsesDefaultMediaType() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    DocumentDetails document = document(documentId, "invalid media type", 123L);
    Resource resource = mock(Resource.class);
    when(downloadPriorAuthorityDocumentUseCase.downloadDocument(priorAuthorityId, documentId))
        .thenReturn(new DocumentDownload(document, resource));

    ResponseEntity<Resource> response =
        controller.downloadPriorAuthorityDocument(null, priorAuthorityId, documentId);

    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
  }

  private DocumentDetails document(UUID documentId, String mediaType, Long size) {
    return new DocumentDetails(
        documentId,
        null,
        "original evidence.pdf",
        "PDF",
        mediaType,
        size,
        Instant.now(),
        "civil-apply",
        null);
  }
}
