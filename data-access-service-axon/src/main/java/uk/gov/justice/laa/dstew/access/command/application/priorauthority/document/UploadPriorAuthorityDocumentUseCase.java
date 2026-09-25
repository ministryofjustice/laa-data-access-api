package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.ValidateApplicationGrantedCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.DocumentUploadResponse;
import uk.gov.justice.laa.dstew.access.query.SubscriptionProjectionGateway;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityDocumentPresentQuery;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;
import uk.gov.justice.laa.dstew.access.util.RequestSerialiser;

/** Dispatches a single command that uploads and finalises prior-authority documents. */
@Component
public class UploadPriorAuthorityDocumentUseCase {
  private final PriorAuthorityDraftStore draftStore;
  private final RetryingCommandDispatcher dispatcher;
  private final SdsService sdsService;
  private final ObjectMapper objectMapper;
  private final SubscriptionProjectionGateway projectionGateway;

  /** Creates the use case with draft lookup, command dispatch, and SDS upload dependencies. */
  public UploadPriorAuthorityDocumentUseCase(
      PriorAuthorityDraftStore draftStore,
      RetryingCommandDispatcher dispatcher,
      SdsService sdsService,
      ObjectMapper objectMapper,
      SubscriptionProjectionGateway projectionGateway) {
    this.draftStore = draftStore;
    this.dispatcher = dispatcher;
    this.sdsService = sdsService;
    this.objectMapper = objectMapper;
    this.projectionGateway = projectionGateway;
  }

  /** Uploads a file and finalises it via a single aggregate command. */
  @AllowApiCaseworker
  public UploadPriorAuthorityDocumentResult execute(
      UUID priorAuthorityId, MultipartFile file, String sourceService) {
    PriorAuthorityDocumentFormat format = PriorAuthorityDocumentFormat.validate(file);
    String originalFilename =
        Objects.requireNonNull(file.getOriginalFilename(), "originalFilename must not be null");
    requireFilenameExtension(originalFilename);
    var draft =
        draftStore
            .find(priorAuthorityId)
            .orElseThrow(
                () ->
                    new ResourceNotFoundException(
                        "Prior Authority %s not found".formatted(priorAuthorityId)));
    dispatcher.dispatch(new ValidateApplicationGrantedCommand(draft.applicationId()));

    UUID documentId = UUID.randomUUID();
    Instant uploadedAt = Instant.now();
    DocumentUploadResponse sdsResponse =
        sdsService.savePriorAuthorityFile(priorAuthorityId, documentId, file);
    String checksum = sdsResponse == null ? null : sdsResponse.getChecksum();
    String serialisedRequest =
        RequestSerialiser.serialise(
            objectMapper,
            new UploadPriorAuthorityDocumentRequest(
                documentId,
                originalFilename,
                file.getSize(),
                format.fileType(),
                format.contentType(),
                sourceService,
                checksum));

    dispatcher.dispatch(
        new PriorAuthorityDocumentUploadCommand(
            priorAuthorityId,
            documentId,
            sourceService,
            checksum,
            serialisedRequest,
            uploadedAt,
            originalFilename,
            file.getSize(),
            format.fileType(),
            format.contentType()));

    projectionGateway.awaitProjection(
        new PriorAuthorityDocumentPresentQuery(priorAuthorityId, documentId), () -> {});

    return new UploadPriorAuthorityDocumentResult(
        documentId,
        originalFilename,
        format.fileType(),
        format.contentType(),
        file.getSize(),
        uploadedAt,
        sourceService,
        checksum);
  }

  private static void requireFilenameExtension(String filename) {
    int extensionIndex = filename.lastIndexOf('.');
    if (extensionIndex <= 0 || extensionIndex == filename.length() - 1) {
      throw new IllegalArgumentException("originalFilename must include a file extension");
    }
  }
}
