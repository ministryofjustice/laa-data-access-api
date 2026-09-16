package uk.gov.justice.laa.dstew.access.controller.application;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.access.api.PriorAuthorityDocumentCommandApi;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.document.UpdatePriorAuthorityDocumentTypeResult;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.document.UpdatePriorAuthorityDocumentTypeUseCase;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.document.UploadPriorAuthorityDocumentResult;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.document.UploadPriorAuthorityDocumentUseCase;
import uk.gov.justice.laa.dstew.access.model.PriorAuthorityDocumentTypeUpdateResponse;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.model.UpdatePriorAuthorityDocumentTypeRequest;
import uk.gov.justice.laa.dstew.access.model.UploadPriorAuthorityDocumentResponse;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;

/** HTTP command adapter for prior-authority document uploads. */
@RestController
public class PriorAuthorityDocumentCommandController implements PriorAuthorityDocumentCommandApi {

  private final UploadPriorAuthorityDocumentUseCase uploadUseCase;
  private final UpdatePriorAuthorityDocumentTypeUseCase updateTypeUseCase;

  public PriorAuthorityDocumentCommandController(
      UploadPriorAuthorityDocumentUseCase uploadUseCase,
      UpdatePriorAuthorityDocumentTypeUseCase updateTypeUseCase) {
    this.uploadUseCase = uploadUseCase;
    this.updateTypeUseCase = updateTypeUseCase;
  }

  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<UploadPriorAuthorityDocumentResponse> uploadPriorAuthorityDocument(
      ServiceName serviceName, UUID priorAuthorityId, MultipartFile file) {
    UploadPriorAuthorityDocumentResult result =
        uploadUseCase.execute(priorAuthorityId, file, serviceName.getValue());
    UploadPriorAuthorityDocumentResponse response =
        new UploadPriorAuthorityDocumentResponse()
            .documentId(result.documentId())
            .fileName(result.fileName())
            .fileType(result.fileType())
            .contentType(result.contentType())
            .size(result.size())
            .uploadedAt(result.uploadedAt().atOffset(java.time.ZoneOffset.UTC))
            .sourceService(result.sourceService())
            .checksum(result.checksum());
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<PriorAuthorityDocumentTypeUpdateResponse> updatePriorAuthorityDocumentType(
      ServiceName serviceName,
      UUID priorAuthorityId,
      UUID documentId,
      UpdatePriorAuthorityDocumentTypeRequest request) {
    UpdatePriorAuthorityDocumentTypeResult result =
        updateTypeUseCase.execute(priorAuthorityId, documentId, request);
    return ResponseEntity.ok()
        .body(
            new PriorAuthorityDocumentTypeUpdateResponse(
                result.documentId(), result.updatedAt().atOffset(java.time.ZoneOffset.UTC)));
  }
}
