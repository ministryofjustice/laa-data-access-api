package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.dstew.access.content.priorauthority.EvidenceDocument;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.EvidenceDocumentDownload;
import uk.gov.justice.laa.dstew.access.usecase.application.DownloadApplicationDocumentUseCase;

@ExtendWith(MockitoExtension.class)
class ApplicationDocumentQueryControllerTest {

  private static final UUID APPLICATION_ID = UUID.randomUUID();
  private static final UUID DOCUMENT_ID = UUID.randomUUID();

  @Mock private DownloadApplicationDocumentUseCase downloadApplicationDocumentUseCase;

  @InjectMocks private ApplicationDocumentQueryController controller;

  @Test
  void givenOwnedDocument_whenDownloaded_thenReturnsContentWithFilenameAndMetadataHeaders() {
    Resource resource = givenDownload("GATEWAY_EVIDENCE", "application/pdf", 123L);

    ResponseEntity<Resource> response =
        controller.downloadApplicationDocument(null, APPLICATION_ID, DOCUMENT_ID);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isSameAs(resource);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
    assertThat(response.getHeaders().getContentLength()).isEqualTo(123L);
    assertThat(response.getHeaders().getFirst("Content-Disposition"))
        .contains("attachment")
        .contains("original evidence.pdf");
    assertThat(response.getHeaders().getFirst("X-Document-Uploaded-At"))
        .isEqualTo("2026-09-28T15:10:27.430Z");
    assertThat(response.getHeaders().getFirst("X-Document-Type")).isEqualTo("GATEWAY_EVIDENCE");
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "invalid media type")
  void givenMissingOrInvalidMediaType_whenDownloaded_thenUsesOctetStreamAndOmitsOptionalHeaders(
      String mediaType) {
    givenDownload(null, mediaType, null);

    ResponseEntity<Resource> response =
        controller.downloadApplicationDocument(null, APPLICATION_ID, DOCUMENT_ID);

    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
    assertThat(response.getHeaders().getContentLength()).isNegative();
    assertThat(response.getHeaders().getFirst("X-Document-Type")).isNull();
  }

  private Resource givenDownload(String documentType, String mediaType, Long size) {
    Resource resource = mock(Resource.class);
    EvidenceDocument document =
        new EvidenceDocument(
            DOCUMENT_ID,
            documentType,
            "original evidence.pdf",
            null,
            mediaType,
            size,
            Instant.parse("2026-09-28T15:10:27.430Z"),
            "CIVIL_APPLY",
            "checksum");
    when(downloadApplicationDocumentUseCase.downloadDocument(APPLICATION_ID, DOCUMENT_ID))
        .thenReturn(new EvidenceDocumentDownload(document, resource));
    return resource;
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  void givenMissingFilename_whenDownloaded_thenUsesDocumentIdAsAttachmentName(String filename) {
    Resource resource = mock(Resource.class);
    EvidenceDocument document =
        new EvidenceDocument(
            DOCUMENT_ID,
            null,
            filename,
            null,
            "application/pdf",
            12L,
            Instant.now(),
            "CIVIL_APPLY",
            null);
    when(downloadApplicationDocumentUseCase.downloadDocument(APPLICATION_ID, DOCUMENT_ID))
        .thenReturn(new EvidenceDocumentDownload(document, resource));

    ResponseEntity<Resource> response =
        controller.downloadApplicationDocument(null, APPLICATION_ID, DOCUMENT_ID);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getBody()).isSameAs(resource);
    assertThat(response.getHeaders().getContentDisposition().getFilename())
        .isEqualTo(DOCUMENT_ID.toString());
  }
}
