package uk.gov.justice.laa.dstew.access.command.application.priorauthority.document;

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;
import uk.gov.justice.laa.dstew.access.command.RetryingCommandDispatcher;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.PriorAuthorityDocumentUploadCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.ValidateApplicationGrantedCommand;
import uk.gov.justice.laa.dstew.access.command.application.priorauthority.data.PriorAuthorityDraftStore;
import uk.gov.justice.laa.dstew.access.content.priorauthority.PriorAuthorityDocumentMetadata;
import uk.gov.justice.laa.dstew.access.exception.ResourceNotFoundException;
import uk.gov.justice.laa.dstew.access.model.DocumentUploadResponse;
import uk.gov.justice.laa.dstew.access.security.AllowApiCaseworker;
import uk.gov.justice.laa.dstew.access.service.sds.SdsService;
import uk.gov.justice.laa.dstew.access.util.RequestSerialiser;

/** Dispatches a single command that uploads and finalises prior-authority documents. */
@Component
public class UploadPriorAuthorityDocumentUseCase {

  private static final byte[] PDF_HEADER =
      "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

  private final PriorAuthorityDraftStore draftStore;
  private final RetryingCommandDispatcher dispatcher;
  private final SdsService sdsService;
  private final ObjectMapper objectMapper;

  /** Creates the use case with draft lookup, command dispatch, and SDS upload dependencies. */
  public UploadPriorAuthorityDocumentUseCase(
      PriorAuthorityDraftStore draftStore,
      RetryingCommandDispatcher dispatcher,
      SdsService sdsService,
      ObjectMapper objectMapper) {
    this.draftStore = draftStore;
    this.dispatcher = dispatcher;
    this.sdsService = sdsService;
    this.objectMapper = objectMapper;
  }

  /** Uploads a file and finalises it via a single aggregate command. */
  @AllowApiCaseworker
  public UploadPriorAuthorityDocumentResult execute(
      UUID priorAuthorityId, MultipartFile file, String sourceService) {
    validateUpload(file);
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
                documentId, file.getOriginalFilename(), file.getSize(), sourceService, checksum));

    dispatcher.dispatch(
        new PriorAuthorityDocumentUploadCommand(
            priorAuthorityId,
            documentId,
            sourceService,
            checksum,
            serialisedRequest,
            uploadedAt,
            file.getOriginalFilename(),
            file.getSize()));

    return new UploadPriorAuthorityDocumentResult(
        documentId,
        file.getOriginalFilename(),
        PriorAuthorityDocumentMetadata.PDF_FILE_TYPE,
        PriorAuthorityDocumentMetadata.PDF_CONTENT_TYPE,
        file.getSize(),
        uploadedAt,
        sourceService,
        checksum);
  }

  private static void validateUpload(MultipartFile file) {
    if (!PriorAuthorityDocumentMetadata.PDF_CONTENT_TYPE.equalsIgnoreCase(file.getContentType())) {
      throw new IllegalArgumentException("Only PDF documents are supported");
    }
    try (var inputStream = file.getInputStream()) {
      byte[] header = inputStream.readNBytes(PDF_HEADER.length);
      if (!Arrays.equals(header, PDF_HEADER)) {
        throw new IllegalArgumentException("Uploaded file is not a valid PDF document");
      }
    } catch (IOException exception) {
      throw new IllegalArgumentException("Unable to validate uploaded document", exception);
    }
  }
}
