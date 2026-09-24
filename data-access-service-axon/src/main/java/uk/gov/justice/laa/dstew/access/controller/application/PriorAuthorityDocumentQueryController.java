package uk.gov.justice.laa.dstew.access.controller.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.dstew.access.api.PriorAuthorityDocumentQueryApi;
import uk.gov.justice.laa.dstew.access.model.ServiceName;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.DownloadPriorAuthorityDocumentUseCase;
import uk.gov.justice.laa.dstew.access.query.application.priorauthority.PriorAuthorityDocumentDownload;

/** HTTP query adapter for downloading Prior Authority documents. */
@RestController
public class PriorAuthorityDocumentQueryController implements PriorAuthorityDocumentQueryApi {
  private final DownloadPriorAuthorityDocumentUseCase downloadPriorAuthorityDocumentUseCase;

  public PriorAuthorityDocumentQueryController(
      DownloadPriorAuthorityDocumentUseCase downloadPriorAuthorityDocumentUseCase) {
    this.downloadPriorAuthorityDocumentUseCase = downloadPriorAuthorityDocumentUseCase;
  }

  /** Streams an owned Prior Authority document using its original filename. */
  @Override
  @Operation(security = @SecurityRequirement(name = "BearerAuth"))
  @GetMapping(PriorAuthorityDocumentQueryApi.PATH_DOWNLOAD_PRIOR_AUTHORITY_DOCUMENT)
  public ResponseEntity<Resource> downloadPriorAuthorityDocument(
      @RequestHeader("X-Service-Name") ServiceName serviceName,
      @PathVariable UUID priorAuthorityId,
      @PathVariable UUID documentId) {
    PriorAuthorityDocumentDownload download =
        downloadPriorAuthorityDocumentUseCase.downloadDocument(priorAuthorityId, documentId);

    ResponseEntity.BodyBuilder response =
        ResponseEntity.ok()
            .contentType(mediaType(download.document().mediaType()))
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment()
                    .filename(download.document().fileName(), StandardCharsets.UTF_8)
                    .build()
                    .toString());
    if (download.document().size() != null) {
      response.contentLength(download.document().size());
    }
    return response.body(download.resource());
  }

  private MediaType mediaType(String mediaType) {
    try {
      return mediaType == null
          ? MediaType.APPLICATION_OCTET_STREAM
          : MediaType.parseMediaType(mediaType);
    } catch (IllegalArgumentException exception) {
      return MediaType.APPLICATION_OCTET_STREAM;
    }
  }
}
