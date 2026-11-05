package uk.gov.justice.laa.dstew.access.command.application.document;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.ApplicationDocumentUploadCommand;
import uk.gov.justice.laa.dstew.access.command.application.draft.ApplicationDraftExistsQuery;
import uk.gov.justice.laa.dstew.access.document.DocumentUploadResult;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;
import uk.gov.justice.laa.dstew.access.service.sds.SdsUploadResult;

/** Uploads a document to SDS on behalf of an Application. */
@Component
public class UploadDocumentUseCase {

  private final SdsService sdsService;
  private final RetryingCommandDispatcher dispatcher;
  private final QueryGateway queryGateway;

  /** Constructs the upload use case with an aggregate-backed draft check and command dispatch. */
  public UploadDocumentUseCase(
      SdsService sdsService, RetryingCommandDispatcher dispatcher, QueryGateway queryGateway) {
    this.sdsService = sdsService;
    this.dispatcher = dispatcher;
    this.queryGateway = queryGateway;
  }

  /**
   * Uploads the given file to SDS under the application's folder.
   *
   * @param applicationId the application the document belongs to
   * @param file the file to upload
   * @return the upload response from SDS containing the file key and checksum
   */
  @AllowApiCaseworker
  public SdsUploadResult execute(UUID applicationId, MultipartFile file) {
    requireDraft(applicationId);
    return sdsService.saveFile(applicationId, file);
  }

  /** Uploads a file to SDS and records its non-filename metadata against the Application. */
  @AllowApiCaseworker
  public DocumentUploadResult execute(
      UUID applicationId, MultipartFile file, String documentType, String sourceService) {
    requireDraft(applicationId);
    UUID documentId = UUID.randomUUID();
    Instant uploadedAt = Instant.now();
    SdsUploadResult response = sdsService.saveEvidenceFile(applicationId, documentId, file);
    dispatcher.dispatch(
        new ApplicationDocumentUploadCommand(
            applicationId,
            documentId,
            documentType,
            uploadedAt,
            file.getSize(),
            file.getContentType(),
            response.checksum(),
            sourceService,
            file.getOriginalFilename()));
    return new DocumentUploadResult(
        documentId,
        file.getOriginalFilename(),
        fileType(file.getContentType()),
        file.getContentType(),
        file.getSize(),
        uploadedAt,
        sourceService,
        response.checksum());
  }

  private void requireDraft(UUID applicationId) {
    if (!queryGateway.query(new ApplicationDraftExistsQuery(applicationId), Boolean.class).join()) {
      throw new ResourceNotFoundException(
          "No application draft found with Application ID: " + applicationId);
    }
  }

  private String fileType(String contentType) {
    if (contentType == null || contentType.isBlank()) {
      return null;
    }
    int separator = contentType.indexOf('/');
    return separator < 0
        ? contentType.toUpperCase()
        : contentType.substring(separator + 1).toUpperCase();
  }
}
