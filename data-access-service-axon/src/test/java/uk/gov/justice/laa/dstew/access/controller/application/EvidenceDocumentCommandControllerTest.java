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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.document.DeletePriorAuthorityDocumentUseCase;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.document.UpdatePriorAuthorityDocumentTypeResult;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.document.UpdatePriorAuthorityDocumentTypeUseCase;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.document.UploadPriorAuthorityDocumentUseCase;
import uk.gov.justice.laa.dstew.access.document.DocumentUploadResult;
import uk.gov.justice.laa.dstew.access.model.DocumentType;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityDocumentTypeUpdateResponse;
import uk.gov.justice.laa.dstew.access.model.UpdatePriorAuthorityDocumentTypeRequest;
import uk.gov.justice.laa.dstew.access.model.UploadPriorAuthorityDocumentResponse;

@ExtendWith(MockitoExtension.class)
class EvidenceDocumentCommandControllerTest {

  @Mock private UploadPriorAuthorityDocumentUseCase uploadUseCase;
  @Mock private UpdatePriorAuthorityDocumentTypeUseCase updateTypeUseCase;
  @Mock private DeletePriorAuthorityDocumentUseCase deleteUseCase;
  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks private PriorAuthorityDocumentCommandController controller;

  @Test
  void givenRequest_whenUploadPriorAuthorityDocument_thenReturnsCreatedAndBody() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    MockMultipartFile file =
        new MockMultipartFile("file", "evidence.pdf", "application/pdf", "%PDF-content".getBytes());
    String sourceService = "CIVIL_APPLY";
    DocumentUploadResult useCaseResponse =
        new DocumentUploadResult(
            documentId,
            "evidence.pdf",
            "PDF",
            "application/pdf",
            12L,
            Instant.parse("2026-09-10T12:30:00Z"),
            sourceService,
            "checksum");
    when(uploadUseCase.execute(priorAuthorityId, file, sourceService)).thenReturn(useCaseResponse);

    ResponseEntity<UploadPriorAuthorityDocumentResponse> response =
        controller.uploadPriorAuthorityDocument(
            uk.gov.justice.laa.dstew.access.model.ServiceName.CIVIL_APPLY, priorAuthorityId, file);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getDocumentId()).isEqualTo(documentId);
    verify(uploadUseCase).execute(priorAuthorityId, file, sourceService);
  }

  @Test
  void givenRequest_whenUpdatePriorAuthorityDocumentType_thenReturnsOkAndAcknowledgement() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();
    Instant updatedAt = Instant.parse("2026-09-10T12:30:00Z");
    UpdatePriorAuthorityDocumentTypeRequest request =
        new UpdatePriorAuthorityDocumentTypeRequest(DocumentType.GATEWAY_EVIDENCE);
    String serialisedRequest = "{\"documentType\":\"GATEWAY_EVIDENCE\"}";
    when(updateTypeUseCase.execute(
            priorAuthorityId, documentId, "GATEWAY_EVIDENCE", serialisedRequest))
        .thenReturn(new UpdatePriorAuthorityDocumentTypeResult(documentId, updatedAt));

    ResponseEntity<PriorAuthorityDocumentTypeUpdateResponse> response =
        controller.updatePriorAuthorityDocumentType(
            uk.gov.justice.laa.dstew.access.model.ServiceName.CIVIL_APPLY,
            priorAuthorityId,
            documentId,
            request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getDocumentId()).isEqualTo(documentId);
    assertThat(response.getBody().getUpdatedAt())
        .isEqualTo(updatedAt.atOffset(java.time.ZoneOffset.UTC));
    verify(updateTypeUseCase)
        .execute(priorAuthorityId, documentId, "GATEWAY_EVIDENCE", serialisedRequest);
  }

  @Test
  void givenRequest_whenDeletePriorAuthorityDocument_thenReturnsNoContent() {
    UUID priorAuthorityId = UUID.randomUUID();
    UUID documentId = UUID.randomUUID();

    ResponseEntity<Void> response =
        controller.deletePriorAuthorityDocument(
            uk.gov.justice.laa.dstew.access.model.ServiceName.CIVIL_APPLY,
            priorAuthorityId,
            documentId);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    verify(deleteUseCase).execute(priorAuthorityId, documentId);
  }
}
