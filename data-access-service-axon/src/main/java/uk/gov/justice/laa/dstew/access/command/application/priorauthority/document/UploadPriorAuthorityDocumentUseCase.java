package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import java.time.Instant;
import java.util.UUID;
import org.axonframework.messaging.queryhandling.gateway.QueryGateway;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.FindPriorAuthorityDraftQuery;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDraftSummary;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.ValidateApplicationGrantedCommand;
import uk.gov.justice.laa.dstew.access.document.DocumentUploadResult;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.security.AuthenticatedCaller;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;
import uk.gov.justice.laa.dstew.access.service.sds.SdsUploadResult;

/** Uploads a prior-authority document to SDS and records its metadata against the draft. */
@Component
public class UploadPriorAuthorityDocumentUseCase {
  private final QueryGateway queryGateway;
  private final RetryingCommandDispatcher dispatcher;
  private final SdsService sdsService;

  /** Creates the use case with draft lookup, command dispatch, and SDS upload dependencies. */
  public UploadPriorAuthorityDocumentUseCase(
      QueryGateway queryGateway, RetryingCommandDispatcher dispatcher, SdsService sdsService) {
    this.queryGateway = queryGateway;
    this.dispatcher = dispatcher;
    this.sdsService = sdsService;
  }

  /** Uploads a file and finalises it via a single aggregate command. */
  @AllowApiCaseworker
  public DocumentUploadResult execute(
      UUID priorAuthorityId, MultipartFile file, String sourceService, AuthenticatedCaller caller) {
    PriorAuthorityDocumentFormat format = PriorAuthorityDocumentFormat.validate(file);
    PriorAuthorityDraftSummary draft =
        queryGateway
            .query(
                new FindPriorAuthorityDraftQuery(priorAuthorityId),
                PriorAuthorityDraftSummary.class)
            .join();
    if (draft == null || !draft.exists()) {
      throw new ResourceNotFoundException(
          "Prior Authority %s not found".formatted(priorAuthorityId));
    }
    dispatcher.dispatch(new ValidateApplicationGrantedCommand(draft.applicationId()));

    UUID documentId = UUID.randomUUID();
    Instant uploadedAt = Instant.now();
    SdsUploadResult sdsResponse = sdsService.saveEvidenceFile(priorAuthorityId, documentId, file);
    String checksum = sdsResponse == null ? null : sdsResponse.checksum();

    dispatcher.dispatch(
        new PriorAuthorityDocumentUploadCommand(
            priorAuthorityId,
            documentId,
            sourceService,
            checksum,
            uploadedAt,
            file.getOriginalFilename(),
            file.getSize(),
            format.contentType(),
            caller));

    return new DocumentUploadResult(
        documentId,
        file.getOriginalFilename(),
        format.fileType(),
        format.contentType(),
        file.getSize(),
        uploadedAt,
        sourceService,
        checksum);
  }

  /** Temporary source-compatible overload for non-HTTP fixtures. */
  @Deprecated(forRemoval = true)
  public DocumentUploadResult execute(
      UUID priorAuthorityId, MultipartFile file, String sourceService) {
    return execute(priorAuthorityId, file, sourceService, null);
  }
}
