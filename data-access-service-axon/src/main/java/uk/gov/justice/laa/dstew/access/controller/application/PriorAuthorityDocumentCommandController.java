package uk.gov.justice.laa.dstew.access.controller.application;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.access.api.PriorAuthorityDocumentCommandApi;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.document.UploadPriorAuthorityDocumentUseCase;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.model.UploadPriorAuthorityDocumentResponse;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodArguments;
import uk.gov.justice.laa.dstew.access.shared.logging.aspects.LogMethodResponse;

/** HTTP command adapter for Prior Authority document uploads. */
@RestController
public class PriorAuthorityDocumentCommandController implements PriorAuthorityDocumentCommandApi {

  private final UploadPriorAuthorityDocumentUseCase uploadUseCase;

  /** Creates the command adapter. */
  public PriorAuthorityDocumentCommandController(
      UploadPriorAuthorityDocumentUseCase uploadUseCase) {
    this.uploadUseCase = uploadUseCase;
  }

  /** Uploads a supporting evidence document to SDS and returns the minted document id. */
  @Override
  @LogMethodArguments
  @LogMethodResponse
  public ResponseEntity<UploadPriorAuthorityDocumentResponse> uploadPriorAuthorityDocument(
      ServiceName serviceName, UUID priorAuthorityId, MultipartFile file) {
    UploadPriorAuthorityDocumentResponse response = uploadUseCase.execute(priorAuthorityId, file);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
