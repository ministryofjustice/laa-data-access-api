package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import uk.gov.justice.laa.dstew.access.command.application.document.UploadDocumentUseCase;
import uk.gov.justice.laa.dstew.access.document.DocumentUploadResult;
import uk.gov.justice.laa.dstew.access.model.DocumentType;
import uk.gov.justice.laa.dstew.access.model.UploadApplicationDocumentResponse;

/** Verifies that each controller endpoint delegates to the appropriate use case. */
@ExtendWith(MockitoExtension.class)
public class ApplicationDocumentCommandControllerTest {
  private ApplicationDocumentCommandController controller;
  private UploadDocumentUseCase uploadDocumentUseCase;

  @BeforeEach
  void setUp() {
    uploadDocumentUseCase = mock(UploadDocumentUseCase.class);
    controller = new ApplicationDocumentCommandController(uploadDocumentUseCase);
  }

  @Test
  void givenDocumentType_whenUploadApplicationDocument_thenDelegatesToUseCaseAndReturns201() {
    UUID id = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Instant uploadedAt = Instant.parse("2026-09-28T15:10:27.430Z");
    MockMultipartFile file =
        new MockMultipartFile("file", "test.pdf", "application/pdf", "content".getBytes());
    DocumentUploadResult expected =
        new DocumentUploadResult(
            documentId,
            "test.pdf",
            "PDF",
            "application/pdf",
            7L,
            uploadedAt,
            "CIVIL_APPLY",
            "checksum");
    when(uploadDocumentUseCase.execute(id, file, "GATEWAY_EVIDENCE", "CIVIL_APPLY"))
        .thenReturn(expected);

    ResponseEntity<UploadApplicationDocumentResponse> response =
        controller.uploadApplicationDocument(
            uk.gov.justice.laa.dstew.access.model.ServiceName.CIVIL_APPLY,
            id,
            file,
            DocumentType.GATEWAY_EVIDENCE);

    verify(uploadDocumentUseCase).execute(id, file, "GATEWAY_EVIDENCE", "CIVIL_APPLY");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getDocumentId()).isEqualTo(documentId);
    assertThat(response.getBody().getFileName()).isEqualTo("test.pdf");
    assertThat(response.getBody().getFileType()).isEqualTo("PDF");
    assertThat(response.getBody().getContentType()).isEqualTo("application/pdf");
    assertThat(response.getBody().getSize()).isEqualTo(7L);
    assertThat(response.getBody().getUploadedAt())
        .isEqualTo(uploadedAt.atOffset(java.time.ZoneOffset.UTC));
    assertThat(response.getBody().getSourceService()).isEqualTo("CIVIL_APPLY");
    assertThat(response.getBody().getChecksum()).isEqualTo("checksum");
  }
}
