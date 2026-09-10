package uk.gov.justice.laa.dstew.access.controller.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.document.UploadPriorAuthorityDocumentResult;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.document.UploadPriorAuthorityDocumentUseCase;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityDocumentType;
import uk.gov.justice.laa.dstew.access.model.UploadPriorAuthorityDocumentResponse;

@ExtendWith(MockitoExtension.class)
class PriorAuthorityDocumentCommandControllerTest {

  @Mock private UploadPriorAuthorityDocumentUseCase uploadUseCase;

  @InjectMocks private PriorAuthorityDocumentCommandController controller;

  @Test
  void givenRequest_whenUploadPriorAuthorityDocument_thenReturnsCreatedAndBody() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile("file", "evidence.pdf", "application/pdf", "%PDF-content".getBytes());
    PriorAuthorityDocumentType documentType = PriorAuthorityDocumentType.GATEWAY_EVIDENCE;
    String sourceService = "CIVIL_APPLY";
    UploadPriorAuthorityDocumentResult useCaseResponse =
        new UploadPriorAuthorityDocumentResult(
            documentId,
            documentType.getValue(),
            "evidence.pdf",
            "PDF",
            "application/pdf",
            12L,
            Instant.parse("2026-09-10T12:30:00Z"),
            sourceService,
            "checksum");
    when(uploadUseCase.execute(priorAuthorityId, file, documentType.getValue(), sourceService))
        .thenReturn(useCaseResponse);

    ResponseEntity<UploadPriorAuthorityDocumentResponse> response =
        controller.uploadPriorAuthorityDocument(
            uk.gov.justice.laa.dstew.access.model.ServiceName.CIVIL_APPLY,
            priorAuthorityId,
            file,
            documentType);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getDocumentId()).isEqualTo(documentId);
    assertThat(response.getBody().getDocumentType()).isEqualTo(documentType);
    verify(uploadUseCase).execute(priorAuthorityId, file, documentType.getValue(), sourceService);
  }
}
