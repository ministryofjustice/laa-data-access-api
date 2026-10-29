package uk.gov.justice.laa.dstew.access.controller.application;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.access.api.ApplicationDocumentCommandApi;
import uk.gov.justice.laa.dstew.access.command.application.document.UploadDocumentUseCase;
import uk.gov.justice.laa.dstew.access.document.DocumentUploadResult;
import uk.gov.justice.laa.dstew.access.model.DocumentType;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.model.UploadApplicationDocumentResponse;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;

/** HTTP command adapter for Application document command writes. */
@RestController
public class ApplicationDocumentCommandController implements ApplicationDocumentCommandApi {
  private final UploadDocumentUseCase uploadDocumentUseCase;

  /** Creates the command adapter. */
  public ApplicationDocumentCommandController(UploadDocumentUseCase uploadDocumentUseCase) {
    this.uploadDocumentUseCase = uploadDocumentUseCase;
  }

  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<UploadApplicationDocumentResponse> uploadApplicationDocument(
      ServiceName serviceName, UUID id, MultipartFile file, DocumentType documentType) {
    DocumentUploadResult result =
        uploadDocumentUseCase.execute(id, file, documentType.getValue(), serviceName.getValue());
    UploadApplicationDocumentResponse response =
        new UploadApplicationDocumentResponse()
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
}
