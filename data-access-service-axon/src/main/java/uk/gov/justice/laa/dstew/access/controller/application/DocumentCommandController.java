package uk.gov.justice.laa.dstew.access.controller.application;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.access.command.application.document.UploadDocumentUseCase;
import uk.gov.justice.laa.dstew.access.model.DocumentUploadResponse;
import uk.gov.justice.laa.dstew.access.model.ServiceName;

/** HTTP command adapter for document operations on an Application. */
@RestController
@RequestMapping("/api/v0/applications")
public class DocumentCommandController {

  private final UploadDocumentUseCase uploadDocumentUseCase;

  /** Creates the document command adapter. */
  public DocumentCommandController(UploadDocumentUseCase uploadDocumentUseCase) {
    this.uploadDocumentUseCase = uploadDocumentUseCase;
  }

  /** Uploads a document to SDS and returns the file key and checksum. */
  @PostMapping(value = "/{id}/upload-document", consumes = "multipart/form-data")
  public ResponseEntity<DocumentUploadResponse> uploadDocument(
      @RequestHeader("X-Service-Name") ServiceName serviceName,
      @PathVariable("id") UUID applicationId,
      @RequestParam("file") MultipartFile file) {
    DocumentUploadResponse response = uploadDocumentUseCase.execute(applicationId, file);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
